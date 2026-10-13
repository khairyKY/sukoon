package com.sukoon.app.domain.metrics

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryGapTest {
    private val now = Instant.parse("2026-10-13T03:00:00Z")
    private fun at(minutesAgo: Long) = GlucoseReading(now.minusSeconds(minutesAgo * 60), 120, TrendDirection.STEADY, SourceKind.LIBRE_BLE)

    @Test
    fun `the newest gap of half an hour or more within the sensor's 8 hours`() {
        val readings = listOf(at(400), at(380), at(300), at(120), at(118), at(2)) // 80 min gap, 180 min gap, then 116 min gap
        assertEquals(at(118).timestamp to at(2).timestamp, MemoryGap.fillable(readings, now))
    }

    @Test
    fun `short gaps, gaps older than 8 hours and no readings at all are not offered`() {
        assertNull(MemoryGap.fillable(listOf(at(30), at(10), at(0)), now)) // 20 min steps
        assertNull(MemoryGap.fillable(listOf(at(600), at(540), at(479)), now)) // only one reading inside the window
        assertNull(MemoryGap.fillable(emptyList(), now))
    }
}
