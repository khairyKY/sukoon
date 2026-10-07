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
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Learns the carb ratio each meal really needed from clean logged meals (docs/research/dosing-sources.md).
 * If glucose 4 h after eating moved by Δ, the meal needed Δ / correction factor more (or less) insulin
 * than was taken: ratio = carbs / (insulin + Δ / factor). Every meal gets a verdict, so the Learning
 * screen and its export show what counted and why the rest didn't. Pure, so it's unit-tested.
 */
object RatioLearner {

    const val MIN_MEALS = 5
    const val MIN_CARBS = 10.0
    /** ponytail: Bell et al. 2015's rich test meal had 44 g fat / 36 g protein; a later rise skews the 4 h change. */
    const val RICH_GRAMS = 35.0

    /** Why a meal was left out, or CLEAN. In the order they're checked. */
    enum class Verdict { CLEAN, TOO_SMALL, NO_INSULIN, NO_READINGS, OUT_OF_RANGE, ATE_AGAIN, MORE_INSULIN, STILL_WORKING, ACTIVE, RICH, ODD }

    /** One meal as the learner saw it; [ratio] only for a clean meal with a correction factor. */
    data class Meal(
        val atMillis: Long,
        val slot: MealSlot,
        val carbs: Double,
        val insulin: Double,
        val start: Int?,
        val change4h: Int?,
        val verdict: Verdict,
        val ratio: Double?,
    )

    /** What the clean meals of one meal time point to: the median ratio and the middle half of them. */
    data class Learned(val slot: MealSlot, val ratio: Double, val low: Double, val high: Double, val meals: List<Meal>, val factor: Double)

    /** Everything for the Learning screen: every meal, newest first, the factor used and the lessons. */
    data class Report(val meals: List<Meal>, val factor: Double?, val factorEstimated: Boolean, val learned: List<Learned>)

    fun review(readings: List<GlucoseReading>, events: List<EventEntity>, correctionFactor: Double?, zone: ZoneId, action: InsulinAction): List<Meal> {
        val sorted = events.sortedBy { it.timestampMillis }
        val results = InsightEngine.mealResults(readings.sortedBy { it.timestamp }, sorted, zone).associateBy { it.atMillis }
        val hour = Duration.ofHours(1).toMillis()
        return sorted.filter { it.logType == LogEventType.CARB && (it.value ?: 0.0) > 0 }.map { meal ->
            val t = meal.timestampMillis
            val r = results[t]
            val carbs = meal.value ?: 0.0
            val insulin = r?.insulin ?: sorted.filter { it.logType == LogEventType.INSULIN && it.timestampMillis in (t - hour)..(t + 30 * 60_000L) }.sumOf { it.value ?: 0.0 }
            val change = r?.change4h
            var verdict = when {
                carbs < MIN_CARBS -> Verdict.TOO_SMALL
                insulin <= 0 -> Verdict.NO_INSULIN
                r == null || change == null -> Verdict.NO_READINGS
                r.start !in 70..250 -> Verdict.OUT_OF_RANGE
                sorted.count { it.logType == LogEventType.CARB && (it.value ?: 0.0) > 0 && it.timestampMillis in (t - hour)..(t + 4 * hour) } > 1 -> Verdict.ATE_AGAIN
                sorted.any { it.logType == LogEventType.INSULIN && it.timestampMillis in (t + 30 * 60_000L + 1)..(t + 4 * hour) } -> Verdict.MORE_INSULIN
                InsulinOnBoard.total(sorted.filter { it.timestampMillis < t - hour }, Instant.ofEpochMilli(t), action) >= 0.5 -> Verdict.STILL_WORKING
                sorted.any { it.logType == LogEventType.ACTIVITY && it.timestampMillis in (t - 2 * hour)..(t + 4 * hour) } -> Verdict.ACTIVE
                (meal.fat ?: 0.0) >= RICH_GRAMS || (meal.protein ?: 0.0) >= RICH_GRAMS -> Verdict.RICH
                else -> Verdict.CLEAN
            }
            var ratio: Double? = null
            if (verdict == Verdict.CLEAN && change != null && correctionFactor != null && correctionFactor > 0) {
                val needed = insulin + change / correctionFactor
                ratio = if (needed < 0.3) null else (carbs / needed).takeIf { it in 3.0..60.0 }
                if (ratio == null) verdict = Verdict.ODD
            }
            Meal(t, InsightEngine.slotFor(Instant.ofEpochMilli(t).atZone(zone).hour), carbs, insulin, r?.start, change, verdict, ratio)
        }.sortedByDescending { it.atMillis }
    }

    fun learn(readings: List<GlucoseReading>, events: List<EventEntity>, correctionFactor: Double, zone: ZoneId, action: InsulinAction): List<Learned> =
        lessons(review(readings, events, correctionFactor, zone, action), correctionFactor)

    /** [correctionFactor] is the user's, else the 1800 rule's estimate from the logbook (null: neither yet). */
    fun report(readings: List<GlucoseReading>, events: List<EventEntity>, correctionFactor: Double?, zone: ZoneId, action: InsulinAction): Report {
        val factor = correctionFactor ?: InsightEngine.formulas(events, zone)?.mgDlPerUnit1800
        val meals = review(readings, events, factor, zone, action)
        return Report(meals, factor, correctionFactor == null && factor != null, factor?.let { lessons(meals, it) }.orEmpty())
    }

    private fun lessons(meals: List<Meal>, factor: Double): List<Learned> =
        meals.filter { it.ratio != null }.groupBy { it.slot }.mapNotNull { (slot, clean) ->
            if (clean.size < MIN_MEALS) return@mapNotNull null
            val v = clean.map { it.ratio!! }.sorted()
            fun at(q: Double) = v[((v.size - 1) * q).roundToInt()]
            Learned(slot, half(at(0.5)), half(at(0.25)), half(at(0.75)), clean, factor)
        }.sortedBy { it.slot.ordinal }

    /** Moves from [current] toward [learned] by at most 20% at a time (a step, then learn again). */
    fun step(current: Double?, learned: Double): Double =
        if (current == null) learned else half(learned.coerceIn(current * 0.8, current * 1.2))

    /** The report as CSV: one row per meal, oldest first, with its verdict and the factor used. */
    fun csv(report: Report, zone: ZoneId): String {
        val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(zone)
        fun n(x: Double?) = x?.let { if (it % 1.0 == 0.0) it.toLong().toString() else String.format(Locale.US, "%.2f", it) }.orEmpty()
        val header = "timestamp,meal,carbs_g,insulin_units,start_mgdl,change_4h_mgdl,counts,reason,ratio_g_per_unit,correction_factor_used"
        val rows = report.meals.sortedBy { it.atMillis }.map { m ->
            listOf(
                time.format(Instant.ofEpochMilli(m.atMillis)), m.slot.name.lowercase(), n(m.carbs), n(m.insulin), m.start?.toString().orEmpty(), m.change4h?.toString().orEmpty(),
                if (m.verdict == Verdict.CLEAN) "yes" else "no", m.verdict.name.lowercase(), n(m.ratio), n(report.factor),
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n", postfix = "\n")
    }

    private fun half(x: Double) = (x * 2).roundToInt() / 2.0
}
