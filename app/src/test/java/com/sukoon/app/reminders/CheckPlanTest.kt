package com.sukoon.app.reminders

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.insights.MealSlot
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckPlanTest {
    private val zone = ZoneOffset.UTC
    // Thursday: a WEEK day.
    private fun t(hhmm: String, day: String = "2026-10-29") = Instant.parse("${day}T$hhmm:00Z")
    private var id = 1L
    private fun meal(at: Instant, grams: Double = 60.0, note: String? = null) = EventEntity(id = id++, timestampMillis = at.toEpochMilli(), type = LogEventType.CARB.name, value = grams, note = note)
    private fun prick(at: Instant, mgDl: Int) = EventEntity(id = id++, timestampMillis = at.toEpochMilli(), type = LogEventType.FINGERSTICK.name, value = mgDl.toDouble())
    private val lunch = mapOf("WEEK:LUNCH" to 14 * 60 + 30)

    private fun plan(now: Instant, events: List<EventEntity> = emptyList(), fp: Boolean = true, settings: CheckSettings = CheckSettings(bedtime = false), sent: Map<String, Instant> = emptyMap()) =
        CheckPlan.plan(now, zone, events, fp, settings, lunch, sent)

    @Test
    fun `meal times - the median of 3 or more, weekdays and Fri-Sat apart, yours win`() {
        val events = listOf(t("14:20", "2026-10-26"), t("14:40", "2026-10-27"), t("14:30", "2026-10-28"), t("16:00", "2026-10-24"))
            .map { meal(it) } + meal(t("08:00", "2026-10-28"))
        val learned = MealTimes.learned(events, t("17:00"), zone)
        assertEquals(mapOf("WEEK:LUNCH" to 14 * 60 + 30), learned)
        assertEquals(mapOf(MealSlot.LUNCH to 13 * 60), MealTimes.times(DayKind.WEEK, learned, mapOf("WEEK:LUNCH" to 13 * 60)))
        assertEquals(emptyMap<MealSlot, Int>(), MealTimes.times(DayKind.WEEKEND, learned, emptyMap()))
    }

    @Test
    fun `finger-prick mode - a check 15 min before the meal and a question 20 min after if nothing's logged`() {
        val due = plan(t("14:00"))
        assertEquals(listOf(CheckKind.BEFORE_MEAL to t("14:15"), CheckKind.MEAL_MISSING to t("14:50")), due.map { it.kind to it.at })
    }

    @Test
    fun `with a sensor only the meal question`() {
        assertEquals(listOf(CheckKind.MEAL_MISSING), plan(t("14:00"), fp = false).map { it.kind })
    }

    @Test
    fun `a logged meal ends both and brings its 2-hour check`() {
        val due = plan(t("14:10"), listOf(meal(t("14:05"))))
        assertEquals(listOf(CheckKind.AFTER_MEAL to t("16:05")), due.map { it.kind to it.at })
    }

    @Test
    fun `a prick near the check answers it, and one 90 min after a meal answers the 2-hour one`() {
        assertTrue(plan(t("14:12"), listOf(prick(t("14:10"), 120))).none { it.kind == CheckKind.BEFORE_MEAL })
        assertTrue(plan(t("15:40"), listOf(meal(t("14:00")), prick(t("15:35"), 150))).none { it.kind == CheckKind.AFTER_MEAL })
    }

    @Test
    fun `after a low - every 15 min until a prick reads 70, 4 times at most`() {
        val low = prick(t("15:00"), 60)
        val first = plan(t("15:05"), listOf(low)).single { it.kind == CheckKind.AFTER_LOW }
        assertEquals(t("15:15"), first.at)
        val second = plan(t("15:16"), listOf(low), sent = mapOf(first.key to t("15:15"))).single { it.kind == CheckKind.AFTER_LOW }
        assertEquals(t("15:30"), second.at)
        assertTrue(plan(t("15:21"), listOf(low, prick(t("15:20"), 95))).none { it.kind == CheckKind.AFTER_LOW })
        val asked = (0..3).associate { "low:${t("15:00").toEpochMilli()}:$it" to t("15:15") }
        assertTrue(plan(t("16:05"), listOf(low), sent = asked).none { it.kind == CheckKind.AFTER_LOW })
    }

    @Test
    fun `the daily cap stops finger-prick checks, not the low rechecks or the meal question`() {
        val sent = (1..3).associate { "bed:x$it" to t("09:00") }
        val due = plan(t("14:00"), listOf(prick(t("13:55"), 60)), settings = CheckSettings(bedtime = false, cap = 3), sent = sent)
        assertEquals(setOf(CheckKind.AFTER_LOW, CheckKind.MEAL_MISSING), due.map { it.kind }.toSet())
    }

    @Test
    fun `sent once, never again - missed by 45 min, dropped`() {
        assertTrue(plan(t("14:16"), sent = mapOf("pre:2026-10-29:LUNCH" to t("14:15"))).none { it.kind == CheckKind.BEFORE_MEAL })
        assertTrue(plan(t("15:01")).none { it.kind == CheckKind.BEFORE_MEAL })
        assertFalse(plan(t("15:01")).none { it.kind == CheckKind.MEAL_MISSING })
    }

    @Test
    fun `bedtime check in finger-prick mode`() {
        val due = plan(t("23:00"), settings = CheckSettings(bedtimeMinute = 23 * 60 + 30))
        assertEquals(t("23:30"), due.first { it.kind == CheckKind.BEDTIME }.at)
    }

    @Test
    fun `the usual - the meal eaten most at that time`() {
        val events = listOf(
            meal(t("14:00", "2026-10-20"), 55.0, "Ful + bread"), meal(t("14:10", "2026-10-21"), 65.0, "ful + bread"), meal(t("14:30", "2026-10-22"), 60.0, "Ful + bread"),
            meal(t("14:00", "2026-10-23"), 90.0, "Koshari"), meal(t("14:00", "2026-10-24"), 90.0, "Koshari"),
            meal(t("08:00", "2026-10-24"), 30.0, "Eggs"), meal(t("08:00", "2026-10-25"), 30.0, "Eggs"), meal(t("08:00", "2026-10-26"), 30.0, "Eggs"),
        )
        assertEquals(UsualMeal("Ful + bread", 60.0), MealTimes.usual(events, MealSlot.LUNCH, t("17:00"), zone))
        assertNull(MealTimes.usual(events, MealSlot.DINNER, t("17:00"), zone))
    }
}
