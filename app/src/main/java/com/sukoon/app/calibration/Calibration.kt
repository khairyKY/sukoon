package com.sukoon.app.calibration

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.TrendDirection
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/** A finger-prick and the raw (uncalibrated) sensor reading nearest it. */
data class CalibrationPair(val at: Instant, val rawMgDl: Int, val meterMgDl: Int, val trend: TrendDirection)

/**
 * reading → slope × reading + intercept, within caps, with one hard safety rule: a reading under
 * 70 is never raised — calibration may make a low look lower, never hide it.
 */
data class Calibration(val slope: Double, val intercept: Double, val pairsUsed: Int) {

    fun apply(rawMgDl: Int): Int {
        val adjusted = (slope * rawMgDl + intercept).roundToInt().coerceIn(MIN_MG_DL, MAX_MG_DL)
        return if (rawMgDl < Calibrator.LOW_MG_DL) minOf(adjusted, rawMgDl) else adjusted
    }

    fun apply(reading: GlucoseReading): GlucoseReading = reading.copy(glucoseMgDl = apply(reading.glucoseMgDl))

    private companion object {
        const val MIN_MG_DL = 40
        const val MAX_MG_DL = 500
    }
}

enum class SkipReason { CHANGING_FAST, TOO_FAR_APART }

data class FitResult(val calibration: Calibration?, val used: List<CalibrationPair>, val skipped: List<Pair<CalibrationPair, SkipReason>>)

/**
 * Finger-prick calibration (B10), after the weighted least-squares idea DiaBox credits
 * (PMC4764224) but deliberately conservative:
 * - only finger-pricks from the last 7 days, the newest 4, newer ones weighing more;
 * - only while glucose was steady (a fast arrow means the sensor trails the blood by minutes);
 * - pairs further apart than 40 mg/dL or 40 % are left out (a bad strip, or a failing sensor
 *   that calibration shouldn't paper over);
 * - a slope only when the pairs span 40+ mg/dL, else an offset; slope capped to 0.8–1.25 and
 *   offset to ±20 mg/dL, so one bad entry can't skew everything.
 */
object Calibrator {
    const val LOW_MG_DL = 70
    const val MAX_PAIRS = 4
    val MAX_AGE: Duration = Duration.ofDays(7)
    const val SLOPE_MIN = 0.8
    const val SLOPE_MAX = 1.25
    const val INTERCEPT_LIMIT = 20.0
    private const val MIN_SPREAD = 40

    fun fit(pairs: List<CalibrationPair>, now: Instant): FitResult {
        val recent = pairs.filter { !it.at.isAfter(now) && Duration.between(it.at, now) <= MAX_AGE }.sortedByDescending { it.at }
        val skipped = recent.mapNotNull { p ->
            when {
                p.trend == TrendDirection.FALLING_FAST || p.trend == TrendDirection.RISING_FAST -> p to SkipReason.CHANGING_FAST
                abs(p.meterMgDl - p.rawMgDl) > maxOf(40.0, 0.4 * p.meterMgDl) -> p to SkipReason.TOO_FAR_APART
                else -> null
            }
        }
        val used = (recent - skipped.map { it.first }.toSet()).take(MAX_PAIRS)
        if (used.isEmpty()) return FitResult(null, used, skipped)

        val w = used.indices.map { 1.0 / (it + 1) } // newest first: 1, ½, ⅓, ¼
        val total = w.sum()
        val mx = used.indices.sumOf { w[it] * used[it].rawMgDl } / total
        val my = used.indices.sumOf { w[it] * used[it].meterMgDl } / total
        val spread = used.maxOf { it.rawMgDl } - used.minOf { it.rawMgDl }
        val slope = if (used.size >= 2 && spread >= MIN_SPREAD) {
            val sxx = used.indices.sumOf { w[it] * (used[it].rawMgDl - mx) * (used[it].rawMgDl - mx) }
            val sxy = used.indices.sumOf { w[it] * (used[it].rawMgDl - mx) * (used[it].meterMgDl - my) }
            (sxy / sxx).coerceIn(SLOPE_MIN, SLOPE_MAX)
        } else {
            1.0
        }
        val intercept = (my - slope * mx).coerceIn(-INTERCEPT_LIMIT, INTERCEPT_LIMIT)
        return FitResult(Calibration(slope, intercept, used.size), used, skipped)
    }
}
