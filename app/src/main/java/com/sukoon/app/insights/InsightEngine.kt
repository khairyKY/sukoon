package com.sukoon.app.insights

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.sukoon.app.domain.metrics.GlucoseMetrics

enum class Level { GOOD, INFO, ATTENTION, URGENT }

enum class MealSlot { BREAKFAST, LUNCH, DINNER, LATE }

/**
 * One observation about the user's own data. Pure numbers — the UI turns them into words (EN/AR)
 * and attaches the citation. Nothing here is an instruction; "attention" means "worth discussing".
 */
sealed interface Insight {
    val level: Level

    /** Too little data for any claim (consensus wants 14 days at ≥70% coverage; we start at 3 days / 50%). */
    data class NotEnoughData(val daysWithData: Int, val coveragePercent: Int) : Insight {
        override val level = Level.INFO
    }

    /** Battelino et al., Diabetes Care 2019 (International Consensus on Time in Range). */
    data class Targets(
        val days: Int,
        val coveragePercent: Int,
        val inRange: Int,
        val below70: Int,
        val below54: Int,
        val above180: Int,
        val above250: Int,
        val meanMgDl: Int,
        val gmiPercent: Double,
    ) : Insight {
        val metInRange get() = inRange >= 70
        val metBelow70 get() = below70 < 4
        val metBelow54 get() = below54 < 1
        val metAbove180 get() = above180 < 25
        val metAbove250 get() = above250 < 5
        override val level = when {
            !metBelow54 || !metBelow70 -> Level.URGENT
            !metInRange || !metAbove180 || !metAbove250 -> Level.ATTENTION
            else -> Level.GOOD
        }
    }

    /** Coefficient of variation; ≤ 36 % = stable (Danne et al., Diabetes Care 2017). */
    data class Variability(val cvPercent: Int) : Insight {
        override val level = if (cvPercent <= 36) Level.GOOD else Level.ATTENTION
    }

    /** Lows (≥ 15 min below 70) starting in the same 3-hour window on several different days. */
    data class RecurringLows(val fromHour: Int, val toHour: Int, val days: Int, val episodes: Int, val totalEpisodes: Int) : Insight {
        val overnight get() = fromHour in 0..3
        override val level = Level.URGENT
    }

    /** Hours where the typical day sits above 180. */
    data class RecurringHighs(val fromHour: Int, val toHour: Int, val percentOfDays: Int) : Insight {
        override val level = Level.ATTENTION
    }

    /** Rise from the 03–06 h low to 07–08 h (before any logged breakfast) of ≥ 20 mg/dL (Monnier et al. 2013). */
    data class DawnRise(val nightsWithRise: Int, val nights: Int, val medianRise: Int) : Insight {
        override val level = Level.ATTENTION
    }

    /** How logged meals in one part of the day turned out. */
    data class MealOutcomes(
        val slot: MealSlot,
        val meals: Int,
        val highAt2h: Int,
        val lowWithin4h: Int,
        val averageRise: Int,
        /** Grams of carbs per unit of rapid insulin actually used, when insulin was logged with the meal. */
        val gramsPerUnit: Double?,
    ) : Insight {
        override val level = when {
            lowWithin4h >= 2 -> Level.URGENT
            highAt2h * 2 >= meals -> Level.ATTENTION
            else -> Level.GOOD
        }
    }

    /** Meals with rapid insulin ≥ 10 min before eating vs at/after eating (Slattery et al., Diabet Med 2018). */
    data class PreBolus(val earlyMeals: Int, val lateMeals: Int, val earlyRise: Int, val lateRise: Int) : Insight {
        override val level = if (earlyRise <= lateRise - 15) Level.GOOD else Level.INFO
    }

    /** Lows preceded (within 4 h) by two rapid doses less than 3 h apart. */
    data class Stacking(val lowsAfterStacking: Int, val totalLows: Int) : Insight {
        override val level = Level.ATTENTION
    }

