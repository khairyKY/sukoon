package com.sukoon.app.ui.logbook

import com.sukoon.app.data.db.LogEventType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class OneMealTest {
    private val at = Instant.parse("2026-10-21T09:00:00Z")
    private fun food(g: Double, name: String, protein: Double? = null) = EntryDraft(LogEventType.CARB, g, name, at, nutrients = protein?.let { Nutrients(protein = it) })

    @Test
    fun `foods said together are one meal, with the dose said with them`() {
        val out = oneMeal(listOf(food(30.0, "ful", 9.0), food(25.0, "aish baladi", 4.0), food(0.0, "eggs", 12.0), EntryDraft(LogEventType.INSULIN, 5.0, null, at)))
        assertEquals(1, out.size)
        val meal = out.single()
        assertEquals(55.0, meal.amount!!, 0.0)
        assertEquals("ful, aish baladi, eggs", meal.note)
        assertEquals(25.0, meal.nutrients!!.protein!!, 0.0)
        assertEquals(5.0, meal.insulin!!, 0.0)
    }

    @Test
    fun `a treatment, long-acting and a walk stay as said`() {
        val juice = EntryDraft(LogEventType.CARB, 15.0, "juice", at, treatment = true)
        val long = EntryDraft(LogEventType.BASAL, 20.0, null, at)
        val out = oneMeal(listOf(food(40.0, "koshari"), food(20.0, "basbousa"), juice, long))
        assertEquals(listOf(60.0, 15.0, 20.0), out.map { it.amount })
        assertEquals(listOf(food(40.0, "koshari")), oneMeal(listOf(food(40.0, "koshari"))))
    }
}
