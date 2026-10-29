package com.sukoon.app.reminders

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.insights.MealSlot
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Egypt's week: Sun–Thu (dorm days) and Fri–Sat; meal times are kept apart for each. */
enum class DayKind {
    WEEK, WEEKEND;

    companion object {
        fun of(date: LocalDate) = if (date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY) WEEKEND else WEEK
    }
}

/**
 * You → Reminders. Finger-prick checks run only in finger-prick mode (no sensor reading for 30 min,
 * or the sensor ended); the missing-meal question runs either way.
 */
data class CheckSettings(
    val beforeMeals: Boolean = true,
    val afterMeals: Boolean = true,
    val bedtime: Boolean = true,
    val bedtimeMinute: Int = 23 * 60 + 30,
    val afterLow: Boolean = true,
    /** Finger-prick reminders a day at most (checks after a low don't count). */
    val cap: Int = 12,
    val mealMissing: Boolean = true,
    /** Times you set, "WEEK:LUNCH" → minute of day; they win over learned ones. */
    val fixed: Map<String, Int> = emptyMap(),
) {
    companion object {
        val CAP_RANGE = 2..20
        fun key(kind: DayKind, slot: MealSlot) = "${kind.name}:${slot.name}"
    }
}

/** The three meals reminders follow (a late snack isn't one). */
val REMINDER_SLOTS = listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER)

/** A usual meal for a slot: what "The usual" logs. */
data class UsualMeal(val note: String, val carbs: Double)

object MealTimes {
    private val LEARN_FROM: Duration = Duration.ofDays(21)
    private const val LEARN_AT_LEAST = 3

    /** A real meal: 10 g or more, not a low treatment, not a day's summary from another app. */
    internal fun isMeal(e: EventEntity) = e.logType == LogEventType.CARB && !e.treatment && !e.summary && (e.value ?: 0.0) >= 10

    /** A meal whose time is when it was eaten (an imported one is timed when it was logged, unless you set it). */
    private fun timed(e: EventEntity) = isMeal(e) && (e.source == null || e.timeSet)

    /** When you usually eat each meal, per day kind: the median of the last 3 weeks, from 3 meals on. */
    fun learned(events: List<EventEntity>, now: Instant, zone: ZoneId): Map<String, Int> = events
        .filter { timed(it) && it.timestampMillis >= now.minus(LEARN_FROM).toEpochMilli() && it.timestampMillis <= now.toEpochMilli() }
        .map { Instant.ofEpochMilli(it.timestampMillis).atZone(zone) }
        .groupBy { CheckSettings.key(DayKind.of(it.toLocalDate()), InsightEngine.slotFor(it.hour)) }
        .filterKeys { k -> REMINDER_SLOTS.any { k.endsWith(":${it.name}") } }
        .filterValues { it.size >= LEARN_AT_LEAST }
        .mapValues { (_, times) -> times.map { it.hour * 60 + it.minute }.sorted().let { it[it.size / 2] } }

    /** Each meal's time on a day of [kind]: yours if you set one, else the learned one; a meal with neither has no reminders. */
    fun times(kind: DayKind, learned: Map<String, Int>, fixed: Map<String, Int>): Map<MealSlot, Int> =
        REMINDER_SLOTS.mapNotNull { slot -> val k = CheckSettings.key(kind, slot); (fixed[k] ?: learned[k])?.let { slot to it } }.toMap()

    /** What you eat most at [slot] (twice or more in 60 days, named): its name and usual carbs. */
    fun usual(events: List<EventEntity>, slot: MealSlot, now: Instant, zone: ZoneId): UsualMeal? = events
        .filter { isMeal(it) && !it.note.isNullOrBlank() && it.timestampMillis >= now.minus(Duration.ofDays(60)).toEpochMilli() }
        .filter { InsightEngine.slotFor(Instant.ofEpochMilli(it.timestampMillis).atZone(zone).hour) == slot }
        .groupBy { it.note!!.trim().lowercase() }
        .filterValues { it.size >= 2 }
        .maxByOrNull { (_, list) -> list.size * 1_000_000_000_000L + list.maxOf { it.timestampMillis } / 1000 }
        ?.value?.let { list -> UsualMeal(list.maxBy { it.timestampMillis }.note!!.trim(), list.map { it.value!! }.sorted()[list.size / 2]) }
}

enum class CheckKind {
    BEFORE_MEAL, AFTER_MEAL, BEDTIME, AFTER_LOW, MEAL_MISSING, DAY_GAPS;

