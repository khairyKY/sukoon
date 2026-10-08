package com.sukoon.app.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayTotalsTest {
    private fun sums(carbs: Double, kcal: Double) = listOf(carbs, 0.0, 0.0, 0.0, 0.0, kcal)

    @Test
    fun `a rise is a meal of what was added`() {
        val step = DayTotals.step(sums(118.0, 900.0), sums(213.0, 1796.0))
        assertEquals(sums(95.0, 896.0), step.meal)
        assertEquals(sums(213.0, 1796.0), step.seen)
        assertNull(step.removed)
    }

    @Test
    fun `a small bite waits for the next`() {
        val step = DayTotals.step(sums(100.0, 800.0), sums(100.5, 810.0))
        assertNull(step.meal)
        assertEquals(sums(100.0, 800.0), step.seen) // still counted from 100, so the half gram isn't lost
    }

    @Test
    fun `food taken off comes off the newest meals`() {
        val step = DayTotals.step(sums(150.0, 1200.0), sums(110.0, 900.0))
        assertNull(step.meal)
        val left = DayTotals.takeOff(listOf(sums(80.0, 600.0), sums(30.0, 200.0), sums(40.0, 400.0)), step.removed!!)
        // 40 g and 300 kcal off: the newest meal goes (40 g, 300 of its 400 kcal leaves 100 kcal, so it stays).
        assertEquals(listOf(sums(80.0, 600.0), sums(30.0, 200.0), sums(0.0, 100.0)), left)
        val more = DayTotals.takeOff(listOf(sums(80.0, 600.0), sums(30.0, 200.0)), sums(50.0, 300.0))
        assertEquals(listOf(sums(60.0, 500.0), null), more)
    }
}
