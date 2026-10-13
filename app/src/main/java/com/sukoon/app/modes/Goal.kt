package com.sukoon.app.modes

import com.sukoon.app.insulin.Profile
import com.sukoon.app.insulin.Sex
import kotlin.math.roundToInt

/** What you're eating for (You → Fitness & food). */
enum class GoalKind { NONE, MAINTAIN, BULK, CUT }

/**
 * Your goal and its numbers. [proteinPerKg] and [change] (kcal a day above or below maintenance)
 * are null until you pick them: then the goal's own default applies. [maintenance]: yours, or null
 * to estimate it from your profile.
 */
data class GoalSettings(
    val kind: GoalKind = GoalKind.NONE,
    val proteinPerKg: Double? = null,
    val change: Int? = null,
    val maintenance: Int? = null,
)

data class GoalTargets(
    /** Calories a day: maintenance + change; null without a maintenance. */
    val kcal: Int?,
    val maintenance: Int?,
    /** The maintenance is Sukoon's estimate, not yours. */
    val estimated: Boolean,
    val proteinGrams: Int?,
    val proteinPerMeal: IntRange?,
    val proteinPerKg: Double,
    val change: Int,
)

/**
 * The numbers per goal (docs/research/modes-sources.md, "Goal"):
 * - Lean bulk: +100–300 kcal a day and 1.6–2.2 g/kg protein (Kai's research, 27 Jul 2026); gain
 *   about 0.25–0.5% of bodyweight a week (Iraki et al. 2019).
 * - Cut: a deficit of at most 500 kcal a day (Murphy & Koehler 2022), losing 0.5–1% a week (Helms
 *   et al. 2014), protein 2.2–2.6 g/kg (Hector & Phillips 2018; ISSN 2017).
 * - Maintain: protein 1.4–2.0 g/kg (ISSN 2017; Morton et al. 2018).
 * Protein is spread over about 4 meals of 0.4–0.5 g/kg.
 */
object Goals {
    fun proteinChoices(kind: GoalKind): List<Double> = when (kind) {
        GoalKind.BULK -> listOf(1.6, 1.8, 2.0, 2.2)
        GoalKind.CUT -> listOf(2.0, 2.2, 2.4, 2.6)
        else -> listOf(1.4, 1.6, 1.8, 2.0)
    }

    fun defaultProtein(kind: GoalKind): Double = when (kind) {
        GoalKind.BULK -> 2.0
        GoalKind.CUT -> 2.2
        else -> 1.6
    }

    /** Kcal a day above (+) or below (−) maintenance to choose from; none for maintain. */
    fun changeChoices(kind: GoalKind): List<Int> = when (kind) {
        GoalKind.BULK -> listOf(200, 250, 300)
        GoalKind.CUT -> listOf(-300, -400, -500)
        else -> emptyList()
    }

    fun defaultChange(kind: GoalKind): Int = when (kind) {
        GoalKind.BULK -> 250
        GoalKind.CUT -> -400
        else -> 0
    }

    /** How fast to gain or lose, kg a week, for [weightKg] (bulk 0.25–0.5%, cut 0.5–1% of bodyweight); null when maintaining. */
    fun weeklyRate(kind: GoalKind, weightKg: Double?): ClosedFloatingPointRange<Double>? {
        val w = weightKg ?: return null
        return when (kind) {
            GoalKind.BULK -> w * 0.0025..w * 0.005
            GoalKind.CUT -> w * 0.005..w * 0.01
            else -> null
        }
    }

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

    /** The day's targets for [s]; null for no goal. */
    fun targets(p: Profile, s: GoalSettings): GoalTargets? {
        if (s.kind == GoalKind.NONE) return null
        val estimate = maintenance(p)
        val m = s.maintenance ?: estimate
        val perKg = s.proteinPerKg?.takeIf { it in proteinChoices(s.kind) } ?: defaultProtein(s.kind)
        val change = s.change?.takeIf { it in changeChoices(s.kind) } ?: defaultChange(s.kind)
        val w = p.weightKg
        return GoalTargets(
            kcal = m?.plus(change),
            maintenance = m,
            estimated = s.maintenance == null && estimate != null,
            proteinGrams = w?.let { (it * perKg).roundToInt() },
            proteinPerMeal = w?.let { (it * 0.4).roundToInt()..(it * 0.5).roundToInt() },
            proteinPerKg = perKg,
            change = change,
        )
    }
}
