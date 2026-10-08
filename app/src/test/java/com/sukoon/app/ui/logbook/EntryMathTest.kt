package com.sukoon.app.ui.logbook

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryMathTest {

    private val t0 = Instant.parse("2026-10-04T12:00:00Z")
    private fun at(minutes: Long) = t0.plusSeconds(minutes * 60).toEpochMilli()
    private fun event(id: Long, type: LogEventType, minutes: Long, value: Double? = null, note: String? = null, source: String? = null) =
        EventEntity(id = id, timestampMillis = at(minutes), type = type.name, value = value, note = note, source = source)
    private fun r(minutes: Long, mgDl: Int) = GlucoseReading(t0.plusSeconds(minutes * 60), mgDl, TrendDirection.STEADY, SourceKind.LIBRE_BLE)

    @Test
    fun `keypad keeps amounts sane`() {
        assertEquals("4", Keypad.press("0", "4", decimals = true, maxWhole = 2))
        assertEquals("4.", Keypad.press("4", Keypad.POINT, decimals = true, maxWhole = 2))
        assertEquals("4.5", Keypad.press("4.", "5", decimals = true, maxWhole = 2))
        assertEquals("4.5", Keypad.press("4.5", "5", decimals = true, maxWhole = 2)) // one decimal place
        assertEquals("45", Keypad.press("45", Keypad.POINT, decimals = false, maxWhole = 3)) // grams are whole
        assertEquals("450", Keypad.press("450", "1", decimals = false, maxWhole = 3))
        assertEquals("0.", Keypad.press("", Keypad.POINT, decimals = true, maxWhole = 2))
        assertEquals("4", Keypad.press("45", Keypad.DELETE, decimals = false, maxWhole = 3))
    }

    @Test
    fun `each rapid dose sits under the nearest meal in its window`() {
        val events = listOf(
            event(5, LogEventType.INSULIN, 250, 2.0), // 4 h after lunch: on its own
            event(4, LogEventType.CARB, 200, 60.0), // lunch
            event(3, LogEventType.INSULIN, 190, 4.0), // 10 min before lunch
            event(2, LogEventType.BASAL, 5, 18.0), // long-acting is never nested
            event(1, LogEventType.INSULIN, 0, 3.0), // with breakfast
            event(0, LogEventType.CARB, 0, 45.0), // breakfast
        )
        val groups = groupEntries(events)
        assertEquals(listOf(5L, 4L, 2L, 0L), groups.map { it.main.id })
        assertEquals(listOf(3L), groups[1].insulin.map { it.id })
        assertEquals(listOf(1L), groups[3].insulin.map { it.id })
    }

    @Test
    fun `a meal's response - start, peak, back in range`() {
        val readings = listOf(r(-5, 120), r(30, 150), r(60, 182), r(85, 170), r(120, 140))
        val response = mealResponse(readings, t0, t0.plusSeconds(5 * 3600))!!
        assertEquals(120, response.start)
        assertEquals(182, response.peak)
        assertEquals(t0.plusSeconds(85 * 60), response.backInRangeAt)
        assertTrue(!response.stillRising)
        assertNull(mealResponse(listOf(r(-40, 120), r(30, 150)), t0, t0)) // no reading at the meal
    }

    @Test
    fun `repeats are recent, Sukoon's own, and distinct`() {
        val events = listOf(
            event(4, LogEventType.INSULIN, 40, 4.0, "Apidra"),
            event(3, LogEventType.CARB, 30, 62.0, null, source = "com.myfitnesspal.android"),
            event(2, LogEventType.INSULIN, 20, 4.0, "Apidra"),
            event(1, LogEventType.FINGERSTICK, 10, 121.0),
            event(0, LogEventType.CARB, 0, 45.0, "Breakfast"),
        )
        assertEquals(listOf(4L, 0L), recentRepeats(events).map { it.id })
        assertEquals(107.0 to 8.0, todayTotals(events, t0, ZoneOffset.UTC))
    }

    @Test
    fun `a dose linked to a meal stays with it when two meals share a time`() {
        val ten = java.time.Instant.parse("2026-10-08T10:00:00Z").toEpochMilli()
        val first = com.sukoon.app.data.db.EventEntity(id = 1, timestampMillis = ten, type = "CARB", value = 109.0, source = "com.myfitnesspal.android")
        val second = com.sukoon.app.data.db.EventEntity(id = 2, timestampMillis = ten, type = "CARB", value = 213.0, source = "com.myfitnesspal.android")
        // Taken at 13:53 for the second meal: far from 10:00, and both meals sit there.
        val dose = com.sukoon.app.data.db.EventEntity(id = 3, timestampMillis = ten + 233 * 60_000L, type = "INSULIN", value = 12.0, mealId = 2)
        val groups = groupEntries(listOf(dose, second, first))
        org.junit.Assert.assertEquals(listOf(dose), groups.single { it.main.id == 2L }.insulin)
        org.junit.Assert.assertTrue(groups.single { it.main.id == 1L }.insulin.isEmpty())
        org.junit.Assert.assertTrue(groups.none { it.main.id == 3L }) // shown under its meal, not on its own
    }
}
