package com.sukoon.app.insulin

import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.insights.MealSlot
import kotlin.math.floor

/**
 * Beta dose suggestions (docs/research/dosing-sources.md): carb counting with a ratio per meal.
 * Every number is optional; a part without its number is simply left out.
 */
data class DoseSettings(
    val enabled: Boolean = false,
    /** Grams of carbs one unit covers, per meal. */
    val carbRatio: Map<MealSlot, Double> = emptyMap(),
    /** How far one unit lowers glucose, mg/dL. */
    val correctionFactor: Double? = null,
    val target: Int = 110,
    val maxDose: Double = 10.0,
    /** The pen's smallest step: whole units unless it's a half-unit pen. Not beta: the insulin keypad follows it too. */
    val step: Double = 1.0,
)

sealed interface DoseAdvice {
    /** Under 70, or under 100 and dropping: treat that, no insulin. */
    data class TreatLowFirst(val glucose: Int) : DoseAdvice

    data class Suggestion(
        val units: Double,
        /** carbs / ratio, when both are known. */
        val mealUnits: Double?,
        val carbs: Double?,
        val ratio: Double?,
        /** (glucose − target) / factor, after insulin still working; negative below target. */
        val correctionUnits: Double?,
        val glucose: Int?,
        /** Insulin still working that the correction made room for. */
        val onBoardUsed: Double,
        val capped: Boolean,
        val slot: MealSlot,
        val target: Int,
        val factor: Double?,
    ) : DoseAdvice
}

object Dose {

    /**
     * Home's correction (beta): above your range on a fresh reading, nothing eaten in the last 2 hours
     * (a rise after eating is expected, and that meal's insulin is on it), and under 0.5 u still working
     * (then Home already says to give it time). Null unless it comes to at least one pen step.
     */
    fun correction(
        settings: DoseSettings,
        glucose: Int?,
        trend: TrendDirection?,
        readingAt: java.time.Instant?,
        now: java.time.Instant,
        top: Int,
        onBoard: Double,
        lastCarbsAt: java.time.Instant?,
        slot: MealSlot,
    ): DoseAdvice.Suggestion? {
        if (!settings.enabled || settings.correctionFactor == null || glucose == null || readingAt == null) return null
        if (java.time.Duration.between(readingAt, now) > java.time.Duration.ofMinutes(10) || glucose <= top || onBoard >= 0.5) return null
        if (lastCarbsAt != null && java.time.Duration.between(lastCarbsAt, now) < java.time.Duration.ofHours(2)) return null
        return (advise(settings, slot, null, glucose, trend, onBoard) as? DoseAdvice.Suggestion)?.takeIf { it.units >= settings.step }
    }

    /**
     * The suggestion for [carbs] (null: a correction only) at [glucose]. Insulin still working
     * offsets the correction only, as pump bolus calculators do: earlier meal insulin is busy with
     * earlier food. Rounded down to the pen's step, capped at the maximum. Null with nothing to say.
     */
    fun advise(
        settings: DoseSettings,
        slot: MealSlot,
        carbs: Double?,
        glucose: Int?,
        trend: TrendDirection?,
        onBoard: Double,
    ): DoseAdvice? {
        val falling = trend == TrendDirection.FALLING || trend == TrendDirection.FALLING_FAST
        if (glucose != null && (glucose < 70 || (glucose < 100 && falling))) return DoseAdvice.TreatLowFirst(glucose)
        val ratio = settings.carbRatio[slot]?.takeIf { it > 0 }
        val meal = if (carbs != null && carbs > 0 && ratio != null) carbs / ratio else null
        val factor = settings.correctionFactor?.takeIf { it > 0 }
        val raw = if (glucose != null && factor != null) (glucose - settings.target) / factor else null
        val correction = raw?.let { if (it > 0) maxOf(0.0, it - onBoard) else it }
        if (meal == null && correction == null) return null
        val total = maxOf(0.0, (meal ?: 0.0) + (correction ?: 0.0))
        val rounded = floor(total / settings.step + 1e-9) * settings.step
        return DoseAdvice.Suggestion(
            units = minOf(rounded, settings.maxDose),
            mealUnits = meal,
            carbs = carbs,
            ratio = ratio,
            correctionUnits = correction,
            glucose = glucose,
            onBoardUsed = if (raw != null && raw > 0) minOf(raw, onBoard) else 0.0,
            capped = rounded > settings.maxDose,
            slot = slot,
            target = settings.target,
            factor = factor,
        )
    }
}

enum class Sex { FEMALE, MALE }

/** About you (dose setup). Weight feeds the starting ratios and age stops them under 18; height and sex go to the AI. */
data class Profile(val weightKg: Double? = null, val heightCm: Int? = null, val ageYears: Int? = null, val sex: Sex? = null)

/** Where a suggested starting point came from, best first. */
enum class StartSource { LOGBOOK, WEIGHT, COMMON }

data class StartingRatios(val ratio: Double, val factor: Double, val source: StartSource, val dailyUnits: Double?)

/**
 * "Suggest starting ratios" (docs/research/dosing-sources.md), for every age: from your logged daily
 * total if there are enough complete days (500 and 1800 rules), else from your weight and age, else
 * (adults only) the common start of 1 u per 15 g and 1 u per 50 mg/dL. Daily units per kg at the
 * cautious end of each published range, since a smaller total means less insulin per gram:
 * adults 0.5 (ADA Standards of Care: 0.4–1.0, 0.5 typical); 12–17 1.0 (ISPAD 2022, puberty 1.0–1.2+);
 * under 12 0.7 (ISPAD 2022, prepubertal 0.7–1.0). A child without a weight gets nothing: too varied to guess.
 */
object StartingPoints {
    fun unitsPerKg(age: Int?): Double = when {
        age == null || age >= 18 -> 0.5
        age >= 12 -> 1.0
        else -> 0.7
    }

    fun suggest(fromLogbook: com.sukoon.app.insights.Insight.Formulas?, profile: Profile): StartingRatios? {
        fromLogbook?.let { return StartingRatios(half(it.gramsPerUnit500), fives(it.mgDlPerUnit1800), StartSource.LOGBOOK, it.totalDailyDose) }
        profile.weightKg?.takeIf { it in 10.0..250.0 }?.let { kg ->
            val daily = kg * unitsPerKg(profile.ageYears)
            return StartingRatios(half(500 / daily), fives(1800 / daily), StartSource.WEIGHT, daily)
        }
        if (profile.ageYears != null && profile.ageYears < 18) return null
        return StartingRatios(15.0, 50.0, StartSource.COMMON, null)
    }

    private fun half(x: Double) = Math.round(x * 2) / 2.0
    private fun fives(x: Double) = Math.round(x / 5) * 5.0
}
