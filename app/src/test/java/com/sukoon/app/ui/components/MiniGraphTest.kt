package com.sukoon.app.ui.components

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniGraphTest {

    private val now = Instant.parse("2026-10-07T03:00:00Z")
    private fun r(minutesAgo: Long, mgDl: Int) = GlucoseReading(now.minus(Duration.ofMinutes(minutesAgo)), mgDl, TrendDirection.STEADY, SourceKind.LIBRE_BLE)

    @Test
    fun `a short low inside 15 minutes shows as the bar, not averaged away`() {
        // Last bucket: 120s with a 62 in the middle; the average (~116) would have stayed green.
        val readings = (0L..14).map { r(it, if (it == 7L) 62 else 120) }
        val bars = homeBars(readings, now)
        assertEquals(12, bars.size)
        assertEquals(62, bars.last())
    }

    @Test
    fun `highs show their peak, in-range their average, and no readings a gap`() {
        // Bucket 9 is 31–45 minutes ago, bucket 8 is 46–60.
        val readings = (31L..45).map { r(it, if (it == 40L) 240 else 150) } + (46L..60).map { r(it, 100 + (it - 46).toInt()) }
        val bars = homeBars(readings, now)
        assertEquals(240, bars[9])
        assertEquals(107, bars[8]) // 100..114
        assertNull(bars[11]) // nothing in the last 15 minutes
    }
}
