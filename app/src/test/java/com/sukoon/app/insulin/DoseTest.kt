package com.sukoon.app.insulin

import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.insights.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseTest {

    private val settings = DoseSettings(enabled = true, carbRatio = mapOf(MealSlot.LUNCH to 12.0), correctionFactor = 50.0, target = 110)

    private fun advise(carbs: Double?, glucose: Int?, trend: TrendDirection? = TrendDirection.STEADY, onBoard: Double = 0.0, s: DoseSettings = settings) =
        Dose.advise(s, MealSlot.LUNCH, carbs, glucose, trend, onBoard)

    @Test
    fun `meal plus correction, rounded down to the pen`() {
        val a = advise(45.0, 160) as DoseAdvice.Suggestion // 3.75 + 1.0 = 4.75
        assertEquals(4.5, a.units, 1e-9)
        assertEquals(1.0, a.correctionUnits!!, 1e-9)
    }

    @Test
    fun `insulin still working offsets the correction, never the meal`() {
        val a = advise(45.0, 160, onBoard = 3.0) as DoseAdvice.Suggestion
        assertEquals(0.0, a.correctionUnits!!, 1e-9)
        assertEquals(1.0, a.onBoardUsed, 1e-9)
        assertEquals(3.5, a.units, 1e-9) // 3.75 rounded down
    }

    @Test
    fun `below target takes some off the meal, and never goes under zero`() {
        assertEquals(3.0, (advise(45.0, 90) as DoseAdvice.Suggestion).units, 1e-9) // 3.75 - 0.4
        assertEquals(0.0, (advise(null, 80, trend = TrendDirection.RISING) as DoseAdvice.Suggestion).units, 1e-9)
    }

    @Test
    fun `lows and drops come first, and empty numbers say nothing`() {
        assertEquals(DoseAdvice.TreatLowFirst, advise(45.0, 65))
        assertEquals(DoseAdvice.TreatLowFirst, advise(45.0, 95, TrendDirection.FALLING))
        assertNull(advise(45.0, 160, s = DoseSettings(enabled = true))) // no ratio, no factor
        assertNull(Dose.advise(settings, MealSlot.DINNER, 45.0, null, null, 0.0)) // no dinner ratio, no glucose
    }

    @Test
    fun `the maximum caps it`() {
        val a = advise(200.0, 300, s = settings.copy(maxDose = 8.0)) as DoseAdvice.Suggestion
        assertEquals(8.0, a.units, 1e-9)
        assertTrue(a.capped)
    }
}