    /**
     * Education-grade rules of thumb from the user's logged total daily dose (TDD): 500/TDD g per unit,
     * 1800/TDD mg/dL per unit (Walsh & Roberts, "Pumping Insulin"; Davidson et al., Endocr Pract 2008
     * found ≈1700/TDD). Shown for discussion only. Incomplete logs make TDD too low, which pushes both
     * numbers toward less insulin — the safer direction.
     */
    data class Formulas(val completeDays: Int, val totalDailyDose: Double, val gramsPerUnit500: Double, val mgDlPerUnit1800: Double) : Insight {
        override val level = Level.INFO
    }

    /** The last 7 days against the 7 before (Battelino et al. 2019 targets). */
    data class WeekOverWeek(val inRange: Int, val inRangeBefore: Int, val mean: Int, val meanBefore: Int, val lows: Int, val lowsBefore: Int) : Insight {
        override val level = when {
            inRange - inRangeBefore <= -5 || lows > lowsBefore + 1 -> Level.ATTENTION
            inRange - inRangeBefore >= 3 && lows <= lowsBefore -> Level.GOOD
            else -> Level.INFO
        }
    }

    data class SlotRise(val slot: MealSlot, val per10g: Int, val meals: Int)

    /**
     * How far glucose rose per 10 g of carbs at each meal, with the insulin taken, and when meals
     * usually peak. Insulin action varies through the day (Hinshaw et al., Diabetes 2013).
     */
    data class CarbResponse(val slots: List<SlotRise>, val peakMinutes: Int) : Insight {
        override val level = Level.INFO
    }

    /** Meals with plenty of fat or protein: later peaks, still up hours later (Bell et al., Diabetes Care 2015). */
    data class RichMeals(val rich: Int, val lean: Int, val richPeak: Int, val leanPeak: Int, val richAt4h: Int, val leanAt4h: Int) : Insight {
        override val level = if (richAt4h - leanAt4h >= 30) Level.ATTENTION else Level.INFO
    }

    /** Lows followed within 2 h by a high: often more than the 15 g a low needs (ADA Standards of Care). */
    data class Rebounds(val rebounds: Int, val lows: Int) : Insight {
        override val level = Level.ATTENTION
    }

    /** Lows in the 24 h after a logged workout (Riddell et al., Lancet Diabetes Endocrinol 2017). */
    data class ActivityLows(val followed: Int, val workouts: Int, val overnight: Int) : Insight {
        override val level = Level.ATTENTION
    }

    /** Midnight to 6 am: nights spent in range, and nights with a low. */
    data class Nights(val inRange: Int, val nights: Int, val withLows: Int) : Insight {
        override val level = when {
            withLows >= 2 -> Level.ATTENTION
            withLows == 0 && inRange * 10 >= nights * 7 -> Level.GOOD
            else -> Level.INFO
        }
    }

    /** Time in range on the higher-carb half of logged days against the lower (Evert et al., Diabetes Care 2019). */
    data class CarbDays(val higherTir: Int, val lowerTir: Int, val splitGrams: Int, val days: Int) : Insight {
        override val level = if (lowerTir - higherTir >= 10) Level.ATTENTION else Level.INFO
    }

    /** Finger-pricks vs the sensor at that moment, judged by the 20/20 band (see [MeterCheck]). */
    data class MeterAgreement(val checks: Int, val agreeing: Int, val meanDiffPercent: Int) : Insight {
        override val level = if (agreeing * 100 >= checks * 80) Level.GOOD else Level.ATTENTION
    }
}

/**
 * Time-weighted share of a stretch of readings in each consensus band (Battelino et al. 2019:
 * < 54, 54–69, 70–180, 181–250, > 250), as percents summing to 100, plus the mean and GMI.
 */
data class RangeSummary(
    val veryLow: Double,
    val low: Double,
    val inRange: Double,
    val high: Double,
    val veryHigh: Double,
    val meanMgDl: Int,
    val gmiPercent: Double,
)

/**
 * Turns the last 14 days of readings + logbook into [Insight]s. Pure and clock/zone-injected so every
 * rule is unit-tested. Percentages are time-weighted (each reading counts for the time until the next,
 * capped at 15 min), so mixing 1-minute live data with 15-minute backfill doesn't skew them.
 */
object InsightEngine {

    const val WINDOW_DAYS = 14L
    private val MAX_WEIGHT = Duration.ofMinutes(15)
    private val EPISODE_MIN = Duration.ofMinutes(15)
    private val GAP_BREAKS_RUN = Duration.ofMinutes(20)

