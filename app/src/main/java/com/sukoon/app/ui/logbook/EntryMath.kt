package com.sukoon.app.ui.logbook

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** A meal's nutrients beyond its carbs: MyFitnessPal brings them, and a meal can be given them by hand. */
data class Nutrients(
    val fiber: Double? = null,
    val sugar: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val kcal: Double? = null,
)

/** What the entry editor saves: one entry, or a meal plus the rapid insulin taken for it. */
data class EntryDraft(
    val type: LogEventType,
    /** Grams, units, mg/dL or minutes; null for a note. */
    val amount: Double?,
    val note: String?,
    val at: Instant,
    val photo: ByteArray? = null,
    /** A meal's rapid insulin, taken [preBolusMinutes] before [at]. */
    val insulin: Double? = null,
    val preBolusMinutes: Int = 0,
    val nutrients: Nutrients? = null,
)

/** The amount pad: digits, one decimal place where the unit allows it, delete. */
internal object Keypad {
    const val DELETE = "del"
    const val POINT = "."

    fun press(current: String, key: String, decimals: Boolean, maxWhole: Int): String = when (key) {
        DELETE -> current.dropLast(1)
        POINT -> if (!decimals || '.' in current) current else current.ifEmpty { "0" } + "."
        else -> {
            val next = (if (current == "0") "" else current) + key
            if (next.substringBefore('.').length > maxWhole || next.substringAfter('.', "").length > 1) current else next
        }
    }
}

/** One logbook row: an entry, or a meal with the rapid insulin taken for it. */
internal data class EntryGroup(val main: EventEntity, val insulin: List<EventEntity> = emptyList())

// The meal window Insights uses too: insulin from an hour before a meal to half an hour after it.
private val DOSE_BEFORE_MEAL = Duration.ofMinutes(60)
private val DOSE_AFTER_MEAL = Duration.ofMinutes(30)

/** Groups [newestFirst] for the timeline: each rapid dose sits under the nearest meal whose window holds it. */
internal fun groupEntries(newestFirst: List<EventEntity>): List<EntryGroup> {
    val meals = newestFirst.filter { it.logType == LogEventType.CARB }
    val mealOf = newestFirst.filter { it.logType == LogEventType.INSULIN }.mapNotNull { dose ->
        meals.filter { dose.timestampMillis - it.timestampMillis in -DOSE_BEFORE_MEAL.toMillis()..DOSE_AFTER_MEAL.toMillis() }
            .minByOrNull { abs(it.timestampMillis - dose.timestampMillis) }
            ?.let { dose to it.id }
    }
    val dosesOf = mealOf.groupBy({ it.second }, { it.first })
    val nested = mealOf.map { it.first.id }.toSet()
    return newestFirst.filter { it.id !in nested }.map { EntryGroup(it, dosesOf[it.id].orEmpty().sortedBy { d -> d.timestampMillis }) }
}

/** What a meal did: the reading as it started, the peak within 4 hours, and when it came back under 180. */
internal data class MealResponse(val start: Int, val peak: Int, val peakAt: Instant, val backInRangeAt: Instant?, val stillRising: Boolean)

/** Null until there's a reading within 15 minutes of the meal and three after it. */
internal fun mealResponse(readings: List<GlucoseReading>, mealAt: Instant, now: Instant): MealResponse? {
    val start = readings.filter { abs(Duration.between(it.timestamp, mealAt).toMinutes()) <= 15 }
        .minByOrNull { abs(Duration.between(it.timestamp, mealAt).seconds) } ?: return null
    val end = mealAt.plus(Duration.ofHours(4))
    val after = readings.filter { it.timestamp > mealAt && it.timestamp <= end }
    if (after.size < 3) return null
    val peak = after.maxBy { it.glucoseMgDl }
    return MealResponse(
        start = start.glucoseMgDl,
        peak = peak.glucoseMgDl,
        peakAt = peak.timestamp,
        backInRangeAt = if (peak.glucoseMgDl > 180) after.firstOrNull { it.timestamp > peak.timestamp && it.glucoseMgDl <= 180 }?.timestamp else null,
        stillRising = peak === after.last() && now < end,
    )
}

/** Up to three recent entries worth repeating: Sukoon's own, with an amount, no duplicates, newest first. */
internal fun recentRepeats(newestFirst: List<EventEntity>): List<EventEntity> =
    newestFirst.filter { it.source == null && it.value != null && it.logType != LogEventType.FINGERSTICK && it.logType != LogEventType.NOTE }
        .distinctBy { Triple(it.logType, it.value, it.note) }
        .take(3)

/** Today's carbs and insulin (rapid and long-acting), for the Logbook's header. */
internal fun todayTotals(events: List<EventEntity>, now: Instant, zone: ZoneId): Pair<Double, Double> {
    val today = events.filter { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() }
    fun sum(vararg types: LogEventType) = today.filter { it.logType in types }.sumOf { it.value ?: 0.0 }
    return sum(LogEventType.CARB) to sum(LogEventType.INSULIN, LogEventType.BASAL)
}

/** Lots of fat or protein: the rise can come 3 to 5 hours later (a rule of thumb, shown for awareness). */
internal fun slowMeal(fat: Double?, protein: Double?): Boolean = (fat ?: 0.0) >= 20 || (protein ?: 0.0) >= 25
