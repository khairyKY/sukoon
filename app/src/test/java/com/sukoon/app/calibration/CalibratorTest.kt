package com.sukoon.app.calibration

import com.sukoon.app.data.source.TrendDirection
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibratorTest {

    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private fun pair(hoursAgo: Long, raw: Int, meter: Int, trend: TrendDirection = TrendDirection.STEADY) =
        CalibrationPair(now.minus(Duration.ofHours(hoursAgo)), raw, meter, trend)

    @Test
    fun `one finger-prick gives an offset`() {
        val c = Calibrator.fit(listOf(pair(1, 100, 110)), now).calibration!!
        assertEquals(1.0, c.slope, 1e-9)
        assertEquals(10.0, c.intercept, 1e-9)
        assertEquals(160, c.apply(150))
    }

    @Test
    fun `a low is never raised into range`() {
        val c = Calibrator.fit(listOf(pair(1, 100, 120)), now).calibration!! // the sensor reads 20 low
        assertEquals(64, c.apply(64)) // would be 84 — stays a low
        assertEquals(54, c.apply(54))
        assertEquals(90, c.apply(70)) // 70 and up are adjusted normally
    }

    @Test
    fun `lowering is always allowed`() {
        val c = Calibrator.fit(listOf(pair(1, 120, 100)), now).calibration!!
        assertEquals(60, c.apply(80)) // in range by the sensor, low by the calibration: alarms fire
        assertEquals(45, c.apply(65))
    }

    @Test
    fun `two pairs far enough apart give a slope`() {
        val c = Calibrator.fit(listOf(pair(1, 200, 230), pair(5, 100, 110)), now).calibration!!
        assertEquals(1.2, c.slope, 1e-9)
        assertEquals(-10.0, c.intercept, 1e-9)
        assertEquals(170, c.apply(150))
    }

    @Test
    fun `caps hold against a wild fit`() {
        val offset = Calibrator.fit(listOf(pair(1, 100, 150)), now).calibration!!
        assertEquals(20.0, offset.intercept, 1e-9)
        val steep = Calibrator.fit(listOf(pair(1, 200, 300), pair(5, 100, 100)), now).calibration!!
        assertEquals(1.25, steep.slope, 1e-9)
        assertEquals(20.0, steep.intercept, 1e-9)
    }

    @Test
    fun `fast-changing, far-apart and old pairs are left out`() {
        val result = Calibrator.fit(
            listOf(
                pair(1, 100, 112, TrendDirection.FALLING_FAST),
                pair(2, 100, 190), // 90 apart: a bad strip or a failing sensor
                pair(24 * 8, 100, 110), // over a week old
            ),
            now,
        )
        assertNull(result.calibration)
        assertEquals(listOf(SkipReason.CHANGING_FAST, SkipReason.TOO_FAR_APART), result.skipped.map { it.second })
    }

    @Test
    fun `only the newest four count, newest weighing most`() {
        val pairs = listOf(pair(1, 100, 120), pair(2, 100, 100), pair(3, 100, 100), pair(4, 100, 100), pair(5, 100, 100))
        val c = Calibrator.fit(pairs, now).calibration!!
        assertEquals(4, c.pairsUsed)
        // Offsets 20, 0, 0, 0 weighted 1, ½, ⅓, ¼ → 20 / (25/12) = 9.6
        assertEquals(9.6, c.intercept, 1e-9)
    }
}