    fun analyze(allReadings: List<GlucoseReading>, allEvents: List<EventEntity>, now: Instant, zone: ZoneId): List<Insight> {
        val start = now.minus(Duration.ofDays(WINDOW_DAYS))
        val readings = allReadings.filter { it.timestamp > start && it.timestamp <= now }.sortedBy { it.timestamp }
        val events = allEvents.filter { Instant.ofEpochMilli(it.timestampMillis).let { t -> t > start && t <= now } }.sortedBy { it.timestampMillis }
        val days = readings.map { it.timestamp.atZone(zone).toLocalDate() }.distinct().size
        val coverage = coveragePercent(readings, now)
        if (days < 3 || coverage < 50) return listOf(Insight.NotEnoughData(days, coverage))

        val out = mutableListOf<Insight>()
        val lows = lowEpisodes(readings)
        out += targets(readings, days, coverage, now)
        out += Insight.Variability(cvPercent(readings, now))
        recurringLows(lows, zone)?.let(out::add)
        recurringHighs(readings, zone)?.let(out::add)
        dawnRise(readings, events, zone)?.let(out::add)
        val meals = mealResults(readings, events, zone)
        MealSlot.entries.mapNotNullTo(out) { slot -> mealOutcomes(slot, meals.filter { it.slot == slot }) }
        preBolus(meals)?.let(out::add)
        stacking(lows, events)?.let(out::add)
        formulas(events, zone)?.let(out::add)
        meterAgreement(readings, events)?.let(out::add)
        weekOverWeek(readings, now)?.let(out::add)
        carbResponse(meals)?.let(out::add)
        richMeals(meals)?.let(out::add)
        rebounds(lows, readings)?.let(out::add)
        activityLows(events, lows, zone)?.let(out::add)
        nights(readings, zone)?.let(out::add)
        carbDays(readings, events, now, zone)?.let(out::add)
        return out.sortedByDescending { it.level.ordinal } // urgent first, good last
    }

    private fun meterAgreement(readings: List<GlucoseReading>, events: List<EventEntity>): Insight? {
        val checks = events.filter { it.logType == LogEventType.FINGERSTICK }
            .mapNotNull { e -> e.value?.let { MeterCheck.of(it.roundToInt(), e.timestampMillis, readings) } }
        if (checks.size < 3) return null
        return Insight.MeterAgreement(checks.size, checks.count { it.agrees }, checks.map { it.percentDiff }.average().roundToInt())
    }

    // --- time weighting ------------------------------------------------------------------------

    private fun weights(r: List<GlucoseReading>, now: Instant): List<Double> = r.indices.map { i ->
        val next = if (i + 1 < r.size) r[i + 1].timestamp else now
        minOf(Duration.between(r[i].timestamp, next), MAX_WEIGHT).seconds.coerceAtLeast(60).toDouble()
    }

    /** Share of 15-minute slots since the first reading that hold at least one reading. */
    internal fun coveragePercent(r: List<GlucoseReading>, now: Instant): Int {
        val first = r.firstOrNull()?.timestamp ?: return 0
        val slots = (Duration.between(first, now).toMinutes() / 15 + 1).coerceAtLeast(1)
        val filled = r.map { Duration.between(first, it.timestamp).toMinutes() / 15 }.distinct().size
        return (filled * 100 / slots).toInt().coerceAtMost(100)
    }

    /** [r] oldest first; null when empty. The graph's stats use this too, so they always match the Insights card. */
    fun summary(r: List<GlucoseReading>, now: Instant): RangeSummary? {
        if (r.isEmpty()) return null
        val w = weights(r, now)
        val total = w.sum()
        fun pct(predicate: (Int) -> Boolean) = r.indices.filter { predicate(r[it].glucoseMgDl) }.sumOf { w[it] } * 100 / total
        val mean = r.indices.sumOf { r[it].glucoseMgDl * w[it] } / total
        return RangeSummary(
            veryLow = pct { it < 54 },
            low = pct { it in 54..69 },
            inRange = pct { it in 70..180 },
            high = pct { it in 181..250 },
            veryHigh = pct { it > 250 },
            meanMgDl = mean.roundToInt(),
            gmiPercent = (GlucoseMetrics.gmiPercent(mean) * 10).roundToInt() / 10.0,
        )
    }

