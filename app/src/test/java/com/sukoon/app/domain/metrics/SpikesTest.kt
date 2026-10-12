package com.sukoon.app.domain.metrics

import com.sukoon.app.ai.AiPrompts
import com.sukoon.app.data.db.ReadingEntity
import com.sukoon.app.data.repository.toGlucoseReading
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpikesTest {

    private val t0 = Instant.parse("2026-10-12T12:00:00Z")
    private fun r(minute: Long, mgDl: Int, source: SourceKind = SourceKind.LIBRE_BLE) =
        GlucoseReading(t0.plusSeconds(minute * 60), mgDl, TrendDirection.STEADY, source)

    @Test
    fun `a reading no body produces goes, real movement stays`() {
        val day = listOf(r(0, 120), r(1, 122), r(2, 64), r(3, 125), r(4, 128), r(5, 131))
        assertEquals(listOf(64), Spikes.of(day).map { it.glucoseMgDl })
        // A fast but possible fall (4 a minute) is kept, and the ends always are.
        val fall = listOf(r(0, 200), r(1, 196), r(2, 192), r(3, 188))
        assertTrue(Spikes.of(fall).isEmpty())
        assertTrue(Spikes.of(listOf(r(0, 40), r(1, 300))).isEmpty())
    }

    @Test
    fun `the AI sees real readings, calls demo demo, and never sees the noise`() {
        val zone = ZoneId.of("UTC")
        val now = t0.plusSeconds(10 * 60)
        val mixed = listOf(r(0, 300, SourceKind.SIMULATED), r(1, 120), r(2, 501), r(3, 124), r(4, 126))
        val brief = AiPrompts.dataBrief(mixed, emptyList(), now, zone)
        assertTrue(brief.contains("DATA SOURCE: REAL"))
        assertFalse(brief.contains("501"))
        assertFalse(brief.contains(" 300 "))
        val demoOnly = AiPrompts.dataBrief(listOf(r(0, 110, SourceKind.SIMULATED), r(1, 112, SourceKind.SIMULATED)), emptyList(), now, zone)
        assertTrue(demoOnly.contains("DATA SOURCE: DEMO"))
    }

    @Test
    fun `readings saved under 0_2's sources read back as real, not demo`() {
        listOf("NIGHTSCOUT", "BROADCAST").forEach {
            assertEquals(SourceKind.LIBRE_BLE, ReadingEntity(timestampMillis = 0, glucoseMgDl = 100, trend = "STEADY", source = it).toGlucoseReading().source)
        }
    }
}
