package com.sukoon.app.insulin

import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.insights.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseTest {

    private val settings = DoseSettings(enabled = true, carbRatio = mapOf(MealSlot.LUNCH to 12.0), correctionFactor = 50.0, target = 110, step = 0.5)

    private fun advise(carbs: Double?, glucose: Int?, trend: TrendDirection? = TrendDirection.STEADY, onBoard: Double = 0.0, s: DoseSettings = settings) =
        Dose.advise(s, MealSlot.LUNCH, carbs, glucose, trend, onBoard)

    @Test
    fun `meal plus correction, rounded down to the pen`() {
        val a = advise(45.0, 160) as DoseAdvice.Suggestion // 3.75 + 1.0 = 4.75
        assertEquals(4.5, a.units, 1e-9)
        assertEquals(1.0, a.correctionUnits!!, 1e-9)
        assertEquals(4.0, (advise(45.0, 160, s = settings.copy(step = 1.0)) as DoseAdvice.Suggestion).units, 1e-9) // whole-unit pen
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
        assertEquals(DoseAdvice.TreatLowFirst(65, 3.5), advise(45.0, 65)) // the meal's own dose for once you're over 70
        assertEquals(DoseAdvice.TreatLowFirst(95, 3.5), advise(45.0, 95, TrendDirection.FALLING))
        assertEquals(DoseAdvice.TreatLowFirst(65, null), advise(null, 65))
        assertNull(advise(45.0, 160, s = DoseSettings(enabled = true))) // no ratio, no factor
        assertNull(Dose.advise(settings, MealSlot.DINNER, 45.0, null, null, 0.0)) // no dinner ratio, no glucose
    }

    @Test
    fun `the maximum caps it`() {
        val a = advise(200.0, 300, s = settings.copy(maxDose = 8.0)) as DoseAdvice.Suggestion
        assertEquals(8.0, a.units, 1e-9)
        assertTrue(a.capped)
    }

    @Test
    fun `starting ratios come from the logbook, else weight and age, else the common start for adults`() {
        val logbook = com.sukoon.app.insights.Insight.Formulas(completeDays = 4, totalDailyDose = 40.0, gramsPerUnit500 = 12.5, mgDlPerUnit1800 = 45.0)
        assertEquals(StartSource.LOGBOOK, StartingPoints.suggest(logbook, Profile(weightKg = 80.0))!!.source)
        val byWeight = StartingPoints.suggest(null, Profile(weightKg = 80.0, ageYears = 30))!! // 40 u a day
        assertEquals(12.5, byWeight.ratio, 1e-9)
        assertEquals(45.0, byWeight.factor, 1e-9)
        assertEquals(StartSource.COMMON, StartingPoints.suggest(null, Profile())!!.source)
        assertEquals(StartSource.LOGBOOK, StartingPoints.suggest(logbook, Profile(ageYears = 15))!!.source)
        val teen = StartingPoints.suggest(null, Profile(weightKg = 50.0, ageYears = 14))!! // 1.0 u/kg: 50 u a day
        assertEquals(10.0, teen.ratio, 1e-9)
        assertEquals(35.0, teen.factor, 1e-9) // 1800 / 50 = 36
        assertNull(StartingPoints.suggest(null, Profile(ageYears = 9))) // a child needs a weight
    }

    @Test
    fun `home suggests a correction only when high, not eating and little insulin working`() {
        val now = java.time.Instant.parse("2026-10-08T15:00:00Z")
        val s = settings.copy(step = 1.0)
        fun corr(g: Int, onBoard: Double = 0.0, ateAgo: Long? = null, readingAgo: Long = 2, top: Int = 180) =
            Dose.correction(s, g, TrendDirection.STEADY, now.minusSeconds(readingAgo * 60), now, top, onBoard, ateAgo?.let { now.minusSeconds(it * 60) }, MealSlot.LUNCH)
        assertEquals(2.0, corr(220)!!.units, 1e-9) // (220 - 110) / 50 = 2.2, rounded down
        assertNull(corr(170)) // in range
        assertEquals(1.0, corr(170, top = 140)!!.units, 1e-9) // above a tighter range: 1.2
        assertNull(corr(220, ateAgo = 60)) // ate an hour ago
        assertNull(corr(220, onBoard = 1.0)) // insulin still working
        assertNull(corr(220, readingAgo = 30)) // stale reading
        assertNull(corr(150, top = 140)) // 0.8 u: under one pen step
        assertNull(Dose.correction(s.copy(enabled = false), 220, null, now, now, 180, 0.0, null, MealSlot.LUNCH))
    }

    @Test
    fun `home suggests a meal's dose only within an hour, with no insulin taken or working`() {
        val now = java.time.Instant.parse("2026-10-08T13:30:00Z")
        val s = settings.copy(step = 1.0)
        fun meal(carbs: Double = 60.0, ago: Long = 20, insulin: Boolean = false, onBoard: Double = 0.0, g: Int = 150) =
            Dose.mealDose(s, carbs, now.minusSeconds(ago * 60), insulin, g, TrendDirection.STEADY, now.minusSeconds(120), now, onBoard, MealSlot.LUNCH)
        assertEquals(5.0, meal()!!.units, 1e-9) // 60 / 12 = 5, + (150 - 110) / 50 = 0.8: 5.8, rounded down
        assertNull(meal(ago = 90)) // over an hour ago
        assertNull(meal(insulin = true))
        assertNull(meal(onBoard = 1.0))
        assertNull(meal(carbs = 5.0))
        assertNull(meal(g = 65)) // low: Home's low screen says what to do
    }
}