    private fun targets(r: List<GlucoseReading>, days: Int, coverage: Int, now: Instant): Insight.Targets {
        val s = requireNotNull(summary(r, now))
        return Insight.Targets(
            days = days,
            coveragePercent = coverage,
            inRange = s.inRange.roundToInt(),
            below70 = (s.veryLow + s.low).roundToInt(),
            below54 = s.veryLow.roundToInt(),
            above180 = (s.high + s.veryHigh).roundToInt(),
            above250 = s.veryHigh.roundToInt(),
            meanMgDl = s.meanMgDl,
            gmiPercent = s.gmiPercent,
        )
    }

    internal fun cvPercent(r: List<GlucoseReading>, now: Instant): Int {
        val w = weights(r, now)
        val total = w.sum()
        val mean = r.indices.sumOf { r[it].glucoseMgDl * w[it] } / total
        val variance = r.indices.sumOf { (r[it].glucoseMgDl - mean).let { d -> d * d } * w[it] } / total
        return (sqrt(variance) / mean * 100).roundToInt()
    }

    // --- lows ----------------------------------------------------------------------------------

    data class Episode(val start: Instant, val end: Instant, val nadir: Int)

    /** CGM hypoglycaemia event: ≥ 15 consecutive minutes below 70 (Danne 2017 / Battelino 2019). */
    internal fun lowEpisodes(r: List<GlucoseReading>, threshold: Int = 70): List<Episode> {
        val out = mutableListOf<Episode>()
        var start: Instant? = null
        var last: Instant? = null
        var nadir = Int.MAX_VALUE
        fun close(end: Instant) {
            val s = start ?: return
            if (Duration.between(s, end) >= EPISODE_MIN) out += Episode(s, end, nadir)
            start = null
            nadir = Int.MAX_VALUE
        }
        for (reading in r) {
            val t = reading.timestamp
            if (start != null && last != null && Duration.between(last, t) > GAP_BREAKS_RUN) close(last!!)
            if (reading.glucoseMgDl < threshold) {
                if (start == null) start = t
                nadir = minOf(nadir, reading.glucoseMgDl)
            } else {
                close(t)
            }
            last = t
        }
        last?.let(::close)
        return out
    }

    private fun recurringLows(lows: List<Episode>, zone: ZoneId): Insight.RecurringLows? {
        if (lows.size < 3) return null
        val starts = lows.map { it.start.atZone(zone) }
        val best = (0..23).map { from ->
            val inWindow = starts.filter { (it.hour - from + 24) % 24 < 3 }
            Triple(from, inWindow.map { it.toLocalDate() }.distinct().size, inWindow.size)
        }.maxWith(compareBy({ it.second }, { it.third }))
        if (best.second < 3) return null
        // Report the hours the lows actually span inside that window, not the whole 3-hour search window.
        val offsets = starts.map { (it.hour - best.first + 24) % 24 }.filter { it < 3 }
        val from = (best.first + offsets.min()) % 24
        val to = (best.first + offsets.max() + 1) % 24
        return Insight.RecurringLows(from, to, best.second, best.third, lows.size)
    }

    // --- highs ---------------------------------------------------------------------------------

    private fun recurringHighs(r: List<GlucoseReading>, zone: ZoneId): Insight.RecurringHighs? {
        val byHour = r.groupBy { it.timestamp.atZone(zone).hour }
        val share = (0..23).map { hour ->
            val perDay = byHour[hour].orEmpty().groupBy { it.timestamp.atZone(zone).toLocalDate() }.values.map { median(it.map(GlucoseReading::glucoseMgDl)) }
            if (perDay.size < 3) 0.0 else perDay.count { it > 180 }.toDouble() / perDay.size
        }
        // Longest run of ≥ 2 consecutive hours (wrapping midnight) that were high on at least half the days.
        var bestStart = -1
        var bestLength = 0
        for (from in 0..23) {
            var length = 0
            while (length < 24 && share[(from + length) % 24] >= 0.5) length++
            if (length > bestLength) {
                bestLength = length
                bestStart = from
            }
        }
        if (bestLength < 2) return null // (24 = high around the clock; from == to then)
        val hours = (0 until bestLength).map { share[(bestStart + it) % 24] }
        return Insight.RecurringHighs(bestStart, (bestStart + bestLength) % 24, (hours.average() * 100).roundToInt())
    }

