package com.sukoon.app.insights

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.reminders.BasalReminder
import com.sukoon.app.reminders.BasalReminderSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BasalAndActivityTest {
    private val zone = ZoneOffset.UTC
    private fun r(t: Instant, v: Int) = GlucoseReading(t, v, TrendDirection.STEADY, SourceKind.LIBRE_BLE)
    private fun e(type: LogEventType, t: Instant, v: Double? = null, note: String? = null) = EventEntity(timestampMillis = t.toEpochMilli(), type = type.name, value = v, note = note)

    @Test
    fun `nights that rise without food or insulin read as a basal drift`() {
        val start = LocalDate.of(2026, 10, 1)
        val readings = (0L until 5L).flatMap { d ->
            val midnight = start.plusDays(d).atStartOfDay(zone).toInstant()
            // 22:00 to 06:30, every 5 min: 110 at midnight rising to 160 at 6
            (-24..78).map { i -> val t = midnight.plus(Duration.ofMinutes(5L * i)); r(t, 110 + (i.coerceAtLeast(0) * 50 / 72)) }
        }
        val basal = (0L until 6L).map { d -> e(LogEventType.BASAL, start.plusDays(d).minusDays(1).atStartOfDay(zone).plusHours(22).toInstant(), 20.0) }
        val drift = InsightEngine.basalDrift(readings, basal, zone)!!
        assertEquals(5, drift.nights)
        assertEquals(5, drift.drifting)
        assertEquals(com.sukoon.app.insights.Level.ATTENTION, drift.level)
        // A meal at 23:00 every night: no clean nights
        val meals = (0L until 5L).map { d -> e(LogEventType.CARB, start.plusDays(d).atStartOfDay(zone).minusHours(1).toInstant(), 40.0) }
        assertNull(InsightEngine.basalDrift(readings, basal + meals, zone))
    }

    @Test
    fun `an activity kind needs three entries and reads its change`() {
        val t0 = Instant.parse("2026-10-01T17:00:00Z")
        val acts = (0L until 3L).map { d -> e(LogEventType.ACTIVITY, t0.plus(Duration.ofDays(d)), 60.0, "Strength training · Hard") }
        val readings = acts.flatMap { a -> val t = Instant.ofEpochMilli(a.timestampMillis); listOf(r(t, 100), r(t.plus(Duration.ofMinutes(120)), 140)) }
        val fx = InsightEngine.activityEffects(readings, acts, zone)!!
        assertEquals("Strength training", fx.kinds.single().kind)
        assertEquals(40, fx.kinds.single().change)
        assertNull(InsightEngine.activityEffects(readings, acts.take(2), zone))
    }

    @Test
    fun `long-acting is due from its time, late from 3 h, gone once logged`() {
        val s = BasalReminderSettings(enabled = true, minuteOfDay = 22 * 60)
        val at = { h: Int, m: Int -> ZonedDateTime.of(2026, 10, 13, h, m, 0, 0, zone) }
        val (due, late) = BasalReminder.overdue(emptyList(), s, at(22, 30))!!
        assertEquals(at(22, 0).toInstant(), due)
        assertEquals(Duration.ofMinutes(30), late)
        assertEquals(Duration.ofHours(3), BasalReminder.overdue(emptyList(), s, at(22, 0).plusHours(3))!!.second)
        val logged = listOf(e(LogEventType.BASAL, at(22, 5).toInstant(), 20.0))
        assertNull(BasalReminder.overdue(logged, s, at(22, 30)))
        assertNotNull(BasalReminder.recent(logged, at(23, 0).toInstant()))
        assertNull(BasalReminder.recent(logged, at(22, 5).plusHours(9).toInstant()))
        assertNull(BasalReminder.overdue(emptyList(), s.copy(enabled = false), at(23, 0)))
    }
}