    /** Counted against the daily cap. */
    val capped: Boolean get() = this == BEFORE_MEAL || this == AFTER_MEAL || this == BEDTIME
}

/** One reminder: [key] names it once (a day's meal, a meal's 2 h, a low), so it's never sent twice. */
data class Due(
    val kind: CheckKind,
    val at: Instant,
    val key: String,
    val slot: MealSlot? = null,
    val mgDl: Int? = null,
    val since: Instant? = null,
    /** [CheckKind.DAY_GAPS]: today's meals with nothing logged. */
    val gaps: List<MealSlot> = emptyList(),
)

/**
 * When to remind, from the logbook (pure; [Checks] posts them). Finger-prick checks follow the ADA's
 * rhythm without a sensor (before meals, 2 h after, bedtime, after a low until back above 70);
 * none at night after a workout (Kai's call).
 */
object CheckPlan {
    val BEFORE: Duration = Duration.ofMinutes(15)
    val AFTER: Duration = Duration.ofHours(2)
    val LOW_RECHECK: Duration = Duration.ofMinutes(15)
    /** The missing-meal question comes this long after the meal's usual time. */
    val MEAL_WAIT: Duration = Duration.ofMinutes(20)
    /** A meal logged from this long before its usual time counts as that meal. */
    private val MEAL_EARLY: Duration = Duration.ofMinutes(90)
    /** A prick this close to a check's time already answers it. */
    private val PRICK_COVERS: Duration = Duration.ofMinutes(60)
    /** Today's gaps: meals that passed with nothing logged, asked about once in the evening. */
    const val GAPS_MINUTE = 22 * 60
    /** Missed by more than this (phone off, app killed): dropped, not sent late. */
    val GRACE: Duration = Duration.ofMinutes(45)
    const val LOW = 70
    private const val LOW_ASKS = 4

    /**
     * Every reminder still to come or due now, soonest first. [sent]: keys already sent, and when
     * (today's capped ones count toward [CheckSettings.cap]); [snoozed]: "In 15 min" until when.
     */
    fun plan(
        now: Instant,
        zone: ZoneId,
        events: List<EventEntity>,
        fingerPrickMode: Boolean,
        settings: CheckSettings,
        learned: Map<String, Int>,
        sent: Map<String, Instant>,
        snoozed: Map<String, Instant> = emptyMap(),
    ): List<Due> {
        val meals = events.filter(MealTimes::isMeal).map { Instant.ofEpochMilli(it.timestampMillis) to it }
        val pricks = events.filter { it.logType == LogEventType.FINGERSTICK && it.value != null }
            .map { Instant.ofEpochMilli(it.timestampMillis) to it.value!!.roundToInt() }.sortedBy { it.first }
        fun mealBetween(from: Instant, to: Instant) = meals.any { (at, _) -> !at.isBefore(from) && !at.isAfter(to) }
        fun prickNear(at: Instant) = pricks.any { (p, _) -> !p.isAfter(now) && Duration.between(p, at).abs() <= PRICK_COVERS }

        val due = mutableListOf<Due>()
        val today = now.atZone(zone).toLocalDate()
        // The evening's look back: meals whose time passed with nothing logged, unless you said you didn't eat.
        if (settings.mealMissing) {
            val evening = today.atStartOfDay(zone).plusMinutes(GAPS_MINUTE.toLong()).toInstant()
            val gaps = MealTimes.times(DayKind.of(today), learned, settings.fixed).filter { (slot, minute) ->
                val mealAt = today.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant()
                mealAt.isBefore(evening) && !mealBetween(mealAt.minus(MEAL_EARLY), evening) && "no:meal:$today:$slot" !in sent
            }.keys.toList()
            if (gaps.isNotEmpty()) due += Due(CheckKind.DAY_GAPS, evening, "gaps:$today", gaps = gaps)
        }
        for (date in listOf(today.minusDays(1), today, today.plusDays(1))) {
            val times = MealTimes.times(DayKind.of(date), learned, settings.fixed)
            for ((slot, minute) in times) {
                val mealAt = date.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant()
                val eaten = mealBetween(mealAt.minus(MEAL_EARLY), maxOf(now, mealAt))
                if (settings.mealMissing && !eaten) due += Due(CheckKind.MEAL_MISSING, mealAt.plus(MEAL_WAIT), "meal:$date:$slot", slot)
                val pre = mealAt.minus(BEFORE)
                if (fingerPrickMode && settings.beforeMeals && !eaten && !prickNear(pre)) due += Due(CheckKind.BEFORE_MEAL, pre, "pre:$date:$slot", slot)
            }
            val bed = date.atStartOfDay(zone).plusMinutes(settings.bedtimeMinute.toLong()).toInstant()
            if (fingerPrickMode && settings.bedtime && !prickNear(bed)) due += Due(CheckKind.BEDTIME, bed, "bed:$date")
        }
        if (fingerPrickMode && settings.afterMeals) meals.forEach { (at, meal) ->
            val check = at.plus(AFTER)
            // A prick from 90 min after the meal on answers its 2-hour check.
            if (pricks.none { (p, _) -> p.isAfter(at.plus(Duration.ofMinutes(90))) }) due += Due(CheckKind.AFTER_MEAL, check, "post:${meal.id}", since = at)
        }
        // After a low, every 15 min (4 times at most) until a prick reads 70 or more.
        pricks.lastOrNull()?.let { (at, mgDl) ->
            val asked = sent.keys.count { it.startsWith("low:${at.toEpochMilli()}:") }
            if (fingerPrickMode && settings.afterLow && mgDl < LOW && asked < LOW_ASKS) {
                due += Due(CheckKind.AFTER_LOW, at.plus(LOW_RECHECK.multipliedBy(asked + 1L)), "low:${at.toEpochMilli()}:$asked", mgDl = mgDl, since = at)
            }
        }

        val capReached = sent.count { (key, at) -> capped(key) && at.atZone(zone).toLocalDate() == today } >= settings.cap.coerceIn(CheckSettings.CAP_RANGE)
        return due
            .filter { it.key !in sent }
            .map { d -> snoozed[d.key]?.let { d.copy(at = maxOf(d.at, it)) } ?: d }
            .filter { Duration.between(it.at, now) <= GRACE }
            .filter { !(capReached && it.kind.capped) }
            .sortedBy { it.at }
    }

