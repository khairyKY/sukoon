package com.sukoon.app.food

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsdaFoodsTest {
    @Test
    fun `a USDA search hit becomes a food with protein and a portion`() {
        val hit = JSONObject(
            """{"fdcId": 172187, "description": "EGG, WHOLE, COOKED, SCRAMBLED", "dataType": "Survey (FNDDS)",
               "foodNutrients": [
                 {"nutrientNumber": "203", "nutrientName": "Protein", "value": 10.0},
                 {"nutrientNumber": "204", "nutrientName": "Total lipid (fat)", "value": 11.0},
                 {"nutrientNumber": "205", "nutrientName": "Carbohydrate, by difference", "value": 1.6},
                 {"nutrientNumber": "208", "nutrientName": "Energy", "value": 149}
               ],
               "foodMeasures": [{"disseminationText": "1 large egg", "gramWeight": 61}]}""",
        )
        val f = UsdaFoods.parse(hit)!!
        assertEquals("Egg, whole, cooked, scrambled", f.name)
        assertEquals(listOf(1.6, 0.0, 0.0, 10.0, 11.0, 149.0), f.per100)
        assertEquals(61.0, f.servingGrams!!, 1e-9)
        assertEquals("1 large egg", f.servingLabel)
        assertNull(UsdaFoods.parse(JSONObject("""{"description": "Water", "foodNutrients": []}""")))
    }
}