    // --- dawn ----------------------------------------------------------------------------------

    private fun dawnRise(r: List<GlucoseReading>, events: List<EventEntity>, zone: ZoneId): Insight.DawnRise? {
        val rises = r.groupBy { it.timestamp.atZone(zone).toLocalDate() }.mapNotNull { (date, day) ->
            val night = day.filter { it.timestamp.atZone(zone).hour in 3..5 }
            val morning = day.filter { it.timestamp.atZone(zone).hour == 7 }
            val nadir = night.minByOrNull { it.glucoseMgDl } ?: return@mapNotNull null
            if (morning.isEmpty()) return@mapNotNull null
            val ateBefore = events.any { e ->
                e.logType == LogEventType.CARB && Instant.ofEpochMilli(e.timestampMillis).let { it > nadir.timestamp && it.atZone(zone).toLocalDate() == date && it.atZone(zone).hour < 8 }
            }
            if (ateBefore) null else morning.map { it.glucoseMgDl }.average() - nadir.glucoseMgDl
        }
        if (rises.size < 3) return null
        val withRise = rises.count { it >= 20 }
        return if (withRise * 2 >= rises.size) Insight.DawnRise(withRise, rises.size, median(rises.map { it.roundToInt() })) else null
    }

    // --- meals ---------------------------------------------------------------------------------

    internal data class MealResult(
        val slot: MealSlot,
        val rise: Int,
        val at2h: Int?,
        val lowWithin4h: Boolean,
        val insulin: Double,
        val preBolusMinutes: Long?,
        val carbs: Double,
        /** Minutes from eating to the highest reading within 4 h. */
        val peakMinutes: Long = 0,
        /** Glucose 4 h after eating minus at the meal (null without a reading then). */
        val change4h: Int? = null,
        val fat: Double? = null,
        val protein: Double? = null,
    )

    internal fun slotFor(hour: Int) = when (hour) {
        in 4..10 -> MealSlot.BREAKFAST
        in 11..15 -> MealSlot.LUNCH
        in 16..21 -> MealSlot.DINNER
        else -> MealSlot.LATE
    }

    internal fun mealResults(r: List<GlucoseReading>, events: List<EventEntity>, zone: ZoneId): List<MealResult> =
        events.filter { it.logType == LogEventType.CARB && (it.value ?: 0.0) > 0 }.mapNotNull { meal ->
            val t = Instant.ofEpochMilli(meal.timestampMillis)
            fun nearest(at: Instant, tolerance: Duration) = r.filter { abs(Duration.between(it.timestamp, at).seconds) <= tolerance.seconds }
                .minByOrNull { abs(Duration.between(it.timestamp, at).seconds) }
            val pre = nearest(t, Duration.ofMinutes(15)) ?: return@mapNotNull null
            val after3h = r.filter { it.timestamp > t && it.timestamp <= t.plus(Duration.ofHours(3)) }
            if (after3h.size < 3) return@mapNotNull null
            val after4h = r.filter { it.timestamp > t && it.timestamp <= t.plus(Duration.ofHours(4)) }
            val doses = events.filter { e ->
                e.logType == LogEventType.INSULIN && Instant.ofEpochMilli(e.timestampMillis).let { it >= t.minus(Duration.ofMinutes(60)) && it <= t.plus(Duration.ofMinutes(30)) }
            }
            MealResult(
                slot = slotFor(t.atZone(zone).hour),
                rise = after3h.maxOf { it.glucoseMgDl } - pre.glucoseMgDl,
                at2h = nearest(t.plus(Duration.ofHours(2)), Duration.ofMinutes(20))?.glucoseMgDl,
                lowWithin4h = after4h.any { it.glucoseMgDl < 70 },
                insulin = doses.sumOf { it.value ?: 0.0 },
                preBolusMinutes = doses.minOfOrNull { it.timestampMillis }?.let { (meal.timestampMillis - it) / 60_000 },
                carbs = meal.value ?: 0.0,
                peakMinutes = Duration.between(t, after4h.maxBy { it.glucoseMgDl }.timestamp).toMinutes(),
                change4h = nearest(t.plus(Duration.ofHours(4)), Duration.ofMinutes(20))?.glucoseMgDl?.minus(pre.glucoseMgDl),
                fat = meal.fat,
                protein = meal.protein,
            )
        }

