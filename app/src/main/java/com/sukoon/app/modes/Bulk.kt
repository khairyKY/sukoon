package com.sukoon.app.modes

import com.sukoon.app.insulin.Profile
import com.sukoon.app.insulin.Sex
import kotlin.math.roundToInt

/**
 * Bulking, from Kai's research (Vault: Fitness And Health/research-2026-07-27-hypertrophy-t1d.md):
 * a small surplus (trained lifters: +100–300 kcal a day; more only adds fat) and 1.6–2.2 g/kg of
 * protein a day, spread over about 4 meals of 0.4–0.5 g/kg.
 */
data class BulkSettings(
    val on: Boolean = false,
    val proteinPerKg: Double = 2.0,
    val surplus: Int = 250,
    /** Your own maintenance, kcal; null = estimated from your profile. */
    val maintenance: Int? = null,
)

data class BulkTargets(
    /** What to eat a day: maintenance + surplus; null without a maintenance. */
    val kcal: Int?,
    val maintenance: Int?,
    /** The maintenance is Sukoon's estimate, not yours. */
    val estimated: Boolean,
    val proteinGrams: Int?,
    val proteinPerMeal: IntRange?,
)

object Bulk {
    val PROTEIN_PER_KG = listOf(1.6, 1.8, 2.0, 2.2)
    val SURPLUS = listOf(200, 250, 300)

    /** Lifting 3–5 days a week ("moderately active"). */
    private const val ACTIVITY = 1.55

    /** Mifflin–St Jeor (1990) resting energy × [ACTIVITY], to the nearest 10 kcal; null without weight, height, age and sex. */
    fun maintenance(p: Profile): Int? {
        val w = p.weightKg ?: return null
        val h = p.heightCm ?: return null
        val a = p.ageYears ?: return null
        val sex = p.sex ?: return null
        val rest = 10 * w + 6.25 * h - 5 * a + if (sex == Sex.MALE) 5 else -161
        return (rest * ACTIVITY / 10).roundToInt() * 10
    }

    fun targets(p: Profile, s: BulkSettings): BulkTargets {
        val estimate = maintenance(p)
        val m = s.maintenance ?: estimate
        val w = p.weightKg
        return BulkTargets(
            kcal = m?.plus(s.surplus),
            maintenance = m,
            estimated = s.maintenance == null && estimate != null,
            proteinGrams = w?.let { (it * s.proteinPerKg).roundToInt() },
            proteinPerMeal = w?.let { (it * 0.4).roundToInt()..(it * 0.5).roundToInt() },
        )
    }
}
