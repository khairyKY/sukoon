package com.sukoon.app.reports

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.insights.RangeSummary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** One point of the profile: percentiles of every reading near this time of day. */
data class AgpPoint(val minuteOfDay: Int, val p5: Int, val p25: Int, val p50: Int, val p75: Int, val p95: Int)

/**
 * The Ambulatory Glucose Profile plus the consensus metrics that go with it (Battelino et al.,
 * Diabetes Care 2019): the one-page summary clinics read.
 */
data class AgpReport(
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    val daysWithData: Int,
    /** Share of the period's 15-minute slots with at least one reading ("time CGM active"). */
    val activePercent: Int,
    val summary: RangeSummary,
    val cvPercent: Int,
    /** Every 15 minutes from midnight; null where too few readings fall near that time. */
    val profile: List<AgpPoint?>,
)

object Agp {
    const val SLOT_MINUTES = 15
    private const val HALF_WINDOW = 30 // each point pools readings within ±30 min of its time of day
    private const val MIN_VALUES = 5

    fun build(all: List<GlucoseReading>, now: Instant, zone: ZoneId, days: Int): AgpReport? {
        val start = now.minus(Duration.ofDays(days.toLong()))
        val readings = all.filter { it.timestamp > start && it.timestamp <= now }.sortedBy { it.timestamp }
        val summary = InsightEngine.summary(readings, now) ?: return null
        val byMinute = Array(1440) { mutableListOf<Int>() }
        readings.forEach { r ->
            val local = r.timestamp.atZone(zone)
            byMinute[local.hour * 60 + local.minute] += r.glucoseMgDl
        }
        val profile = (0 until 1440 step SLOT_MINUTES).map { center ->
            val values = (-HALF_WINDOW..HALF_WINDOW).flatMap { byMinute[Math.floorMod(center + it, 1440)] }.sorted()
            if (values.size < MIN_VALUES) {
                null
            } else {
                AgpPoint(center, percentile(values, 0.05), percentile(values, 0.25), percentile(values, 0.5), percentile(values, 0.75), percentile(values, 0.95))
            }
        }
        val slots = days * 1440 / SLOT_MINUTES
        val filled = readings.map { it.timestamp.epochSecond / (SLOT_MINUTES * 60) }.distinct().size
        return AgpReport(
            from = start.atZone(zone).toLocalDate(),
            to = now.atZone(zone).toLocalDate(),
            days = days,
            daysWithData = readings.map { it.timestamp.atZone(zone).toLocalDate() }.distinct().size,
            activePercent = (filled * 100.0 / slots).roundToInt().coerceAtMost(100),
            summary = summary,
            cvPercent = InsightEngine.cvPercent(readings, now),
            profile = profile,
        )
    }

    /** Linear-interpolated percentile of [sorted] (q in 0..1). */
    internal fun percentile(sorted: List<Int>, q: Double): Int {
        val position = (sorted.size - 1) * q
        val low = sorted[floor(position).toInt()]
        val high = sorted[ceil(position).toInt()]
        return (low + (high - low) * (position - floor(position))).roundToInt()
    }
}
