package com.sukoon.app.reminders

import com.sukoon.app.R
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MorningSummaryTest {

    private val from = Instant.parse("2026-10-20T00:00:00Z")
    private val to = from.plus(Duration.ofDays(1))

    /** One reading every 5 minutes through the day, [mgDl] at minute m. */
    private fun day(mgDl: (Int) -> Int) = (0 until 24 * 60 step 5).map { m ->
        GlucoseReading(from.plus(Duration.ofMinutes(m.toLong())), mgDl(m), TrendDirection.STEADY, SourceKind.LIBRE_BLE)
    }

    private val fake: (Int, Array<out Any>) -> String = { id, a ->
        when (id) {
            R.string.morning_title -> "TIR ${a[0]}"
            R.string.morning_lows -> "lows ${a[0]} min ${a[1]}"
            R.string.morning_no_lows -> "no lows"
            R.string.morning_highs -> "high ${a[0]}"
            R.string.morning_no_highs -> "no highs"
            R.string.morning_insight -> "look: ${a[0]}"
            else -> "?"
        }
    }

    @Test
    fun `a steady day is all in range, no lows, no highs`() {
        val y = MorningSummary.yesterday(day { 120 }, from, to)!!
        assertEquals(Yesterday(100, 0, null, 0), y)
        assertEquals("TIR 100" to "no lows · no highs", MorningSummary.words(y, "0 min", null, fake))
    }

    @Test
    fun `lows are 15-minute episodes with their lowest, highs are minutes above 180`() {
        val readings = day { m ->
            when (m) {
                135 -> 52
                in 120 until 150 -> 60 // 30 min low
                600 -> 65 // a 5 min dip: not an episode
                in 900 until 1020 -> 220 // 2 h high
                else -> 120
            }
        }
        val y = MorningSummary.yesterday(readings, from, to)!!
        assertEquals(Yesterday(inRange = 89, lows = 1, lowest = 52, highMinutes = 120), y) // (1440 - 30 - 5 - 120) / 1440
        assertEquals("TIR 89" to "lows 1 min 52 · high 2 h\nlook: Lows cluster", MorningSummary.words(y, "2 h", "Lows cluster", fake))
    }

    @Test
    fun `under 6 hours of readings sends nothing, and other days don't count`() {
        assertNull(MorningSummary.yesterday(day { 120 }.take(5 * 12), from, to))
        assertNull(MorningSummary.yesterday(day { 120 }.map { it.copy(timestamp = it.timestamp.plus(Duration.ofDays(1))) }, from, to))
    }
}