    private fun mealOutcomes(slot: MealSlot, meals: List<MealResult>): Insight.MealOutcomes? {
        if (meals.size < 3) return null
        val withInsulin = meals.filter { it.insulin > 0 }
        return Insight.MealOutcomes(
            slot = slot,
            meals = meals.size,
            highAt2h = meals.count { (it.at2h ?: 0) > 180 },
            lowWithin4h = meals.count { it.lowWithin4h },
            averageRise = meals.map { it.rise }.average().roundToInt(),
            gramsPerUnit = if (withInsulin.isEmpty()) null else (withInsulin.sumOf { it.carbs } / withInsulin.sumOf { it.insulin }).let { (it * 10).roundToInt() / 10.0 },
        )
    }

    private fun preBolus(meals: List<MealResult>): Insight.PreBolus? {
        val early = meals.filter { (it.preBolusMinutes ?: -1) >= 10 }
        val late = meals.filter { it.preBolusMinutes != null && it.preBolusMinutes <= 2 }
        if (early.size < 3 || late.size < 3) return null
        return Insight.PreBolus(early.size, late.size, early.map { it.rise }.average().roundToInt(), late.map { it.rise }.average().roundToInt())
    }

    // --- stacking + formulas -------------------------------------------------------------------

    private fun stacking(lows: List<Episode>, events: List<EventEntity>): Insight.Stacking? {
        val rapid = events.filter { it.logType == LogEventType.INSULIN }.map { Instant.ofEpochMilli(it.timestampMillis) }
        val stacked = lows.count { low ->
            val before = rapid.filter { it < low.start && it >= low.start.minus(Duration.ofHours(4)) }.sorted()
            before.zipWithNext().any { (a, b) -> Duration.between(a, b) < Duration.ofHours(3) }
        }
        return if (stacked >= 2) Insight.Stacking(stacked, lows.size) else null
    }

    private fun formulas(events: List<EventEntity>, zone: ZoneId): Insight.Formulas? {
        val byDay = events.groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
        val totals = byDay.values.mapNotNull { day ->
            val basal = day.filter { it.logType == LogEventType.BASAL }.sumOf { it.value ?: 0.0 }
            val rapid = day.filter { it.logType == LogEventType.INSULIN }.sumOf { it.value ?: 0.0 }
            if (basal > 0 && rapid > 0) basal + rapid else null // only days with both logged count as complete
        }
        if (totals.size < 3) return null
        val tdd = totals.average()
        fun round1(x: Double) = (x * 10).roundToInt() / 10.0
        return Insight.Formulas(totals.size, round1(tdd), round1(500 / tdd), round1(1800 / tdd))
    }

