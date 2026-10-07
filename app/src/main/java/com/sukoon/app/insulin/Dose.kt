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
    /** The pen's smallest step. */
    val step: Double = 0.5,
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
