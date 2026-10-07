package com.sukoon.app.insulin

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.insights.MealSlot
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RatioLearnerTest {

    private val zone = ZoneOffset.UTC
    private val day0 = Instant.parse("2026-10-01T00:00:00Z")

    /** A lunch at 13:00 on [day]: 60 g with [units], glucose going 120 → 120 + [rise] by 4 h, readings every 15 min. */
    private fun lunch(day: Long, units: Double, rise: Int): Pair<List<EventEntity>, List<GlucoseReading>> {
        val t = day0.plus(Duration.ofDays(day)).plus(Duration.ofHours(13))
        val events = listOf(
            EventEntity(timestampMillis = t.toEpochMilli(), type = "CARB", value = 60.0),
            EventEntity(timestampMillis = t.minus(Duration.ofMinutes(10)).toEpochMilli(), type = "INSULIN", value = units),
        )
        val readings = (0..16).map { i -> GlucoseReading(t.plus(Duration.ofMinutes(15L * i)), 120 + rise * i / 16, TrendDirection.STEADY, SourceKind.LIBRE_BLE) }
        return events to readings
    }

    private fun learn(meals: List<Pair<List<EventEntity>, List<GlucoseReading>>>, factor: Double = 50.0) =
        RatioLearner.learn(meals.flatMap { it.second }, meals.flatMap { it.first }, factor, zone, InsulinAction())

    @Test
    fun `under-dosed lunches point to a stronger ratio`() {
        // 60 g with 5 u (1:12) ended 50 higher; at 1 u per 50 mg/dL that's 1 u short: 60 / 6 = 10.
        val learned = learn((0L until 5).map { lunch(it, 5.0, 50) }).single()
        assertEquals(MealSlot.LUNCH, learned.slot)
        assertEquals(10.0, learned.ratio, 1e-9)
        assertEquals(5, learned.meals)
    }

    @Test
    fun `too few or unclean meals teach nothing`() {
        assertTrue(learn((0L until 4).map { lunch(it, 5.0, 50) }).isEmpty())
        // A snack 2 h after each lunch muddies all five.
        val withSnacks = (0L until 5).map { d ->
            val (e, r) = lunch(d, 5.0, 50)
            (e + EventEntity(timestampMillis = e.first().timestampMillis + Duration.ofHours(2).toMillis(), type = "CARB", value = 20.0)) to r
        }
        assertTrue(learn(withSnacks).isEmpty())
    }

    @Test
    fun `a proposal moves 20 percent at most`() {
        assertEquals(10.0, RatioLearner.step(null, 10.0), 1e-9)
        assertEquals(12.0, RatioLearner.step(15.0, 10.0), 1e-9)
        assertEquals(11.0, RatioLearner.step(12.0, 11.0), 1e-9)
    }
}
