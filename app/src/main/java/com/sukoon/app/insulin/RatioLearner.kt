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

    /** Everything for the Learning screen: every meal, newest first, the factor used, the lessons, and the fit over everything. */
    data class Report(val meals: List<Meal>, val factor: Double?, val factorEstimated: Boolean, val learned: List<Learned>, val fit: Fit? = null)

    /** What all usable meals and corrections say together: how far 1 unit lowers you, and each meal time's ratio. */
    data class Fit(val factor: Double, val ratios: Map<MealSlot, Double>, val meals: Int, val corrections: Int)

    const val MIN_POINTS = 8
    const val MIN_PER_SLOT = 3

    /**
     * Least squares over every usable meal and correction-only dose of the window: the glucose change
     * at 4 h ≈ a(meal time) × carbs − b × insulin. b is the correction factor, b ÷ a that meal's ratio.
     * Needs no factor to start from, and uses everyday meals (no insulin is fine). A point counts when
     * nothing else was eaten, no other insulin was taken and none was still working, no workout, not
     * very rich, starting 70–300 with readings at the start and 4 h later. Null until there's enough,
     * with different amounts in it, and the answer is plausible.
     */
    fun fit(readings: List<GlucoseReading>, events: List<EventEntity>, zone: ZoneId, action: InsulinAction): Fit? {
        val r = readings.sortedBy { it.timestamp }
        val sorted = events.sortedBy { it.timestampMillis }
        val hour = Duration.ofHours(1).toMillis()
        fun near(at: Long, toleranceMin: Long) = r.filter { kotlin.math.abs(it.timestamp.toEpochMilli() - at) <= toleranceMin * 60_000 }.minByOrNull { kotlin.math.abs(it.timestamp.toEpochMilli() - at) }
        fun quiet(t: Long, dose: EventEntity?, mealId: Long? = null) =
            sorted.none { it.logType == LogEventType.ACTIVITY && it.timestampMillis in (t - 2 * hour)..(t + 4 * hour) } &&
                InsulinOnBoard.total(sorted.filter { it.timestampMillis < t - hour }, Instant.ofEpochMilli(t), action) < 0.5 &&
                sorted.none { it.logType == LogEventType.INSULIN && it !== dose && (mealId == null || it.mealId != mealId) && it.timestampMillis in (t + 30 * 60_000L + 1)..(t + 4 * hour) }
        data class Point(val slot: MealSlot?, val carbs: Double, val insulin: Double, val change: Double)
        val results = InsightEngine.mealResults(r, sorted, zone).associateBy { it.atMillis }
        val meals = sorted.filter { it.logType == LogEventType.CARB && (it.value ?: 0.0) >= MIN_CARBS }.mapNotNull { meal ->
            val t = meal.timestampMillis
            val res = results[t] ?: return@mapNotNull null
            val change = res.change4h ?: return@mapNotNull null
            val alone = sorted.count { it.logType == LogEventType.CARB && (it.value ?: 0.0) > 0 && it.timestampMillis in (t - hour)..(t + 4 * hour) } == 1
            val rich = (meal.fat ?: 0.0) >= RICH_GRAMS || (meal.protein ?: 0.0) >= RICH_GRAMS
            if (res.start !in 70..300 || !alone || rich || !quiet(t, null, meal.id)) return@mapNotNull null
            Point(res.slot, res.carbs, res.insulin, change.toDouble())
        }
        val corrections = sorted.filter { it.logType == LogEventType.INSULIN && (it.value ?: 0.0) > 0 }.mapNotNull { dose ->
            val t = dose.timestampMillis
            val ate = sorted.any { it.logType == LogEventType.CARB && (it.value ?: 0.0) > 0 && it.timestampMillis in (t - hour)..(t + 4 * hour) }
            val other = sorted.any { it.logType == LogEventType.INSULIN && it !== dose && it.timestampMillis in (t - hour)..(t + 4 * hour) }
            if (ate || other || !quiet(t, dose)) return@mapNotNull null
            val start = near(t, 15) ?: return@mapNotNull null
            val after = near(t + 4 * hour, 20) ?: return@mapNotNull null
            if (start.glucoseMgDl !in 70..400) return@mapNotNull null
            Point(null, 0.0, dose.value ?: 0.0, (after.glucoseMgDl - start.glucoseMgDl).toDouble())
        }
        val slots = meals.groupBy { it.slot!! }.filter { it.value.size >= MIN_PER_SLOT }.keys.sortedBy { it.ordinal }
        val points = meals.filter { it.slot in slots } + corrections
        if (points.size < MIN_POINTS || points.none { it.insulin > 0 } || slots.isEmpty()) return null
        // One column per meal time (its carbs, else 0) and one for −insulin; solve the normal equations.
        val k = slots.size + 1
        fun row(p: Point) = DoubleArray(k) { j -> if (j < slots.size) (if (p.slot == slots[j]) p.carbs else 0.0) else -p.insulin }
        val ata = Array(k) { DoubleArray(k) }
        val aty = DoubleArray(k)
        points.forEach { p ->
            val x = row(p)
            for (i in 0 until k) {
                aty[i] += x[i] * p.change
                for (j in 0 until k) ata[i][j] += x[i] * x[j]
            }
        }
        val beta = solve(ata, aty) ?: return null
        val b = beta.last()
        if (b !in 10.0..300.0) return null
        val ratios = slots.mapIndexedNotNull { i, s -> beta[i].takeIf { it > 0 }?.let { s to half(b / it) } }.filter { it.second in 3.0..60.0 }.toMap()
        return Fit(Math.round(b / 5) * 5.0, ratios, points.count { it.slot != null }, points.count { it.slot == null })
    }

    /** Gaussian elimination with partial pivoting; null when the data can't tell the numbers apart. */
    private fun solve(a: Array<DoubleArray>, y: DoubleArray): DoubleArray? {
        val n = y.size
        val m = Array(n) { i -> a[i].copyOf() + y[i] }
        for (c in 0 until n) {
            val p = (c until n).maxBy { kotlin.math.abs(m[it][c]) }
            if (kotlin.math.abs(m[p][c]) < 1e-9) return null
            val tmp = m[c]; m[c] = m[p]; m[p] = tmp
            for (rIdx in 0 until n) if (rIdx != c) {
                val f = m[rIdx][c] / m[c][c]
                for (j in c..n) m[rIdx][j] -= f * m[c][j]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }

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
                sorted.any { it.logType == LogEventType.INSULIN && it.mealId != meal.id && it.timestampMillis in (t + 30 * 60_000L + 1)..(t + 4 * hour) } -> Verdict.MORE_INSULIN
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
        return Report(meals, factor, correctionFactor == null && factor != null, factor?.let { lessons(meals, it) }.orEmpty(), fit(readings, events, zone, action))
    }

    private fun lessons(meals: List<Meal>, factor: Double): List<Learned> =
        meals.filter { it.ratio != null }.groupBy { it.slot }.mapNotNull { (slot, clean) ->
            if (clean.size < MIN_MEALS) return@mapNotNull null
            val v = clean.map { it.ratio!! }.sorted()
            fun at(q: Double) = v[((v.size - 1) * q).roundToInt()]
            Learned(slot, half(at(0.5)), half(at(0.25)), half(at(0.75)), clean, factor)
        }.sortedBy { it.slot.ordinal }

    /** Home's notice: a learned number that differs from yours. [slot] null = the correction factor. */
    data class Nudge(val slot: MealSlot?, val value: Double) {
        /** What "Not now" remembers: the same suggestion stays hidden, a different one shows. */
        val key: String get() = "${slot?.name ?: "FACTOR"}:$value"
    }

    /**
     * The one suggestion worth a notice on Home: a meal time's clean-meal lesson first, then what
     * everything logged says (a ratio, then the factor). Only when it's 10% or more from your number,
     * or you have none, so a small wobble doesn't nag.
     */
    fun nudge(report: Report, settings: DoseSettings): Nudge? {
        fun differs(current: Double?, learned: Double) = current == null || kotlin.math.abs(learned - current) >= current * 0.1
        report.learned.firstOrNull { differs(settings.carbRatio[it.slot], it.ratio) }?.let { return Nudge(it.slot, it.ratio) }
        val fit = report.fit ?: return null
        fit.ratios.entries.firstOrNull { differs(settings.carbRatio[it.key], it.value) }?.let { return Nudge(it.key, it.value) }
        return if (differs(settings.correctionFactor, fit.factor)) Nudge(null, fit.factor) else null
    }

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
