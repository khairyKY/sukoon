package com.sukoon.app.insulin

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealChangeTest {
    private val at = Instant.parse("2026-10-16T13:00:00Z")
    private val action = InsulinAction()

    @Test
    fun `ate less right after the dose - nearly all the extra is still coming`() {
        // 6 u for 80 g at 1:10 g/u... ratio 12 g/u: 80 g needs 6.7 u, so all 6 u were meal insulin; 36 g needs 3 u.
        val g = MealChange.assess(80.0, 36.0, 6.0, at, at, at.plus(Duration.ofMinutes(10)), ratio = 12.0, factor = 40.0, glucose = 140, action = action)!!
        assertTrue(g.ateLess)
        assertEquals(3.0, g.gapUnits, 1e-9)
        assertTrue(g.stillActing in 2.8..3.0)
        assertTrue(g.ahead!! in 20..30) // 140 − ~3 u × 40: a low ahead
        assertEquals(40, g.coverCarbs) // ~2.95 u × 12 g = 35.4 → 40
    }

    @Test
    fun `a correction in the dose stays out of the gap, and late or small changes say nothing`() {
        // 8 u for 60 g at 12 g/u: 5 u meal, 3 u correction. Ate 60 g: no gap.
        assertNull(MealChange.assess(60.0, 60.0, 8.0, at, at, at, 12.0, 40.0, 220, action))
        assertNull(MealChange.assess(80.0, 72.0, 6.0, at, at, at, 12.0, 40.0, 140, action)) // under a unit
        assertNull(MealChange.assess(80.0, 0.0, 6.0, at, at, at.plus(Duration.ofHours(6)), 12.0, 40.0, 140, action)) // spent
        assertNull(MealChange.assess(80.0, 0.0, 6.0, at, at, at, null, 40.0, 140, action)) // no ratio: words only
    }

    @Test
    fun `ate more - a top-up only within 2 h of the meal and not after a low`() {
        val g = MealChange.assess(40.0, 90.0, 3.0, at, at, at.plus(Duration.ofMinutes(30)), 12.0, 40.0, 150, action)!!
        assertTrue(!g.ateLess)
        assertEquals(4.0, g.topUpUnits!!, 1e-9) // the 3 u were all meal insulin; 90 g needs 7.5 u: 4.5 short, 4 u in whole steps
        assertNotNull(g.ahead)
        assertNull(MealChange.assess(40.0, 90.0, 3.0, at, at, at.plus(Duration.ofMinutes(150)), 12.0, 40.0, 150, action)!!.topUpUnits)
        assertNull(MealChange.assess(40.0, 90.0, 3.0, at, at, at.plus(Duration.ofMinutes(30)), 12.0, 40.0, 150, action, lowSince2h = true)!!.topUpUnits)
    }
}
