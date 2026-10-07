package com.sukoon.app.insulin

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.insights.MealSlot
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Learns the carb ratio each meal really needed from clean logged meals (docs/research/dosing-sources.md).
 * If glucose 4 h after eating moved by Δ, the meal needed Δ / correction factor more (or less) insulin
 * than was taken: ratio = carbs / (insulin + Δ / factor). Pure, so it's unit-tested.
 */
object RatioLearner {

    const val MIN_MEALS = 5

    /** What the clean meals of one meal time point to: the median ratio and the middle half of them. */
    data class Learned(val slot: MealSlot, val ratio: Double, val low: Double, val high: Double, val meals: Int)

    fun learn(readings: List<GlucoseReading>, events: List<EventEntity>, correctionFactor: Double, zone: ZoneId, action: InsulinAction): List<Learned> {
        if (correctionFactor <= 0) return emptyList()
        val sorted = events.sortedBy { it.timestampMillis }
        val ratios = InsightEngine.mealResults(readings.sortedBy { it.timestamp }, sorted, zone).mapNotNull { r ->
            val t = r.atMillis
            val change = r.change4h ?: return@mapNotNull null
            if (r.insulin <= 0 || r.carbs < 10 || r.start !in 70..250) return@mapNotNull null
            // Clean: nothing else eaten from an hour before to 4 h after, no late correction, no insulin still working from before.
            val hour = Duration.ofHours(1).toMillis()
            val otherFood = sorted.count { it.logType == LogEventType.CARB && (it.value ?: 0.0) > 0 && it.timestampMillis in (t - hour)..(t + 4 * hour) } > 1
            val lateInsulin = sorted.any { it.logType == LogEventType.INSULIN && it.timestampMillis in (t + 30 * 60_000L + 1)..(t + 4 * hour) }
            val before = InsulinOnBoard.total(sorted.filter { it.timestampMillis < t - hour }, Instant.ofEpochMilli(t), action)
            if (otherFood || lateInsulin || before >= 0.5) return@mapNotNull null
            val needed = r.insulin + change / correctionFactor
            if (needed < 0.3) return@mapNotNull null
            (r.carbs / needed).takeIf { it in 3.0..60.0 }?.let { r.slot to it }
        }
        return ratios.groupBy({ it.first }, { it.second }).mapNotNull { (slot, values) ->
            if (values.size < MIN_MEALS) return@mapNotNull null
            val v = values.sorted()
            fun at(q: Double) = v[((v.size - 1) * q).roundToInt()]
            Learned(slot, half(at(0.5)), half(at(0.25)), half(at(0.75)), v.size)
        }.sortedBy { it.slot.ordinal }
    }

    /** Moves from [current] toward [learned] by at most 20% at a time (a step, then learn again). */
    fun step(current: Double?, learned: Double): Double =
        if (current == null) learned else half(learned.coerceIn(current * 0.8, current * 1.2))

    private fun half(x: Double) = (x * 2).roundToInt() / 2.0
}
