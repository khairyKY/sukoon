package com.sukoon.app.modes

import com.sukoon.app.insulin.Profile
import com.sukoon.app.insulin.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkTest {
    private val kai = Profile(weightKg = 75.0, heightCm = 178, ageYears = 21, sex = Sex.MALE)

    @Test
    fun `maintenance from Mifflin-St Jeor, targets from the research`() {
        // 10×75 + 6.25×178 − 5×21 + 5 = 1762.5; × 1.55 = 2731.9 → 2730
        assertEquals(2730, Bulk.maintenance(kai))
        val t = Bulk.targets(kai, BulkSettings(on = true))
        assertEquals(2980, t.kcal) // + 250
        assertEquals(150, t.proteinGrams) // 2.0 g/kg
        assertEquals(30..38, t.proteinPerMeal) // 0.4–0.5 g/kg
        assertTrue(t.estimated)
    }

    @Test
    fun `your own maintenance wins, and a missing profile says nothing`() {
        val t = Bulk.targets(kai, BulkSettings(on = true, maintenance = 2500, surplus = 200))
        assertEquals(2700, t.kcal)
        assertFalse(t.estimated)
        assertNull(Bulk.maintenance(Profile(weightKg = 75.0)))
        assertNull(Bulk.targets(Profile(), BulkSettings(on = true)).kcal)
    }
}
