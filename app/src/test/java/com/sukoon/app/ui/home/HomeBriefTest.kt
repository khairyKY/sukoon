package com.sukoon.app.ui.home

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.insulin.InsulinAction
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBriefTest {

    private val now = Instant.parse("2026-10-04T14:00:00Z")
    private fun at(hour: Int) = Instant.parse(String.format("2026-10-04T%02d:00:00Z", hour))

    /** One reading a minute ending at [end], oldest first; the newest carries [trend]. */
    private fun series(values: List<Int>, end: Instant = now, trend: TrendDirection = TrendDirection.STEADY) = values.mapIndexed { i, v ->
        GlucoseReading(end.minusSeconds((values.lastIndex - i) * 60L), v, if (i == values.lastIndex) trend else TrendDirection.STEADY, SourceKind.LIBRE_BLE)
    }

    private fun event(type: LogEventType, value: Double, minutesAgo: Long) =
        EventEntity(timestampMillis = now.minusSeconds(minutesAgo * 60).toEpochMilli(), type = type.name, value = value)

    private fun brief(readings: List<GlucoseReading>, events: List<EventEntity> = emptyList(), at: Instant = now, treatedAt: Instant? = null) =
        HomeBriefs.of(readings, events, at, ZoneOffset.UTC, InsulinAction(), treatedAt = treatedAt)

    @Test
    fun `falling toward 70 comes first, with carbs ready`() {
        // 120 to 90 over 15 min (-2/min): 70 in 10 min.
        val b = brief(series((0..15).map { 120 - it * 2 }, trend = TrendDirection.FALLING))
        assertEquals(Brief.HeadingLow(10, 0.0), b)
        assertEquals(Step.CARBS_READY, b!!.step)
    }

    @Test
    fun `a low says eat, and once treated says wait the 15 minutes`() {
        val low = series(listOf(80, 75, 68, 62), trend = TrendDirection.FALLING)
        assertEquals(Brief.Low(0.0), brief(low))
        val pressed = now.minusSeconds(5 * 60)
        assertEquals(Brief.Treated(pressed), brief(low, treatedAt = pressed))
        assertEquals(Step.RECHECK, brief(low, treatedAt = pressed)!!.step)
        assertEquals(Brief.Low(0.0, again = true), brief(low, treatedAt = now.minusSeconds(20 * 60))) // 15 minutes are up: treat again
        assertEquals(Brief.Low(0.0), brief(low, treatedAt = now.minusSeconds(90 * 60))) // an hour on, a new low
        assertTrue(brief(low, listOf(event(LogEventType.CARB, 15.0, 3))) is Brief.Treated) // logged juice counts too
    }

    @Test
    fun `high with insulin still working warns against stacking`() {
        val b = brief(series(List(30) { 220 }), listOf(event(LogEventType.INSULIN, 3.0, 30)))
        assertTrue(b is Brief.InsulinWorking)
        assertEquals(Step.DONT_STACK, b!!.step)
    }

    @Test
    fun `over 250 for two hours asks about ketones, briefly over asks for water`() {
        assertEquals(Brief.VeryHighFor(130), brief(series(List(131) { 270 })))
        assertEquals(Step.KETONES, brief(series(List(131) { 270 }))!!.step)
        assertEquals(Step.WATER, brief(series(List(31) { 270 }))!!.step)
    }

    @Test
    fun `rising after a logged meal needs nothing, rising without one asks to log it`() {
        val up = series((0..20).map { 120 + it * 2 }, trend = TrendDirection.RISING)
        val afterMeal = brief(up, listOf(event(LogEventType.CARB, 45.0, 40)))
        assertEquals(Brief.AfterMeal(45, now.minusSeconds(40 * 60)), afterMeal)
        assertNull(afterMeal!!.step)
        assertEquals(Brief.RisingNoMeal, brief(up))
        assertEquals(Step.LOG_MEAL, brief(up)!!.step)
        assertEquals(Brief.DawnRise, brief(series((0..20).map { 100 + it * 2 }, at(6), TrendDirection.RISING), at = at(6)))
    }

    @Test
    fun `after a low - back in range suggests a snack, a high is a rebound`() {
        // Low (62) for 20 minutes, starting 49 minutes ago; back at 95 for the last 30.
        assertEquals(Brief.BackFromLow(now.minusSeconds(49 * 60)), brief(series(List(10) { 80 } + List(20) { 62 } + List(30) { 95 })))
        val rebound = brief(series(List(20) { 60 } + List(100) { 210 }))
        assertTrue(rebound is Brief.Rebound)
        assertEquals(Step.LET_IT_SETTLE, rebound!!.step)
    }

    @Test
    fun `steady in range has nothing to do, and says more the longer it lasts`() {
        val calm = brief(series(List(60) { 110 }))
        assertTrue(calm is Brief.Steady)
        assertNull(calm!!.step)
        assertEquals(Brief.InRangeFor(240), brief(series(List(241) { 110 })))
        assertEquals(Brief.GoodNight, brief(series(List(421) { 110 }, at(7)), at = at(7)))
        assertEquals(Brief.QuietNight, brief(series(List(60) { 110 }, at(3)), at = at(3)))
    }

    @Test
    fun `bedtime under 110 and drifting down suggests a snack`() {
        val b = brief(series((0..15).map { 106 - it / 3 }, at(22), TrendDirection.FALLING), at = at(22))
        assertEquals(Brief.Bedtime(101, insulin = false), b)
        assertEquals(Step.BEDTIME_SNACK, b!!.step)
    }

    @Test
    fun `no fresh reading, no brief`() {
        assertNull(brief(series(List(10) { 110 }, now.minusSeconds(11 * 60))))
        assertNull(brief(emptyList()))
    }
}
