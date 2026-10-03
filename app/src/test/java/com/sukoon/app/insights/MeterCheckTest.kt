package com.sukoon.app.insights

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterCheckTest {

    private val t0 = Instant.parse("2026-10-03T10:00:00Z")
    private fun r(minute: Long, mgDl: Int) = GlucoseReading(t0.plusSeconds(minute * 60), mgDl, TrendDirection.STEADY, SourceKind.LIBRE_BLE)
    private fun at(minute: Long) = t0.plusSeconds(minute * 60).toEpochMilli()

    @Test
    fun `20-20 band is absolute under 100 and relative from 100`() {
        assertTrue(MeterCheck(80, 100).agrees)
        assertFalse(MeterCheck(80, 101).agrees)
        assertTrue(MeterCheck(200, 240).agrees)
        assertFalse(MeterCheck(200, 241).agrees)
        assertEquals(20, MeterCheck(200, 240).percentDiff)
        assertEquals(-10, MeterCheck(100, 90).percentDiff)
    }

    @Test
    fun `pairs with the nearest reading within five minutes only`() {
        val readings = listOf(r(0, 100), r(9, 130))
        assertEquals(MeterCheck(110, 130), MeterCheck.of(110, at(6), readings)) // 3 min from the second reading
        assertNull(MeterCheck.of(110, at(20), readings)) // 11 min from the nearest
        assertNull(MeterCheck.of(0, at(0), readings))
    }
}