    private fun median(values: List<Int>): Int = values.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }

    // --- comparisons, meals in detail, lows after things, nights ----------------------------------

    /** Lots of fat or protein: the rise can come 3 to 5 hours later (a rule of thumb, Bell et al. 2015). */
    fun richMeal(fat: Double?, protein: Double?): Boolean = (fat ?: 0.0) >= 20 || (protein ?: 0.0) >= 25

    private fun weekOverWeek(r: List<GlucoseReading>, now: Instant): Insight.WeekOverWeek? {
        val split = now.minus(Duration.ofDays(7))
        val (recent, before) = r.partition { it.timestamp > split }
        if (r.isEmpty() || coveragePercent(recent, now) < 50 || before.isEmpty() || Duration.between(before.first().timestamp, split).toDays() < 5) return null
        val a = summary(recent, now) ?: return null
        val b = summary(before, split) ?: return null
        return Insight.WeekOverWeek(a.inRange.roundToInt(), b.inRange.roundToInt(), a.meanMgDl, b.meanMgDl, lowEpisodes(recent).size, lowEpisodes(before).size)
    }

    private fun carbResponse(meals: List<MealResult>): Insight.CarbResponse? {
        val counted = meals.filter { it.carbs >= 10 }
        val slots = counted.groupBy { it.slot }.filterValues { it.size >= 3 }
            .map { (slot, ms) -> Insight.SlotRise(slot, median(ms.map { (it.rise * 10 / it.carbs).roundToInt() }), ms.size) }
            .sortedBy { it.slot.ordinal }
        if (slots.size < 2) return null
        return Insight.CarbResponse(slots, median(counted.map { it.peakMinutes.toInt() }))
    }

    private fun richMeals(meals: List<MealResult>): Insight.RichMeals? {
        val (rich, lean) = meals.filter { it.fat != null || it.protein != null }.partition { richMeal(it.fat, it.protein) }
        val richLater = rich.mapNotNull { it.change4h }
        val leanLater = lean.mapNotNull { it.change4h }
        if (rich.size < 3 || lean.size < 3 || richLater.size < 2 || leanLater.size < 2) return null
        return Insight.RichMeals(rich.size, lean.size, median(rich.map { it.peakMinutes.toInt() }), median(lean.map { it.peakMinutes.toInt() }), median(richLater), median(leanLater))
    }

    private fun rebounds(lows: List<Episode>, r: List<GlucoseReading>): Insight.Rebounds? {
        if (lows.size < 3) return null
        val n = lows.count { low -> r.any { it.timestamp > low.end && it.timestamp <= low.end.plus(Duration.ofHours(2)) && it.glucoseMgDl > 180 } }
        return if (n >= 2 && n * 10 >= lows.size * 3) Insight.Rebounds(n, lows.size) else null
    }

    private fun activityLows(events: List<EventEntity>, lows: List<Episode>, zone: ZoneId): Insight.ActivityLows? {
        val workouts = events.filter { it.logType == LogEventType.ACTIVITY && (it.value ?: 0.0) >= 15 }
        if (workouts.size < 2) return null
        val followed = workouts.mapNotNull { w ->
            val t = Instant.ofEpochMilli(w.timestampMillis)
            lows.firstOrNull { it.start > t && it.start <= t.plus(Duration.ofHours(24)) }
        }
        if (followed.size < 2) return null
        return Insight.ActivityLows(followed.size, workouts.size, followed.count { it.start.atZone(zone).hour < 6 })
    }

    private fun nights(r: List<GlucoseReading>, zone: ZoneId): Insight.Nights? {
        val nights = r.filter { it.timestamp.atZone(zone).hour < 6 }
            .groupBy { it.timestamp.atZone(zone).toLocalDate() }.values
            .filter { it.size >= 2 && Duration.between(it.first().timestamp, it.last().timestamp) >= Duration.ofHours(4) }
        if (nights.size < 5) return null
        return Insight.Nights(nights.count { n -> n.all { it.glucoseMgDl in 70..180 } }, nights.size, nights.count { lowEpisodes(it).isNotEmpty() })
    }

    private fun carbDays(r: List<GlucoseReading>, events: List<EventEntity>, now: Instant, zone: ZoneId): Insight.CarbDays? {
        val today = now.atZone(zone).toLocalDate()
        val carbs = events.filter { it.logType == LogEventType.CARB }
            .groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
            .mapValues { (_, meals) -> meals.sumOf { it.value ?: 0.0 } }
        val byDay = r.groupBy { it.timestamp.atZone(zone).toLocalDate() }
        val days = carbs.keys.filter { d -> d != today && byDay[d]?.let { Duration.between(it.first().timestamp, it.last().timestamp) >= Duration.ofHours(12) } == true }
            .sortedBy { carbs.getValue(it) }
        if (days.size < 6) return null
        val half = days.size / 2
        fun tir(ds: List<java.time.LocalDate>) = ds.map { d -> byDay.getValue(d).let { summary(it, it.last().timestamp)!!.inRange } }.average().roundToInt()
        return Insight.CarbDays(tir(days.takeLast(half)), tir(days.take(half)), carbs.getValue(days[half]).roundToInt(), days.size)
    }
}