    private fun capped(key: String) = key.startsWith("pre:") || key.startsWith("post:") || key.startsWith("bed:")
}

/**
 * "Did you eat?" on a rise nothing explains (sensor on): asked, never logged for you. Meal
 * detectors catch a meal 20–40 min in and raise 0.4–2.8 false alarms a day (Brummer et al.,
 * Front Nutr 2023), so this one is strict and asks twice a day at most.
 */
object RiseCheck {
    /** Up this much over [WINDOW], still going up over its last half, and at least [FROM]. */
    const val RISE = 30
    const val FROM = 120
    val WINDOW: Duration = Duration.ofMinutes(20)
    /** Food or rapid insulin logged this recently explains it; a workout (lifting raises glucose) too. */
    private val EXPLAINED: Duration = Duration.ofMinutes(90)
    private val AFTER_WORKOUT: Duration = Duration.ofHours(2)
    const val PER_DAY = 2
    val APART: Duration = Duration.ofHours(3)

    /** [readings] (time, mg/dL) of the last half hour or so; [asked] when it was asked before. */
    fun unexplained(readings: List<Pair<Instant, Int>>, events: List<EventEntity>, now: Instant, zone: ZoneId, asked: List<Instant>): Boolean {
        val fresh = readings.filter { !it.first.isAfter(now) }.sortedBy { it.first }
        val latest = fresh.lastOrNull()?.takeIf { Duration.between(it.first, now) <= Duration.ofMinutes(10) } ?: return false
        fun at(back: Duration) = fresh.lastOrNull { !it.first.isAfter(latest.first.minus(back)) }
        val start = at(WINDOW)?.takeIf { Duration.between(it.first, latest.first) <= WINDOW.plusMinutes(5) } ?: return false
        val half = at(WINDOW.dividedBy(2)) ?: return false
        if (latest.second < FROM || latest.second - start.second < RISE || latest.second <= half.second) return false
        val explained = events.any { e ->
            val t = Instant.ofEpochMilli(e.timestampMillis)
            !t.isAfter(now) && when (e.logType) {
                LogEventType.CARB, LogEventType.INSULIN -> Duration.between(t, now) <= EXPLAINED
                LogEventType.ACTIVITY -> Duration.between(t.plus(Duration.ofMinutes((e.value ?: 0.0).toLong())), now) <= AFTER_WORKOUT
                else -> false
            }
        }
        if (explained) return false
        val today = now.atZone(zone).toLocalDate()
        if (asked.count { it.atZone(zone).toLocalDate() == today } >= PER_DAY) return false
        return asked.none { Duration.between(it, now) < APART }
    }
}
