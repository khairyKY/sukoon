package com.sukoon.app.food

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodsTest {
    @Test
    fun `a product becomes a food per 100 g, its brand first of several`() {
        val food = OpenFoodFacts.parse(
            JSONObject(
                """{"code":"6221024","product_name":"Foul medames","brands":"Americana, Halwani","product_quantity":400,
                   "nutriments":{"carbohydrates_100g":13,"fiber_100g":5,"proteins_100g":7,"fat_100g":1.5,"energy-kcal_100g":110}}""",
            ),
        )!!
        assertEquals("Americana", food.brand)
        assertEquals(400.0, food.packageGrams!!, 0.0)
        assertEquals(26.0, food.of(200.0)[Food.CARBS], 1e-9) // half a can
        assertEquals(220.0, food.of(200.0)[Food.KCAL], 1e-9)
        assertEquals(listOf("¼" to 100.0, "½" to 200.0, "1" to 400.0), portions(food))
    }

    @Test
    fun `no carbs, no food (it could not be dosed from)`() {
        assertNull(OpenFoodFacts.parse(JSONObject("""{"product_name":"Water","nutriments":{"energy-kcal_100g":0}}""")))
    }

    @Test
    fun `one of yours is counted in portions`() {
        val bowl = Food(name = "Koshari", per100 = listOf(70.0, 0.0, 0.0, 0.0, 0.0, 0.0), own = true)
        assertEquals(105.0, bowl.of(1.5)[Food.CARBS], 1e-9)
    }

    @Test
    fun `arabic spellings match whichever way they're typed`() {
        assertEquals(Dishes.normalize("ملوخيه"), Dishes.normalize("مُلوخيّة"))
        assertEquals(Dishes.normalize("ارانب"), Dishes.normalize("أرانب"))
    }
}
