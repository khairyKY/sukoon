package com.sukoon.app.insulin

import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A meal edited after its insulin (D:\Coding\_claude-cloud\sukoon\meal-edit-logic.md): how far the
 * dose now is from what was eaten, and what's still coming. All numbers are estimates for a
 * prompt to check, never a dose instruction.
 */
data class MealGap(
    val planned: Double,
    val eaten: Double,
    val dose: Double,
    val doseAt: Instant,
    /** Units more (+) or fewer (−) than the eaten carbs need. */
    val gapUnits: Double,
    /** The part of [gapUnits] still to act now. */
    val stillActing: Double,
    /** Where glucose may bottom out (or top out), mg/dL; null without a correction factor or a reading. */
    val ahead: Int?,
    /** When the extra insulin is spent. */
    val by: Instant,
    /** Grams that would cover the extra (ate less), or units that would cover the extra carbs (ate more). */
    val coverCarbs: Int?,
    val topUpUnits: Double?,
) {
    val ateLess: Boolean get() = gapUnits > 0
}

object MealChange {
    /** Edits more than this long after the dose are just a record fix: the insulin's spent. */
    private val TOO_LATE: Duration = Duration.ofHours(5)
    /** A top-up for extra carbs only this soon after the meal. */
    private val TOP_UP_WITHIN: Duration = Duration.ofHours(2)

    /**
     * [planned] and [eaten] carbs (g), the meal's [dose] (units, taken [doseAt]), the meal slot's
     * [ratio] (g/u; null: words only), the [factor] (mg/dL per u), [glucose] now. Null when it makes
     * no difference (under a pen [step], or too long ago).
     */
    fun assess(
        planned: Double, eaten: Double, dose: Double, doseAt: Instant, mealAt: Instant, now: Instant,
        ratio: Double?, factor: Double?, glucose: Int?, action: InsulinAction, step: Double = 1.0,
        lowSince2h: Boolean = false,
    ): MealGap? {
        if (dose <= 0 || Duration.between(doseAt, now) > TOO_LATE || ratio == null || ratio <= 0) return null
        // The dose's meal part: what covered the planned carbs; anything above that was a correction.
        val mealPart = minOf(dose, planned / ratio)
        val needed = eaten / ratio
        val gap = mealPart - needed
        if (kotlin.math.abs(gap) < step) return null
        val left = InsulinOnBoard.remaining(Duration.between(doseAt, now).toMillis() / 60_000.0, action)
        val acting = gap * left
        val by = doseAt.plus(Duration.ofMinutes(action.durationMinutes.toLong()))
        return if (gap > 0) {
            if (acting < step / 2) return null
            MealGap(
                planned, eaten, dose, doseAt, gap, acting,
                ahead = if (factor != null && glucose != null) (glucose - acting * factor).roundToInt() else null,
                by = by,
                // Rounded up to 5 g, at least 10 and at most 60 (more than that: treat as a low).
                coverCarbs = (ceil(acting * ratio / 5) * 5).roundToInt().coerceIn(10, 60),
                topUpUnits = null,
            )
        } else {
            val short = -gap
            val soon = Duration.between(mealAt, now) <= TOP_UP_WITHIN && !lowSince2h
            MealGap(
                planned, eaten, dose, doseAt, gap, -short * left,
                ahead = if (factor != null && glucose != null) (glucose + short * left * factor).roundToInt() else null,
                by = by,
                coverCarbs = null,
                topUpUnits = if (soon) (kotlin.math.floor(short / step) * step).takeIf { it >= step } else null,
            )
        }
    }
}
