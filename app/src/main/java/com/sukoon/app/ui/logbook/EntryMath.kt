package com.sukoon.app.ui.logbook

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.insulin.Finger
import com.sukoon.app.insulin.InjectionSite
import com.sukoon.app.ai.CarbEstimate
import com.sukoon.app.ai.VoiceEntry
import com.sukoon.app.food.Food
import com.sukoon.app.food.PlateItem

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
    val photos: List<ByteArray> = emptyList(),
    /** A meal's rapid insulin, taken [preBolusMinutes] before [at]. */
    val insulin: Double? = null,
    val preBolusMinutes: Int = 0,
    val nutrients: Nutrients? = null,
    /** Where the insulin went: the dose itself, or a meal's rapid insulin. */
    val site: InjectionSite? = null,
    /** Which fingertip a finger-prick used. */
    val finger: Finger? = null,
    /** Insulin for an imported meal: that meal's id, so the dose stays with it whatever the times say. */
    val mealId: Long? = null,
    /** Carbs that treated a low. */
    val treatment: Boolean = false,
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
    val mealIds = meals.map { it.id }.toSet()
    val mealOf = newestFirst.filter { it.logType == LogEventType.INSULIN }.mapNotNull { dose ->
        if (dose.mealId != null && dose.mealId in mealIds) return@mapNotNull dose to dose.mealId
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

/** A meal you've logged by name at least twice in 60 days: its usual carbs, and the last time's dose. */
internal data class UsualMeal(val name: String, val carbs: Double, val times: Int, val last: EventEntity, val lastDose: Double?)

/** Your usual meals, most eaten first (low treatments and app-imported day totals left out). */
internal fun usualMeals(newestFirst: List<EventEntity>, now: Instant): List<UsualMeal> {
    val since = now.minus(java.time.Duration.ofDays(60)).toEpochMilli()
    val meals = newestFirst.filter { it.logType == LogEventType.CARB && !it.treatment && (it.value ?: 0.0) > 0 && !it.note.isNullOrBlank() && it.timestampMillis > since }
    return meals.groupBy { it.note!!.trim().lowercase() }.filterValues { it.size >= 2 }.map { (_, list) ->
        val last = list.maxBy { it.timestampMillis }
        val carbs = list.map { it.value!! }.sorted()
        val dose = newestFirst.filter { it.logType == LogEventType.INSULIN && kotlin.math.abs(it.timestampMillis - last.timestampMillis) <= 30 * 60_000L }
            .minByOrNull { kotlin.math.abs(it.timestampMillis - last.timestampMillis) }?.value
        UsualMeal(last.note!!.trim(), carbs[carbs.size / 2], list.size, last, dose)
    }.sortedByDescending { it.times }.take(6)
}

/** Today's carbs and insulin (rapid and long-acting), for the Logbook's header. */
internal fun todayTotals(events: List<EventEntity>, now: Instant, zone: ZoneId): Pair<Double, Double> {
    val today = events.filter { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() }
    fun sum(vararg types: LogEventType) = today.filter { it.logType in types }.sumOf { it.value ?: 0.0 }
    return sum(LogEventType.CARB) to sum(LogEventType.INSULIN, LogEventType.BASAL)
}

/** Lots of fat or protein: the rise can come 3 to 5 hours later (a rule of thumb, shown for awareness). */
internal fun slowMeal(fat: Double?, protein: Double?): Boolean = InsightEngine.richMeal(fat, protein)

/** Every food's carbs, fibre, sugar, protein, fat and kcal added up, in [Food.per100]'s order. */
internal fun plateSums(plate: List<PlateItem>): List<Double> = plate.fold(List(6) { 0.0 }) { acc, item -> acc.zip(item.sums, Double::plus) }

/** One portion of a food the AI named: these carbs and, when it gave them, protein, fat and kcal. */
internal fun portion(name: String, carbs: Double, protein: Double? = null, fat: Double? = null, kcal: Double? = null) =
    PlateItem(Food(name, per100 = listOf(carbs, 0.0, 0.0, protein ?: 0.0, fat ?: 0.0, kcal ?: 0.0), own = true), 1.0)

/** What the eat box understood: the foods on one plate, a single rapid dose as its insulin, and the rest as said. */
internal data class EatPlan(val plate: List<PlateItem>, val insulin: Double?, val rest: List<VoiceEntry>)

/**
 * Words or speech as one meal: every food (not a treatment for a low) on the plate, and one rapid
 * dose said with them is its insulin. A treatment, long-acting, a walk or a second dose stay as said.
 */
internal fun eatPlan(entries: List<VoiceEntry>, unnamed: String): EatPlan {
    val foods = entries.filter { it.type == LogEventType.CARB && !it.treatment }
    val dose = entries.singleOrNull { it.type == LogEventType.INSULIN }?.takeIf { foods.isNotEmpty() }
    return EatPlan(
        foods.map { portion(it.name ?: unnamed, it.amount ?: 0.0, it.protein, it.fat, it.kcal) },
        dose?.amount,
        entries.filter { it.type != LogEventType.CARB || it.treatment }.filter { it !== dose },
    )
}

/** A photo's estimate as foods: each item it named, else the whole meal as one. */
internal fun photoPlate(r: CarbEstimate): List<PlateItem> {
    val items = r.items.filter { it.first.isNotBlank() }
    val total = items.sumOf { it.second }.toDouble()
    if (items.size < 2 || total <= 0) return listOf(portion(r.title, r.carbsGrams.toDouble(), r.proteinGrams?.toDouble(), r.fatGrams?.toDouble(), r.kcal?.toDouble()))
    // ponytail: the model gives protein, fat and kcal for the whole meal, so each item carries its carbs' share;
    // the totals stay right, one removed item's may not. Per-item nutrients in the prompt if that matters.
    return items.map { (name, carbs) ->
        val k = carbs / total
        portion(name, carbs.toDouble(), r.proteinGrams?.let { it * k }, r.fatGrams?.let { it * k }, r.kcal?.let { it * k })
    }
}

/** The food with [grams] of carbs: more or less of it (its other nutrients alike), or, holding none, one portion with them. */
internal fun withCarbs(item: PlateItem, grams: Double): PlateItem {
    val carbs = item.sums[Food.CARBS]
    if (carbs > 0 && grams > 0) return item.copy(amount = item.amount * grams / carbs)
    return PlateItem(item.food.copy(per100 = item.sums.toMutableList().also { it[Food.CARBS] = grams }, own = true, lastAmount = null), 1.0)
}
