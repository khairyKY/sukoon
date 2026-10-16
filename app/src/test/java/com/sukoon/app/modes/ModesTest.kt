package com.sukoon.app.modes

import com.sukoon.app.alarms.AlarmSettings
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModesTest {
    private val zone = ZoneOffset.UTC
    private fun at(time: String) = Instant.parse("2026-10-13T${time}:00Z")
    private fun workout(start: String, minutes: Int) = EventEntity(timestampMillis = at(start).toEpochMilli(), type = LogEventType.ACTIVITY.name, value = minutes.toDouble())
    private fun active(now: String, events: List<EventEntity> = emptyList(), fasting: FastingSettings = FastingSettings(), sick: Instant? = null, manualUntil: Instant? = null, manualStart: Instant? = null) =
        Modes.active(at(now), zone, manualUntil, manualStart, events, fasting, sick)

    @Test
    fun `a workout raises the low alarm to 100, then the night after watches at 80`() {
        val lift = listOf(workout("17:00", 60))
        val during = active("17:30", lift)
        assertTrue(during.workingOut)
        assertEquals(100, Modes.alarmSettings(AlarmSettings(), during).lowMgDl)
        val evening = active("20:00", lift)
        assertTrue(evening.lateLowWatch && !evening.nightWatch)
        assertEquals(70, Modes.alarmSettings(AlarmSettings(), evening).lowMgDl) // daytime: your own line
        val night = active("23:30", lift)
        assertTrue(night.nightWatch)
        assertEquals(80, Modes.alarmSettings(AlarmSettings(), night).lowMgDl)
        assertEquals(90, Modes.alarmSettings(AlarmSettings(lowMgDl = 90), night).lowMgDl) // never lowers yours
        assertFalse(Modes.active(at("17:30").plusSeconds(25 * 3600), zone, null, null, lift, FastingSettings(), null).lateLowWatch) // a day on: over
    }

    @Test
    fun `a manual workout counts like a logged one`() {
        val m = active("10:30", manualStart = at("10:00"), manualUntil = at("11:00"))
        assertTrue(m.workingOut)
        assertTrue(active("12:00", manualStart = at("10:00"), manualUntil = at("11:00")).lateLowWatch)
    }

    @Test
    fun `doses after a workout and at suhoor are cut, corrections around lifting halved`() {
        val m = active("19:00", listOf(workout("17:00", 60)))
        val meal = Modes.doseChange(m, at("19:00"), at("18:45"), zone, afterWorkoutCutPercent = 50)!!
        assertEquals(DoseChange.Reason.AFTER_WORKOUT, meal.reason)
        assertEquals(0.5, meal.mealFactor, 1e-9)
        assertEquals(0.5, meal.correctionFactor, 1e-9)
        assertNull(Modes.doseChange(m, at("19:00"), at("18:45"), zone, afterWorkoutCutPercent = 0)?.takeIf { it.mealFactor < 1 })
        assertNull(Modes.doseChange(active("21:30", listOf(workout("17:00", 60))), at("21:30"), at("21:30"), zone, 50)) // 3.5 h after: nothing
        val fasting = FastingSettings(on = true, suhoorEnds = LocalTime.of(4, 20), iftar = LocalTime.of(17, 40))
        val suhoor = Modes.doseChange(active("03:30", fasting = fasting), at("03:30"), at("03:30"), zone, 50)!!
        assertEquals(DoseChange.Reason.SUHOOR, suhoor.reason)
        assertEquals(0.7, suhoor.mealFactor, 1e-9)
        assertNull(Modes.doseChange(active("18:00", fasting = fasting), at("18:00"), at("18:00"), zone, 50)) // iftar: as usual
    }

    @Test
    fun `a custom cut after a workout is any 5 percent step, capped at 75`() {
        val m = active("19:00", listOf(workout("17:00", 60)))
        fun meal(percent: Int) = Modes.doseChange(m, at("19:00"), at("18:45"), zone, percent)!!
        assertEquals(0.65, meal(35).mealFactor, 1e-9)
        assertEquals(35, meal(35).mealCutPercent)
        assertEquals(DoseChange.Reason.IN_WORKOUT, meal(0).reason) // 0%: the meal as usual, only the correction halved
        assertEquals(1.0, meal(0).mealFactor, 1e-9)
        assertEquals(0.25, meal(90).mealFactor, 1e-9)
        (0..75 step 5).forEach { assertEquals(it, meal(it).takeIf { c -> c.reason == DoseChange.Reason.AFTER_WORKOUT }?.mealCutPercent ?: 0) } // 10, 20, 45 don't show as 9, 19, 44
        assertEquals(listOf(0, 25, 50, 35, 75, 0, 40), listOf(0, 25, 50, 35, 120, -10, 38).map(Modes::afterWorkoutCut)) // old choices kept as is
    }

    @Test
    fun `fasting runs from suhoor's end to iftar`() {
        val f = FastingSettings(on = true, suhoorEnds = LocalTime.of(4, 20), iftar = LocalTime.of(17, 40))
        assertTrue(active("12:00", fasting = f).fastingNow)
        assertFalse(active("18:00", fasting = f).fastingNow)
        assertFalse(active("04:00", fasting = f).fastingNow)
        assertFalse(active("12:00", fasting = f.copy(on = false)).fastingNow)
    }

    @Test
    fun `before lifting and ketone bands follow the guidelines`() {
        assertEquals(Modes.BeforeWorkout.CARBS_AND_WAIT, Modes.beforeWorkout(85))
        assertEquals(Modes.BeforeWorkout.SMALL_CARBS, Modes.beforeWorkout(110))
        assertNull(Modes.beforeWorkout(150))
        assertEquals(Modes.BeforeWorkout.KETONES_FIRST, Modes.beforeWorkout(290))
        assertEquals(Modes.KetoneBand.NORMAL, Modes.ketoneBand(0.4))
        assertEquals(Modes.KetoneBand.HIGH, Modes.ketoneBand(1.8))
        assertEquals(Modes.KetoneBand.DANGER, Modes.ketoneBand(3.0))
        assertEquals(60, Modes.alarmSettings(AlarmSettings(highSnoozeMinutes = 120), active("12:00", sick = at("08:00"))).highSnoozeMinutes)
    }
}
