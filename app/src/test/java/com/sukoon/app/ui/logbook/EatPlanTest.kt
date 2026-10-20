package com.sukoon.app.ui.logbook

import com.sukoon.app.ai.CarbEstimate
import com.sukoon.app.ai.VoiceEntry
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.food.Food
import com.sukoon.app.food.PlateItem
import org.junit.Assert.assertEquals
import org.junit.Test

class EatPlanTest {
    private fun food(g: Double, name: String?, protein: Double? = null) = VoiceEntry(LogEventType.CARB, g, name, estimated = true, protein = protein)

    @Test
    fun `foods said together are one plate, with the dose said with them`() {
        val plan = eatPlan(listOf(food(30.0, "ful", 9.0), food(25.0, "aish baladi", 4.0), food(0.0, null, 12.0), VoiceEntry(LogEventType.INSULIN, 5.0)), "Food")
        assertEquals(listOf("ful", "aish baladi", "Food"), plan.plate.map { it.food.name })
        val sums = plateSums(plan.plate)
        assertEquals(55.0, sums[Food.CARBS], 0.0)
        assertEquals(25.0, sums[3], 0.0)
        assertEquals(5.0, plan.insulin!!, 0.0)
        assertEquals(emptyList<VoiceEntry>(), plan.rest)
    }

    @Test
    fun `a treatment, long-acting, a second dose and a dose with no food stay as said`() {
        val juice = VoiceEntry(LogEventType.CARB, 15.0, "juice", treatment = true)
        val long = VoiceEntry(LogEventType.BASAL, 20.0)
        val plan = eatPlan(listOf(food(40.0, "koshari"), juice, long, VoiceEntry(LogEventType.INSULIN, 4.0), VoiceEntry(LogEventType.INSULIN, 2.0)), "Food")
        assertEquals(1, plan.plate.size)
        assertEquals(null, plan.insulin)
        assertEquals(listOf(15.0, 20.0, 4.0, 2.0), plan.rest.map { it.amount })
        val onlyDose = eatPlan(listOf(VoiceEntry(LogEventType.INSULIN, 6.0)), "Food")
        assertEquals(null, onlyDose.insulin)
        assertEquals(1, onlyDose.rest.size)
    }

    @Test
    fun `a photo's items share the meal's protein by their carbs, or it is one food`() {
        val two = photoPlate(CarbEstimate("Breakfast", 60, listOf("bread" to 45, "beans" to 15), "medium", "", proteinGrams = 20, kcal = 400))
        assertEquals(listOf(45.0, 15.0), two.map { it.sums[Food.CARBS] })
        assertEquals(20.0, plateSums(two)[3], 1e-9)
        assertEquals(400.0, plateSums(two)[Food.KCAL], 1e-9)
        val one = photoPlate(CarbEstimate("Koshari", 90, emptyList(), "high", ""))
        assertEquals("Koshari", one.single().food.name)
        assertEquals(90.0, one.single().sums[Food.CARBS], 0.0)
    }

    @Test
    fun `changing a food's carbs changes how much of it, or gives a no-carb food its carbs`() {
        val bread = PlateItem(Food("bread", per100 = listOf(50.0, 2.0, 1.0, 8.0, 1.0, 250.0)), 100.0)
        val more = withCarbs(bread, 75.0)
        assertEquals(150.0, more.amount, 1e-9)
        assertEquals(12.0, more.sums[3], 1e-9)
        val eggs = portion("eggs", 0.0, protein = 12.0)
        val given = withCarbs(eggs, 2.0)
        assertEquals(2.0, given.sums[Food.CARBS], 0.0)
        assertEquals(12.0, given.sums[3], 0.0)
    }
}
