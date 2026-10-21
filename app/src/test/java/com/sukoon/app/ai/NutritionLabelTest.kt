package com.sukoon.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionLabelTest {
    @Test
    fun `a label becomes what 100 g holds`() {
        val f = NutritionLabel.parse("""{"name":"Molto Magnum","per100g":{"carbs":48.5,"fiber":1.2,"sugar":22,"protein":6,"fat":21,"kcal":410},"serving_g":60}""")!!
        assertEquals("Molto Magnum", f.name)
        assertEquals(listOf(48.5, 1.2, 22.0, 6.0, 21.0, 410.0), f.per100)
        assertEquals(60.0, f.servingGrams!!, 0.0)
    }

    @Test
    fun `missing extras are zero, but no carbs means it wasn't read`() {
        assertEquals(listOf(12.0, 0.0, 0.0, 0.0, 0.0, 0.0), NutritionLabel.parse("""{"per100g":{"carbs":12}}""")!!.per100)
        assertNull(NutritionLabel.parse("""{"per100g":{"carbs":null,"kcal":200}}"""))
        assertNull(NutritionLabel.parse("""{"error":"not a label"}"""))
        assertNull(NutritionLabel.parse("""{"per100g":{"carbs":140}}""")) // more than 100 g in 100 g: misread
        assertNull(NutritionLabel.parse("sorry"))
    }
}
