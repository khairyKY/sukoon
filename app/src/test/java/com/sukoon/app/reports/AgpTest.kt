package com.sukoon.app.reports

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgpTest {

    private val zone = ZoneOffset.UTC
    private val day0 = Instant.parse("2026-09-20T00:00:00Z")
    private val now = day0.plus(Duration.ofDays(14))

    /** Every 5 minutes for 14 days: 100 overnight (00–06), 150 the rest of the day. */
    private val readings = (0 until 14).flatMap { d ->
        (0 until 1440 step 5).map { m ->
            GlucoseReading(day0.plus(Duration.ofDays(d.toLong())).plus(Duration.ofMinutes(m.toLong())), if (m < 360) 100 else 150, TrendDirection.STEADY, SourceKind.LIBRE_BLE)
        }
    }

    @Test
    fun `the profile follows the time of day`() {
        val report = Agp.build(readings, now, zone, days = 14)!!
        assertEquals(96, report.profile.size)
        assertEquals(100, report.profile[3 * 4]!!.p50) // 03:00
        assertEquals(150, report.profile[12 * 4]!!.p50) // 12:00
        assertEquals(100, report.activePercent)
        assertEquals(14, report.daysWithData)
        assertEquals(100.0, report.summary.inRange, 1e-9)
    }

    @Test
    fun `percentiles interpolate`() {
        val values = listOf(10, 20, 30, 40, 50)
        assertEquals(12, Agp.percentile(values, 0.05))
        assertEquals(20, Agp.percentile(values, 0.25))
        assertEquals(30, Agp.percentile(values, 0.5))
        assertEquals(48, Agp.percentile(values, 0.95))
    }

    @Test
    fun `no readings, no report`() {
        assertNull(Agp.build(emptyList(), now, zone, days = 14))
    }
}
