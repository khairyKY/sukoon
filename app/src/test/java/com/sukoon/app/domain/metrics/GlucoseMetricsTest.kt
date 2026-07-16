package com.sukoon.app.domain.metrics

import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseMetricsTest {

    private val baseTime: Instant = Instant.parse("2026-07-16T08:00:00Z")

    private fun samples(vararg values: Int, everyMinutes: Long = 5): List<GlucoseSample> =
        values.mapIndexed { i, v -> GlucoseSample(baseTime.plusSeconds(i * everyMinutes * 60), v) }

    // --- Units ---

    @Test
    fun `mg dL to mmol L conversion`() {
        assertEquals(10.0, GlucoseMetrics.mgDlToMmolL(180), 0.0001)
    }

    @Test
    fun `mmol L to mg dL conversion`() {
        assertEquals(180, GlucoseMetrics.mmolLToMgDl(10.0))
    }

    // --- Range brackets ---

    @Test
    fun `bracket boundaries match PLAN md §3 thresholds`() {
        assertEquals(GlucoseMetrics.RangeBracket.VERY_LOW, GlucoseMetrics.bracketFor(53))
        assertEquals(GlucoseMetrics.RangeBracket.LOW, GlucoseMetrics.bracketFor(54))
        assertEquals(GlucoseMetrics.RangeBracket.LOW, GlucoseMetrics.bracketFor(69))
        assertEquals(GlucoseMetrics.RangeBracket.IN_RANGE, GlucoseMetrics.bracketFor(70))
        assertEquals(GlucoseMetrics.RangeBracket.IN_RANGE, GlucoseMetrics.bracketFor(180))
        assertEquals(GlucoseMetrics.RangeBracket.HIGH, GlucoseMetrics.bracketFor(181))
        assertEquals(GlucoseMetrics.RangeBracket.HIGH, GlucoseMetrics.bracketFor(250))
        assertEquals(GlucoseMetrics.RangeBracket.VERY_HIGH, GlucoseMetrics.bracketFor(251))
    }

    @Test
    fun `time in range percentages sum to 100`() {
        // 1 very-low, 2 low, 2 in-range, 1 high, 1 very-high = 7 readings
        val result = GlucoseMetrics.timeInRange(samples(40, 60, 65, 100, 150, 200, 260))

        assertEquals(1 / 7.0 * 100, result[GlucoseMetrics.RangeBracket.VERY_LOW]!!, 0.01)
        assertEquals(2 / 7.0 * 100, result[GlucoseMetrics.RangeBracket.LOW]!!, 0.01)
        assertEquals(2 / 7.0 * 100, result[GlucoseMetrics.RangeBracket.IN_RANGE]!!, 0.01)
        assertEquals(1 / 7.0 * 100, result[GlucoseMetrics.RangeBracket.HIGH]!!, 0.01)
        assertEquals(1 / 7.0 * 100, result[GlucoseMetrics.RangeBracket.VERY_HIGH]!!, 0.01)
        assertEquals(100.0, result.values.sum(), 0.01)
    }

    // --- GMI ---

    @Test
    fun `GMI matches the published formula`() {
        // Mean 154 mg/dL is the textbook "about 7% A1C" reference point.
        assertEquals(6.99368, GlucoseMetrics.gmiPercent(154.0), 0.0001)
    }

    // --- SD / CV ---

    @Test
    fun `SD and CV on a simple known series`() {
        val readings = samples(90, 100, 110)
        // mean=100, variance=((10^2)+(0^2)+(10^2))/(3-1)=10 -> SD=10
        assertEquals(10.0, GlucoseMetrics.standardDeviation(readings), 0.0001)
        assertEquals(10.0, GlucoseMetrics.coefficientOfVariationPercent(readings), 0.0001)
    }

    // --- GVI / GVP ---

    @Test
    fun `GVI is exactly 1_0 for a perfectly flat trace`() {
        val flat = samples(100, 100, 100, 100)
        assertEquals(1.0, GlucoseMetrics.glycemicVariabilityIndex(flat), 0.0001)
        assertEquals(0.0, GlucoseMetrics.glucoseVariabilityPercent(flat), 0.0001)
    }

    @Test
    fun `GVI exceeds 1_0 for a varying trace`() {
        val varying = samples(90, 150, 80, 160, 90)
        assertTrue(GlucoseMetrics.glycemicVariabilityIndex(varying) > 1.0)
    }

    // --- PGS ---

    @Test
    fun `PGS scores poor control worse than good control`() {
        val wellControlled = samples(95, 100, 105, 100, 95, 100, 105, 100)
        val poorControl = samples(40, 180, 50, 220, 45, 240, 40, 230)

        val goodScore = GlucoseMetrics.patientGlycemicStatus(wellControlled, n54 = 0, n70 = 0)
        val badScore = GlucoseMetrics.patientGlycemicStatus(poorControl, n54 = 3, n70 = 2)

        assertTrue("expected poor-control PGS ($badScore) > well-controlled PGS ($goodScore)", badScore > goodScore)
    }

    // --- Trend ---

    @Test
    fun `trend detects a fast fall`() {
        // -45 mg/dL over 15 min = -3 mg/dL/min
        val readings = samples(150, 105, everyMinutes = 15)
        assertEquals(TrendDirection.FALLING_FAST, GlucoseMetrics.trendFor(readings))
    }

    @Test
    fun `trend detects a fast rise`() {
        // +45 mg/dL over 15 min = +3 mg/dL/min
        val readings = samples(105, 150, everyMinutes = 15)
        assertEquals(TrendDirection.RISING_FAST, GlucoseMetrics.trendFor(readings))
    }

    @Test
    fun `trend detects steady`() {
        val readings = samples(100, 101, everyMinutes = 15)
        assertEquals(TrendDirection.STEADY, GlucoseMetrics.trendFor(readings))
    }

    // --- Empty input must not crash ---

    @Test
    fun `empty input does not crash and returns zeroed metrics`() {
        val empty = emptyList<GlucoseSample>()
        assertEquals(0.0, GlucoseMetrics.mean(empty), 0.0)
        assertEquals(0.0, GlucoseMetrics.standardDeviation(empty), 0.0)
        assertEquals(0.0, GlucoseMetrics.coefficientOfVariationPercent(empty), 0.0)
        assertEquals(1.0, GlucoseMetrics.glycemicVariabilityIndex(empty), 0.0)
        assertEquals(TrendDirection.STEADY, GlucoseMetrics.trendFor(empty))
    }
}
