package com.sukoon.app.ui.home

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeStatsTest {

    private val now = Instant.parse("2026-10-06T14:00:00Z")
    private fun at(hoursAgo: Long) = now.minusSeconds(hoursAgo * 3600).toEpochMilli()

    @Test
    fun `today's food, insulin and glucose, nothing from yesterday`() {
        val events = listOf(
            EventEntity(timestampMillis = at(20), type = LogEventType.CARB.name, value = 90.0, kcal = 900.0), // yesterday
            EventEntity(timestampMillis = at(6), type = LogEventType.CARB.name, value = 45.0, kcal = 380.0, protein = 12.0),
            EventEntity(timestampMillis = at(2), type = LogEventType.CARB.name, value = 62.0), // no calories logged
            EventEntity(timestampMillis = at(2), type = LogEventType.INSULIN.name, value = 4.0),
            EventEntity(timestampMillis = at(8), type = LogEventType.BASAL.name, value = 18.0),
        )
        val readings = (0..120).map { GlucoseReading(now.minusSeconds((120 - it) * 60L), if (it < 30) 60 else 120, TrendDirection.STEADY, SourceKind.LIBRE_BLE) }
        val stats = todayStats(events, readings, now, ZoneOffset.UTC)
        assertEquals(380.0, stats[HomeStat.KCAL])
        assertEquals(107.0, stats[HomeStat.CARBS])
        assertEquals(12.0, stats[HomeStat.PROTEIN])
        assertFalse(HomeStat.FAT in stats) // nothing to go on: a dash, not a zero
        assertEquals(4.0, stats[HomeStat.RAPID])
        assertEquals(18.0, stats[HomeStat.LONG])
        assertEquals(75.0, stats[HomeStat.TIR])
        assertEquals(1.0, stats[HomeStat.LOWS])
    }
}
