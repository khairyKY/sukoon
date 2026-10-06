package com.sukoon.app.ui.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.insulin.InsulinAction
import com.sukoon.app.ui.widget.GlucoseWidget.Companion.toWidgetOptions
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetExtrasTest {

    private val now = Instant.parse("2026-10-07T13:00:00Z")
    private fun at(hoursAgo: Double, type: String, value: Double, kcal: Double? = null) =
        EventEntity(timestampMillis = now.minus(Duration.ofMinutes((hoursAgo * 60).toLong())).toEpochMilli(), type = type, value = value, kcal = kcal)

    @Test
    fun `today's carbs and calories, the last doses, and long-acting taken in the last 12 hours`() {
        val events = listOf(
            at(20.0, "CARB", 90.0, kcal = 800.0), // yesterday: not today
            at(5.0, "CARB", 40.0, kcal = 400.0),
            at(1.0, "CARB", 60.0, kcal = 600.0),
            at(1.0, "INSULIN", 5.0),
            at(15.0, "BASAL", 20.0), // 15 h ago: not today's dose
        )
        val x = WidgetExtras.build(events, now, ZoneOffset.UTC, InsulinAction())
        assertEquals(100.0, x.carbs, 0.0)
        assertEquals(1000.0, x.kcal!!, 0.0)
        assertEquals(5.0, x.lastRapidUnits!!, 0.0)
        assertTrue(x.iob > 0)
        assertNull(x.longTakenAt)
        assertEquals(20.0, x.lastLongDose!!, 0.0)
        assertTrue(WidgetExtras.build(events + at(2.0, "BASAL", 22.0), now, ZoneOffset.UTC, InsulinAction()).longTakenAt != null)
    }

    @Test
    fun `a widget from before styles keeps its look, a styled one keeps its choices`() {
        val old = preferencesOf(intPreferencesKey("graph_hours") to 0, booleanPreferencesKey("show_details") to false).toWidgetOptions()
        assertEquals(WidgetStyle.CARD, old.style)
        assertFalse(old.graphShown)
        assertFalse(old.showDetails)
        val styled = preferencesOf(stringPreferencesKey("style") to "TODAY", stringSetPreferencesKey("info") to setOf("CARBS", "LONG", "ARROW")).toWidgetOptions()
        assertEquals(WidgetStyle.TODAY, styled.style)
        assertTrue(styled.shows(WidgetInfo.LONG))
        assertFalse(styled.shows(WidgetInfo.ARROW)) // not a Today option
    }
}
