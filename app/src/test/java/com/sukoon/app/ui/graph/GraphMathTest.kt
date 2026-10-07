package com.sukoon.app.ui.graph

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class GraphMathTest {

    private val t0 = Instant.parse("2026-10-03T00:00:00Z")
    private fun r(minute: Long, mgDl: Int) = GlucoseReading(t0.plusSeconds(minute * 60), mgDl, TrendDirection.STEADY, SourceKind.LIBRE_BLE)

    @Test
    fun `readings list groups by day, newest first, with each change`() {
        // t0 is midnight UTC: two readings the evening before, two after.
        val days = readingDays(listOf(r(-2, 100), r(-1, 104), r(1, 110), r(40, 120)), ZoneOffset.UTC, Locale.US)
        assertEquals(listOf(LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-02")), days.map { it.date })
        assertEquals(listOf("00:40", "00:01"), days[0].rows.map { it.time })
        assertEquals(listOf(null, "+6"), days[0].rows.map { it.delta }) // 00:40 is 39 min after 00:01: no change shown
        assertEquals(listOf("23:59", "23:58"), days[1].rows.map { it.time })
        assertEquals(listOf("+4", null), days[1].rows.map { it.delta }) // the oldest has nothing before it
    }

    @Test
    fun `downsample averages each bucket and stamps its middle`() {
        val out = downsample(listOf(r(0, 100), r(5, 110), r(14, 120), r(15, 200)), 15 * 60_000L)
        assertEquals(listOf(110, 200), out.map { it.glucoseMgDl })
        assertEquals(t0.plusSeconds(450), out[0].timestamp)
        assertEquals(t0.plusSeconds(1350), out[1].timestamp)
    }

    @Test
    fun `y axis keeps the target band readable and fits the data`() {
        assertEquals(250, yMaxFor(listOf(r(0, 142))))
        assertEquals(300, yMaxFor(listOf(r(0, 260))))
        assertEquals(400, yMaxFor(listOf(r(0, 395))))
        assertEquals(250, yMaxFor(emptyList()))
    }

    @Test
    fun `ticks land on round local hours and midnights`() {
        val day = 24 * 3_600_000L
        val start = t0.toEpochMilli() + 30 * 60_000L // 00:30
        assertEquals(listOf(6, 12, 18, 24).map { t0.toEpochMilli() + it * 3_600_000L }, timeTicks(start, start + day, 6, ZoneOffset.UTC))
        assertEquals(3, timeTicks(start, start + 3 * day, 24, ZoneOffset.UTC).size)
        assertEquals(listOf(1, 2, 3).map { t0.toEpochMilli() + it * 3_600_000L }, timeTicks(start, start + 3 * 3_600_000L, 1, ZoneOffset.UTC))
    }
}
