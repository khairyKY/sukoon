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
        assertEquals(5, learned.meals.size)
        assertEquals(50, learned.meals.first().change4h)
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
    fun `every meal gets a verdict, and the export says which counted`() {
        val (lunchEvents, readings) = lunch(0, 5.0, 50)
        val snack = EventEntity(timestampMillis = lunchEvents.first().timestampMillis + Duration.ofHours(2).toMillis(), type = "CARB", value = 20.0)
        val noInsulin = EventEntity(timestampMillis = day0.plus(Duration.ofDays(1)).plus(Duration.ofHours(8)).toEpochMilli(), type = "CARB", value = 30.0)
        val report = RatioLearner.report(readings, lunchEvents + snack + noInsulin, 50.0, zone, InsulinAction())
        assertEquals(
            listOf(RatioLearner.Verdict.NO_INSULIN, RatioLearner.Verdict.NO_INSULIN, RatioLearner.Verdict.ATE_AGAIN), // newest first
            report.meals.map { it.verdict },
        )
        assertTrue(!report.factorEstimated)
        val csv = RatioLearner.csv(report, zone).lines()
        assertTrue(csv.first().startsWith("timestamp,meal,carbs_g"))
        assertTrue(csv[1].contains(",lunch,60,5,120,50,no,ate_again,,50"))
    }

    /** A point on its own day at [hour]:00: [carbs] with [units] (or a correction alone), glucose [start] → start + [change] by 4 h. */
    private fun point(day: Long, hour: Long, carbs: Double, units: Double, change: Int, start: Int = 150): Pair<List<EventEntity>, List<GlucoseReading>> {
        val t = day0.plus(Duration.ofDays(day)).plus(Duration.ofHours(hour))
        val events = listOfNotNull(
            if (carbs > 0) EventEntity(timestampMillis = t.toEpochMilli(), type = "CARB", value = carbs) else null,
            EventEntity(timestampMillis = t.minus(Duration.ofMinutes(if (carbs > 0) 10 else 0)).toEpochMilli(), type = "INSULIN", value = units),
        )
        val readings = (0..16).map { i -> GlucoseReading(t.plus(Duration.ofMinutes(15L * i)), start + change * i / 16, TrendDirection.STEADY, SourceKind.LIBRE_BLE) }
        return events to readings
    }

    @Test
    fun `the fit over every meal and correction finds the factor and each meal's ratio`() {
        // Truth: 1 u lowers 50; lunch 1 : 10 (a = 5), dinner 1 : 12.5 (a = 4). Change = a × carbs − 50 × insulin.
        val points = listOf(
            point(0, 13, 60.0, 5.0, 50), point(1, 13, 40.0, 4.0, 0), point(2, 13, 80.0, 6.0, 100),
            point(3, 19, 50.0, 3.0, 50), point(4, 19, 75.0, 6.0, 0), point(5, 19, 60.0, 5.0, -10),
            point(6, 16, 0.0, 2.0, -100, start = 250), point(7, 16, 0.0, 3.0, -150, start = 260),
        )
        val fit = RatioLearner.fit(points.flatMap { it.second }, points.flatMap { it.first }, zone, InsulinAction())!!
        assertEquals(50.0, fit.factor, 1e-9)
        assertEquals(10.0, fit.ratios.getValue(MealSlot.LUNCH), 1e-9)
        assertEquals(12.5, fit.ratios.getValue(MealSlot.DINNER), 1e-9)
        assertEquals(6, fit.meals)
        assertEquals(2, fit.corrections)
        assertEquals(null, RatioLearner.fit(points.take(5).flatMap { it.second }, points.take(5).flatMap { it.first }, zone, InsulinAction())) // too few
    }

    @Test
    fun `home nudges about a lesson first, then the fit, only when it differs by 10 percent or more`() {
        val lesson = RatioLearner.Learned(MealSlot.LUNCH, 11.0, 10.0, 12.0, emptyList(), 50.0)
        val fit = RatioLearner.Fit(45.0, mapOf(MealSlot.DINNER to 10.0), 9, 2)
        val report = RatioLearner.Report(emptyList(), 50.0, false, listOf(lesson), fit)
        val mine = DoseSettings(enabled = true, carbRatio = MealSlot.entries.associateWith { 13.0 }, correctionFactor = 50.0)
        assertEquals(RatioLearner.Nudge(MealSlot.LUNCH, 11.0), RatioLearner.nudge(report, mine))
        val lunchDone = mine.copy(carbRatio = mine.carbRatio + (MealSlot.LUNCH to 11.0))
        assertEquals(RatioLearner.Nudge(MealSlot.DINNER, 10.0), RatioLearner.nudge(report, lunchDone))
        val ratiosDone = lunchDone.copy(carbRatio = lunchDone.carbRatio + (MealSlot.DINNER to 10.5))
        assertEquals(RatioLearner.Nudge(null, 45.0), RatioLearner.nudge(report, ratiosDone)) // 45 vs 50: exactly 10%
        assertEquals(null, RatioLearner.nudge(report, ratiosDone.copy(correctionFactor = 47.0)))
        assertEquals("LUNCH:11.0", RatioLearner.Nudge(MealSlot.LUNCH, 11.0).key)
    }

    @Test
    fun `a proposal moves 20 percent at most`() {
        assertEquals(10.0, RatioLearner.step(null, 10.0), 1e-9)
        assertEquals(12.0, RatioLearner.step(15.0, 10.0), 1e-9)
        assertEquals(11.0, RatioLearner.step(12.0, 11.0), 1e-9)
    }
}
