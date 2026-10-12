package com.sukoon.app.insights

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightDigestTest {

    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val zone = ZoneOffset.UTC
    private val targets = Insight.Targets(14, 90, 72, 5, 1, 22, 3, 150, 6.9)
    private val nightLows = Insight.RecurringLows(fromHour = 2, toHour = 5, days = 4, episodes = 4, totalEpisodes = 6)
    private fun build(vararg insights: Insight) = InsightDigestBuilder.build(insights.toList(), emptyList(), now, zone)

    @Test
    fun `lows are the one thing, told once, with the first action that would change something`() {
        val digest = build(targets, nightLows, Insight.Nights(8, 14, 4), Insight.Stacking(1, 6), Insight.Variability(40))
        val one = digest.oneThing!!
        assertTrue(one.insight is LowsStory)
        assertEquals(InsightAction.TURN_ON_GOING_LOW, one.action(InEffect(goingLow = false)))
        assertEquals(InsightAction.LOOK_AT, one.action(InEffect(goingLow = true)))
        // Nights and stacking were folded into the lows story, not repeated.
        assertTrue((digest.alsoWorth + digest.more).none { it.insight is Insight.Nights || it.insight is Insight.Stacking })
    }

    @Test
    fun `lows after workouts offer the after-workout check first`() {
        val one = build(targets, nightLows, Insight.ActivityLows(followed = 3, workouts = 5, overnight = 1)).oneThing!!
        assertEquals(LowCause.WORKOUTS, (one.insight as LowsStory).cause)
        assertEquals(InsightAction.TURN_ON_WORKOUT_CHECK, one.action(InEffect()))
        assertEquals(InsightAction.TURN_ON_GOING_LOW, one.action(InEffect(workoutCheck = true)))
    }

    @Test
    fun `at most three more, formulas never shown, the rest folded`() {
        val digest = build(
            targets.copy(below70 = 2), Insight.RecurringHighs(13, 16, 60), Insight.DawnRise(10, 14, 40), Insight.Variability(41),
            Insight.Rotation(com.sukoon.app.insulin.InjectionSite.entries.first(), 8, 10, false),
            Insight.Formulas(7, 40.0, 12.5, 45.0), Insight.Nights(12, 14, 0),
        )
        assertEquals(Insight.RecurringHighs::class, digest.oneThing!!.insight::class)
        assertEquals(3, digest.alsoWorth.size)
        assertTrue((listOf(digest.oneThing!!) + digest.alsoWorth + digest.more).none { it.insight is Insight.Formulas })
        assertTrue(digest.more.any { it.insight is Insight.Nights })
    }

    @Test
    fun `not enough says what to log`() {
        assertEquals(NotEnough.Readings(2), build(Insight.NotEnoughData(2, 60)).notEnough)
        val calm = build(targets.copy(below70 = 2), Insight.Variability(30))
        assertNull(calm.oneThing)
        assertEquals(NotEnough.Meals(0), calm.notEnough)
    }
}
