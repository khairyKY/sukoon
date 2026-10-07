package com.sukoon.app.insights

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightEngineTest {

    private val zone = ZoneOffset.UTC
    private val day0 = Instant.parse("2026-09-20T00:00:00Z")
    private val now = day0.plus(Duration.ofDays(6))

    private fun at(day: Int, minuteOfDay: Int) = day0.plus(Duration.ofDays(day.toLong())).plus(Duration.ofMinutes(minuteOfDay.toLong()))
    private fun r(t: Instant, mgDl: Int) = GlucoseReading(t, mgDl, TrendDirection.STEADY, SourceKind.LIBRE_BLE)

    /** Readings every 5 min for [days] days, value from (day, minute-of-day). */
    private fun series(days: Int, value: (Int, Int) -> Int) =
        (0 until days).flatMap { d -> (0 until 1440 step 5).map { m -> r(at(d, m), value(d, m)) } }

    private fun event(day: Int, minute: Int, type: String, value: Double) = EventEntity(timestampMillis = at(day, minute).toEpochMilli(), type = type, value = value)

    private inline fun <reified T : Insight> List<Insight>.find(): T? = filterIsInstance<T>().firstOrNull()

    @Test
    fun `finger-pricks are compared with the sensor at that moment`() {
        val events = listOf(event(1, 600, "FINGERSTICK", 100.0), event(2, 600, "FINGERSTICK", 120.0), event(3, 600, "FINGERSTICK", 60.0))
        val m = InsightEngine.analyze(series(6) { _, _ -> 120 }, events, now, zone).find<Insight.MeterAgreement>()!!
        // Sensor 120 each time: +20 % (agrees, 20/20), 0 % (agrees), +100 % (60 under 100: 60 off, disagrees).
        assertEquals(3, m.checks)
        assertEquals(2, m.agreeing)
        assertEquals(40, m.meanDiffPercent)
        assertEquals(Level.ATTENTION, m.level)
    }

    @Test
    fun `range summary splits time into the five consensus bands`() {
        // Each day: 1 h at 50, 1 h at 60, 18 h at 120, 3 h at 200, 1 h at 300.
        val readings = series(6) { _, m ->
            when {
                m < 60 -> 50
                m < 120 -> 60
                m < 1200 -> 120
                m < 1380 -> 200
                else -> 300
            }
        }
        val s = InsightEngine.summary(readings, now)!!
        assertEquals(100.0, s.veryLow + s.low + s.inRange + s.high + s.veryHigh, 1e-9)
        assertEquals(75.0, s.inRange, 1e-9)
        assertEquals(12.5, s.high, 1e-9)
        assertEquals(100.0 / 24, s.veryLow, 1e-9)
        assertNull(InsightEngine.summary(emptyList(), now))
    }

    @Test
    fun `too little data gives no claims`() {
        val out = InsightEngine.analyze(series(2) { _, _ -> 120 }, emptyList(), day0.plus(Duration.ofDays(2)), zone)
        assertTrue(out.single() is Insight.NotEnoughData)
    }

    @Test
    fun `steady in-range days meet every consensus target`() {
        val out = InsightEngine.analyze(series(6) { _, _ -> 120 }, emptyList(), now, zone)
        val t = out.find<Insight.Targets>()!!
        assertEquals(100, t.inRange)
        assertEquals(Level.GOOD, t.level)
        assertEquals(6.2, t.gmiPercent, 0.0) // 3.31 + 0.02392 × 120 = 6.18
        assertEquals(Level.GOOD, out.find<Insight.Variability>()!!.level)
    }

    @Test
    fun `percentages are time-weighted, not reading-counted`() {
        // 60 one-minute readings at 100, then one reading at 300 that stands for 15 minutes.
        val readings = (0 until 60).map { r(day0.plusSeconds(it * 60L), 100) } + r(day0.plusSeconds(3600), 300)
        val start = day0
        val weightedNow = start.plusSeconds(3600 + 15 * 60)
        val t = InsightEngine.analyze(readings, emptyList(), weightedNow, zone)
        // Too short for insights overall — check the weighting helper through coverage/targets directly instead.
        assertTrue(t.single() is Insight.NotEnoughData)
        // Time-weighted: 300 for 15 of 75 min → mean 140, SD 80 → CV 57 %. Counting readings would say ~25 %.
        assertEquals(57, InsightEngine.cvPercent(readings, weightedNow))
    }

    @Test
    fun `a low only counts after 15 minutes below 70, and gaps split runs`() {
        val dip = listOf(100, 65, 62, 60, 64, 75).mapIndexed { i, v -> r(day0.plusSeconds(i * 300L), v) } // 65 at 5 min .. 75 at 25 min
        assertEquals(1, InsightEngine.lowEpisodes(dip).size)
        val blip = listOf(100, 65, 66, 80).mapIndexed { i, v -> r(day0.plusSeconds(i * 300L), v) } // 10 minutes low
        assertTrue(InsightEngine.lowEpisodes(blip).isEmpty())
    }

    @Test
    fun `lows at the same hour on several days are a recurring pattern`() {
        // 03:00–03:30 at 60 on days 1, 2, 4, 5.
        val readings = series(6) { d, m -> if (d in setOf(1, 2, 4, 5) && m in 180..210) 60 else 130 }
        val lows = InsightEngine.analyze(readings, emptyList(), now, zone).find<Insight.RecurringLows>()!!
        assertEquals(3, lows.fromHour)
        assertEquals(4, lows.toHour)
        assertEquals(4, lows.days)
        assertTrue(lows.overnight)
        assertEquals(Level.URGENT, lows.level)
    }

    @Test
    fun `afternoons above 180 on most days are flagged as recurring highs`() {
        val readings = series(6) { _, m -> if (m in 14 * 60 until 17 * 60) 230 else 120 }
        val highs = InsightEngine.analyze(readings, emptyList(), now, zone).find<Insight.RecurringHighs>()!!
        assertEquals(14, highs.fromHour)
        assertEquals(17, highs.toHour)
        assertEquals(100, highs.percentOfDays)
    }

    @Test
    fun `a rise from the night low to 7am is a dawn pattern unless breakfast came first`() {
        val readings = series(6) { _, m -> if (m in 3 * 60 until 6 * 60) 100 else if (m in 7 * 60 until 8 * 60) 145 else 120 }
        val dawn = InsightEngine.analyze(readings, emptyList(), now, zone).find<Insight.DawnRise>()!!
        assertEquals(6, dawn.nights)
        assertEquals(45, dawn.medianRise)
        val breakfasts = (0 until 6).map { event(it, 6 * 60 + 30, "CARB", 40.0) }
        assertNull(InsightEngine.analyze(readings, breakfasts, now, zone).find<Insight.DawnRise>())
    }

    @Test
    fun `meal outcomes report highs after breakfast and the ratio actually used`() {
        // Breakfast at 08:00 each day: 120 → 260 by 09:00, 230 at 10:00 (2 h). 60 g with 5 U at the meal.
        val readings = series(6) { _, m ->
            when (m) {
                in 8 * 60..9 * 60 -> 120 + (m - 8 * 60) * 140 / 60
                in 9 * 60 + 1..10 * 60 + 30 -> 230
                else -> 120
            }
        }
        val events = (0 until 5).flatMap { listOf(event(it, 8 * 60, "CARB", 60.0), event(it, 8 * 60, "INSULIN", 5.0)) }
        val breakfast = InsightEngine.analyze(readings, events, now, zone).filterIsInstance<Insight.MealOutcomes>().single()
        assertEquals(MealSlot.BREAKFAST, breakfast.slot)
        assertEquals(5, breakfast.meals)
        assertEquals(5, breakfast.highAt2h)
        assertEquals(12.0, breakfast.gramsPerUnit!!, 0.0)
        assertEquals(Level.ATTENTION, breakfast.level)
    }

    @Test
    fun `pre-bolused meals rising less than at-meal boluses show the effect`() {
        // Days 0-2: insulin 15 min before → peak +40. Days 3-5: insulin at the meal → peak +110.
        val readings = series(6) { d, m -> if (m in 13 * 60 + 30..15 * 60) 120 + (if (d < 3) 40 else 110) else 120 }
        val events = (0 until 6).flatMap { d ->
            listOf(event(d, 13 * 60, "CARB", 50.0), event(d, 13 * 60 - if (d < 3) 15 else 0, "INSULIN", 5.0))
        }
        val pre = InsightEngine.analyze(readings, events, now, zone).find<Insight.PreBolus>()!!
        assertEquals(40, pre.earlyRise)
        assertEquals(110, pre.lateRise)
        assertEquals(Level.GOOD, pre.level)
    }

    @Test
    fun `lows after two rapid doses within 3 hours are flagged as stacking`() {
        // Days 1 and 3: doses at 12:00 and 14:00, then 60 from 16:00 to 16:30.
        val readings = series(6) { d, m -> if (d in setOf(1, 3) && m in 16 * 60..16 * 60 + 30) 60 else 130 }
        val events = listOf(1, 3).flatMap { listOf(event(it, 12 * 60, "INSULIN", 4.0), event(it, 14 * 60, "INSULIN", 3.0)) }
        val stacking = InsightEngine.analyze(readings, events, now, zone).find<Insight.Stacking>()!!
        assertEquals(2, stacking.lowsAfterStacking)
    }

    @Test
    fun `formula estimates use only days with both basal and rapid logged`() {
        val readings = series(6) { _, _ -> 120 }
        val events = (0 until 3).flatMap { listOf(event(it, 22 * 60, "BASAL", 20.0), event(it, 13 * 60, "INSULIN", 20.0)) } +
            event(4, 13 * 60, "INSULIN", 50.0) // rapid only → incomplete day, ignored
        val f = InsightEngine.analyze(readings, events, now, zone).find<Insight.Formulas>()!!
        assertEquals(3, f.completeDays)
        assertEquals(40.0, f.totalDailyDose, 0.0)
        assertEquals(12.5, f.gramsPerUnit500, 0.0)
        assertEquals(45.0, f.mgDlPerUnit1800, 0.0)
    }

    @Test
    fun `carb response compares the rise per 10 g at each meal`() {
        // Breakfast 08:00 40 g → +80 (20 per 10 g); dinner 19:00 40 g → +40 (10 per 10 g). Peak 60 min after.
        val readings = series(6) { _, m ->
            when (m) {
                in 8 * 60..9 * 60 -> 120 + (m - 8 * 60) * 80 / 60
                in 19 * 60..20 * 60 -> 120 + (m - 19 * 60) * 40 / 60
                else -> 120
            }
        }
        val events = (0 until 5).flatMap { listOf(event(it, 8 * 60, "CARB", 40.0), event(it, 19 * 60, "CARB", 40.0)) }
        val c = InsightEngine.analyze(readings, events, now, zone).find<Insight.CarbResponse>()!!
        assertEquals(listOf(MealSlot.BREAKFAST to 20, MealSlot.DINNER to 10), c.slots.map { it.slot to it.per10g })
        assertEquals(60, c.peakMinutes)
    }

    @Test
    fun `fat or protein heavy meals are still up four hours later`() {
        // Lunch 13:00 each day; days 0-2 carry 30 g fat and stay +60 at 4 h, days 3-5 come back to start.
        val readings = series(6) { d, m -> if (m in 13 * 60 + 30..17 * 60 + 15) (if (d < 3) 180 else if (m < 15 * 60) 170 else 120) else 120 }
        val events = (0 until 6).map { d -> event(d, 13 * 60, "CARB", 50.0).copy(fat = if (d < 3) 30.0 else 5.0) }
        val rich = InsightEngine.analyze(readings, events, now, zone).find<Insight.RichMeals>()!!
        assertEquals(3, rich.rich)
        assertEquals(60, rich.richAt4h)
        assertEquals(0, rich.leanAt4h)
        assertEquals(Level.ATTENTION, rich.level)
        assertTrue(InsightEngine.richMeal(fat = null, protein = 25.0))
    }

    @Test
    fun `lows followed by a high within 2 hours are rebounds`() {
        // A low at 03:00 every day; on days 1, 3 and 5 it is followed by 220 at 04:00.
        val readings = series(6) { d, m ->
            when {
                m in 3 * 60..3 * 60 + 25 -> 60
                d % 2 == 1 && m in 4 * 60..4 * 60 + 30 -> 220
                else -> 130
            }
        }
        val reb = InsightEngine.analyze(readings, emptyList(), now, zone).find<Insight.Rebounds>()!!
        assertEquals(3, reb.rebounds)
        assertEquals(6, reb.lows)
    }

    @Test
    fun `lows within a day of a workout are linked to it`() {
        // Workouts at 18:00 on days 1 and 3, lows at 02:00 the night after.
        val readings = series(6) { d, m -> if (d in setOf(2, 4) && m in 2 * 60..2 * 60 + 25) 62 else 130 }
        val events = listOf(event(1, 18 * 60, "ACTIVITY", 45.0), event(3, 18 * 60, "ACTIVITY", 30.0), event(4, 18 * 60, "ACTIVITY", 5.0))
        val a = InsightEngine.analyze(readings, events, now, zone).find<Insight.ActivityLows>()!!
        assertEquals(2, a.followed)
        assertEquals(2, a.workouts) // the 5-minute walk doesn't count
        assertEquals(2, a.overnight)
    }

    @Test
    fun `nights count in-range nights and nights with a low`() {
        val readings = series(6) { d, m -> if (d == 2 && m in 60..90) 60 else if (d == 4 && m in 120..180) 200 else 120 }
        val n = InsightEngine.analyze(readings, emptyList(), now, zone).find<Insight.Nights>()!!
        assertEquals(6, n.nights)
        assertEquals(4, n.inRange)
        assertEquals(1, n.withLows)
    }

    @Test
    fun `this week is compared with the week before`() {
        // Week 1 averages 160 with afternoons at 220; week 2 is flat 120.
        val readings = series(14) { d, m -> if (d < 7 && m in 12 * 60..18 * 60) 220 else 120 }
        val w = InsightEngine.analyze(readings, emptyList(), day0.plus(Duration.ofDays(14)), zone).find<Insight.WeekOverWeek>()!!
        assertEquals(100, w.inRange)
        assertTrue(w.inRangeBefore < 80)
        assertTrue(w.mean < w.meanBefore)
        assertEquals(Level.GOOD, w.level)
    }

    @Test
    fun `the most serious observations come first`() {
        val readings = series(6) { d, m -> if (d in setOf(1, 2, 4, 5) && m in 180..210) 60 else 130 }
        val out = InsightEngine.analyze(readings, emptyList(), now, zone)
        assertEquals(Level.URGENT, out.first().level)
    }
}
