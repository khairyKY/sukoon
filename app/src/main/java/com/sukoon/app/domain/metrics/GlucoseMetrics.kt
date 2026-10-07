package com.sukoon.app.domain.metrics

import com.sukoon.app.data.source.TrendDirection
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * A single (time, glucose) sample for metrics calculations — decoupled from the DB/source
 * layers so this module stays pure Kotlin with no Android/Room dependency, and fast to
 * unit-test on the plain JVM.
 */
data class GlucoseSample(val timestamp: Instant, val glucoseMgDl: Int)

/** Formulas and thresholds cited in docs/PLAN.md §3. */
object GlucoseMetrics {

    // --- Units ---
    fun mgDlToMmolL(mgDl: Int): Double = mgDl / 18.0
    fun mmolLToMgDl(mmolL: Double): Int = (mmolL * 18.0).toInt()

    // --- Time in Range brackets ---
    enum class RangeBracket { VERY_LOW, LOW, IN_RANGE, HIGH, VERY_HIGH }

    /** [high]: the top of the range; the international 180 unless a caller passes yours ([TargetRange.high]). */
    fun bracketFor(mgDl: Int, high: Int = 180): RangeBracket = when {
        mgDl < 54 -> RangeBracket.VERY_LOW
        mgDl < 70 -> RangeBracket.LOW
        mgDl <= high -> RangeBracket.IN_RANGE
        mgDl <= 250 -> RangeBracket.HIGH
        else -> RangeBracket.VERY_HIGH
    }

    /** Percentage of readings in each bracket. Empty input returns all-zero percentages. */
    fun timeInRange(readings: List<GlucoseSample>, high: Int = 180): Map<RangeBracket, Double> {
        if (readings.isEmpty()) return RangeBracket.entries.associateWith { 0.0 }
        val counts = readings.groupingBy { bracketFor(it.glucoseMgDl, high) }.eachCount()
        return RangeBracket.entries.associateWith { bracket ->
            (counts[bracket] ?: 0) * 100.0 / readings.size
        }
    }

    // --- GMI (Glucose Management Indicator) / eA1C ---
    fun gmiPercent(meanGlucoseMgDl: Double): Double = 3.31 + 0.02392 * meanGlucoseMgDl

    // --- Variability: mean / SD / CV ---
    fun mean(readings: List<GlucoseSample>): Double {
        if (readings.isEmpty()) return 0.0
        return readings.map { it.glucoseMgDl }.average()
    }

    fun standardDeviation(readings: List<GlucoseSample>): Double {
        if (readings.size < 2) return 0.0
        val m = mean(readings)
        val variance = readings.sumOf { (it.glucoseMgDl - m) * (it.glucoseMgDl - m) } / (readings.size - 1)
        return sqrt(variance)
    }

    fun coefficientOfVariationPercent(readings: List<GlucoseSample>): Double {
        val m = mean(readings)
        if (m == 0.0) return 0.0
        return standardDeviation(readings) / m * 100.0
    }

    // --- GVI / GVP: trace length vs. a flat baseline over the same duration ---
    // Shared core: ratio = actual path length / flat-baseline length (always >= 1.0 for >=2
    // points). GVI exposes the ratio directly (scale: 1.0-1.2 low, 1.2-1.5 modest, >1.5 high).
    // GVP exposes it as a percentage-excess, which is what PGS's f(GVP) sub-function expects —
    // they're the same underlying computation, just two conventional presentations of it.
    private fun traceLengthRatio(readings: List<GlucoseSample>): Double {
        if (readings.size < 2) return 1.0
        val sorted = readings.sortedBy { it.timestamp }
        var pathLength = 0.0
        var flatLength = 0.0
        for (i in 1 until sorted.size) {
            val dtMinutes = Duration.between(sorted[i - 1].timestamp, sorted[i].timestamp).toMillis() / 60_000.0
            val dGlucose = (sorted[i].glucoseMgDl - sorted[i - 1].glucoseMgDl).toDouble()
            pathLength += sqrt(dtMinutes * dtMinutes + dGlucose * dGlucose)
            flatLength += dtMinutes
        }
        return if (flatLength == 0.0) 1.0 else pathLength / flatLength
    }

    fun glycemicVariabilityIndex(readings: List<GlucoseSample>): Double = traceLengthRatio(readings)

    fun glucoseVariabilityPercent(readings: List<GlucoseSample>): Double =
        (traceLengthRatio(readings) - 1.0) * 100.0

    // --- PGS: Patient Glycemic Status --- piecewise sigmoids per docs/PLAN.md §3.
    // n54/n70 are hypoglycemic-episode COUNTS over the scoring window, not percentages.
    fun patientGlycemicStatus(readings: List<GlucoseSample>, n54: Int, n70: Int): Double {
        val gvp = glucoseVariabilityPercent(readings)
        val meanGlucose = mean(readings)
        val ptir = timeInRange(readings)[RangeBracket.IN_RANGE] ?: 0.0

        val f = 1 + 9 / (1 + exp(-0.049 * (gvp - 65.47)))
        val g = 1 + 9 * (
            1 / (1 + exp(0.1139 * (meanGlucose - 72.08))) +
                1 / (1 + exp(-0.09195 * (meanGlucose - 157.57)))
            )
        val h = 1 + 9 / (1 + exp(0.0833 * (ptir - 55.04)))
        val a = 0.5 + 4.5 * (1 - exp(-0.91093 * n54))
        val b = if (n70 <= 7.65) 0.5714 * n70 + 0.625 else 5.0

        return f + g + h + a + b
    }

    // --- Trend arrow: rate of change over the trailing ~15-min window ---
    fun trendFor(readings: List<GlucoseSample>): TrendDirection {
        val sorted = readings.sortedBy { it.timestamp }
        if (sorted.size < 2) return TrendDirection.STEADY

        val last = sorted.last()
        val target = last.timestamp.minusSeconds(15 * 60)
        val reference = sorted.dropLast(1).minByOrNull { abs(Duration.between(it.timestamp, target).toMillis()) }
            ?: sorted.first()

        val minutes = Duration.between(reference.timestamp, last.timestamp).toMillis() / 60_000.0
        if (minutes <= 0.0) return TrendDirection.STEADY

        val rocPerMinute = (last.glucoseMgDl - reference.glucoseMgDl) / minutes
        return when {
            rocPerMinute < -2 -> TrendDirection.FALLING_FAST
            rocPerMinute < -1 -> TrendDirection.FALLING
            rocPerMinute <= 1 -> TrendDirection.STEADY
            rocPerMinute <= 2 -> TrendDirection.RISING
            else -> TrendDirection.RISING_FAST
        }
    }
}
