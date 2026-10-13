package com.sukoon.app.modes

import com.sukoon.app.insulin.Profile
import com.sukoon.app.insulin.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalTest {
    private val kai = Profile(weightKg = 75.0, heightCm = 178, ageYears = 21, sex = Sex.MALE)

    @Test
    fun `maintenance from Mifflin-St Jeor, a lean bulk from the research`() {
        // 10×75 + 6.25×178 − 5×21 + 5 = 1762.5; × 1.55 = 2731.9 → 2730
        assertEquals(2730, Goals.maintenance(kai))
        val t = Goals.targets(kai, GoalSettings(GoalKind.BULK))!!
        assertEquals(2980, t.kcal) // + 250
        assertEquals(150, t.proteinGrams) // 2.0 g/kg
        assertEquals(30..38, t.proteinPerMeal) // 0.4–0.5 g/kg
        assertTrue(t.estimated)
    }

    @Test
    fun `each goal has its own defaults, and a pick from another goal falls back`() {
        val cut = Goals.targets(kai, GoalSettings(GoalKind.CUT))!!
        assertEquals(2730 + Goals.defaultChange(GoalKind.CUT), cut.kcal)
        assertEquals(Goals.defaultProtein(GoalKind.CUT), cut.proteinPerKg, 1e-9)
        val maintain = Goals.targets(kai, GoalSettings(GoalKind.MAINTAIN, change = 250))!! // a bulk's surplus doesn't carry over
        assertEquals(2730, maintain.kcal)
        assertNull(Goals.targets(kai, GoalSettings(GoalKind.NONE)))
    }

    @Test
    fun `your own maintenance wins, and a missing profile says nothing`() {
        val t = Goals.targets(kai, GoalSettings(GoalKind.BULK, maintenance = 2500, change = 200))!!
        assertEquals(2700, t.kcal)
        assertFalse(t.estimated)
        assertNull(Goals.maintenance(Profile(weightKg = 75.0)))
        assertNull(Goals.targets(Profile(), GoalSettings(GoalKind.BULK))!!.kcal)
    }
}
