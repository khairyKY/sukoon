package com.sukoon.app.health

/**
 * MyFitnessPal shares one Health Connect record per day: the day's running total, rewritten each
 * time food is added. Sukoon turns it back into meals: each rise is a meal, each fall comes off the
 * newest meals. Sums are [CARBS], fibre, sugar, protein, fat (g) and [KCAL], in that order.
 */
internal object DayTotals {
    const val CARBS = 0
    const val KCAL = 5

    /** What changed since [seen]: the meal it adds (null: too little yet), what was taken off, and the total to compare with next time. */
    class Step(val meal: List<Double>?, val removed: List<Double>?, val seen: List<Double>)

    fun step(seen: List<Double>, now: List<Double>): Step {
        val added = now.zip(seen) { a, b -> maxOf(0.0, a - b) }
        val removed = now.zip(seen) { a, b -> maxOf(0.0, b - a) }.takeIf { r -> r.any { it > 0.05 } }
        // A bite under 1 g of carbs and 20 kcal isn't a meal yet: it stays uncounted and adds up with the next.
        val meal = added.takeIf { it[CARBS] >= 1 || it[KCAL] >= 20 }
        return Step(meal, removed, if (meal != null) now else now.zip(seen, ::minOf))
    }

    /** [removed] taken off [meals] (oldest first), from the newest back; a meal left with nothing in it becomes null. */
    fun takeOff(meals: List<List<Double>>, removed: List<Double>): List<List<Double>?> {
        val left = removed.toMutableList()
        return meals.asReversed().map { meal ->
            val after = meal.mapIndexed { i, x -> (x - minOf(x, left[i])).also { left[i] -= x - it } }
            after.takeIf { it[CARBS] > 0.05 || it[KCAL] > 1 }
        }.asReversed()
    }
}
