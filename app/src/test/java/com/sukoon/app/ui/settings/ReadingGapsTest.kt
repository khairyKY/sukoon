package com.sukoon.app.ui.settings

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingGapsTest {

    private val t0 = Instant.parse("2026-10-07T00:00:00Z")
    private fun at(minute: Long) = GlucoseReading(t0.plus(Duration.ofMinutes(minute)), 120, TrendDirection.STEADY, SourceKind.LIBRE_BLE)

    @Test
    fun `gaps are counted between readings and up to now, the longest kept`() {
        val readings = (0L..60).map(::at) + (75L..200).map(::at) + (240L..300).map(::at) // 15 and 40 min holes
        val g = ReadingGaps.of(readings, t0.plus(Duration.ofMinutes(301)), saveEveryMinutes = 1)
        assertEquals(2, g.gaps)
        assertEquals(40, g.longestMinutes)
        assertEquals(t0.plus(Duration.ofMinutes(200)), g.longestAt)
    }

    @Test
    fun `a sparse save interval is not a gap, a quiet sensor right now is`() {
        val every15 = (0L..300 step 15).map(::at)
        assertEquals(0, ReadingGaps.of(every15, t0.plus(Duration.ofMinutes(305)), saveEveryMinutes = 15).gaps)
        assertEquals(1, ReadingGaps.of(every15, t0.plus(Duration.ofMinutes(360)), saveEveryMinutes = 15).gaps)
    }
}
