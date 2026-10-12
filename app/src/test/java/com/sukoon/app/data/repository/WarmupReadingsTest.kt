package com.sukoon.app.data.repository

import com.sukoon.app.data.db.ReadingDao
import com.sukoon.app.data.db.ReadingEntity
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SimulatedSource
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A sensor's first hour is shown on Home and the graph, and left out of everything that analyses or acts on readings. */
class WarmupReadingsTest {

    private class FakeDao(vararg rows: ReadingEntity) : ReadingDao {
        val all = MutableStateFlow(rows.toList().sortedBy { it.timestampMillis })
        override suspend fun insert(reading: ReadingEntity) { all.value = (all.value + reading).sortedBy { it.timestampMillis } }
        override suspend fun deleteBySource(source: String) {}
        override suspend fun countBetween(from: Long, to: Long) = all.value.count { it.timestampMillis in (from + 1) until to }
        override fun latest(): Flow<ReadingEntity?> = all.map { it.lastOrNull() }
        override fun since(sinceMillis: Long): Flow<List<ReadingEntity>> = all.map { rows -> rows.filter { it.timestampMillis >= sinceMillis } }
        override fun between(fromMillis: Long, toMillis: Long): Flow<List<ReadingEntity>> =
            all.map { rows -> rows.filter { it.timestampMillis in fromMillis until toMillis } }
        override fun latestN(limit: Int): Flow<List<ReadingEntity>> = all.map { it.takeLast(limit).reversed() }
    }

    private fun row(minute: Long, mgDl: Int, warmup: Boolean) =
        ReadingEntity(timestampMillis = minute * 60_000, glucoseMgDl = mgDl, trend = "STEADY", source = "LIBRE_BLE", warmup = warmup)

    private val repository = GlucoseRepository(
        sources = MutableStateFlow(SimulatedSource(scope = CoroutineScope(Dispatchers.Unconfined))),
        readingDao = FakeDao(row(1, 300, warmup = true), row(2, 310, warmup = true), row(61, 120, warmup = false), row(62, 124, warmup = false)),
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun `analysis paths never see warm-up readings`() = runBlocking {
        assertEquals(listOf(120, 124), repository.readingsSince(0).first().map { it.glucoseMgDl })
        assertEquals(listOf(120, 124), repository.readingsBetween(0, 10_000_000).first().map { it.glucoseMgDl })
        assertEquals(listOf(120, 124), repository.rawReadingsSince(0).first().map { it.glucoseMgDl })
    }

    @Test
    fun `display paths can show them`() = runBlocking {
        assertEquals(listOf(300, 310, 120, 124), repository.readingsSince(0, includeWarmup = true).first().map { it.glucoseMgDl })
        assertEquals(listOf(300, 310, 120, 124), repository.readingsBetween(0, 10_000_000, includeWarmup = true).first().map { it.glucoseMgDl })
    }

    @Test
    fun `the flag survives the database and defaults to off`() {
        val reading = GlucoseReading(Instant.ofEpochMilli(60_000), 150, TrendDirection.STEADY, SourceKind.LIBRE_BLE, warmup = true)
        assertTrue(reading.toEntity().warmup)
        assertTrue(reading.toEntity().toGlucoseReading().warmup)
        assertEquals(false, GlucoseReading(Instant.EPOCH, 100, TrendDirection.STEADY, SourceKind.LIBRE_BLE).warmup)
    }
}
