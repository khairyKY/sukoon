package com.sukoon.app.ui.widget

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {

    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private fun at(minutesAgo: Long, mgDl: Int) =
        GlucoseReading(now.minusSeconds(minutesAgo * 60), mgDl, TrendDirection.RISING, SourceKind.LIBRE_BLE)

    @Test
    fun `layout follows the space - one cell to half the screen`() {
        assertEquals(WidgetLayout.TINY, layoutFor(70f, 70f)) // 1×1
        assertEquals(WidgetLayout.TINY, layoutFor(70f, 220f)) // 1×3
        assertEquals(WidgetLayout.TINY, layoutFor(140f, 70f)) // 2×1
        assertEquals(WidgetLayout.STRIP, layoutFor(300f, 70f)) // 4×1
        assertEquals(WidgetLayout.CARD, layoutFor(140f, 140f)) // 2×2
        assertEquals(WidgetLayout.CARD, layoutFor(380f, 420f)) // ~half a phone screen
    }

    @Test
    fun `model carries value, 5-minute change, minutes ago and today's time in range`() {
        // All five readings fall on the same UTC day.
        val readings = listOf(at(60, 250), at(30, 260), at(6, 140), at(5, 150), at(1, 158))
        val model = WidgetModel.build(readings, now, ZoneOffset.UTC, graphHours = 3)
        assertEquals(158, model.mgDl)
        assertEquals(1L, model.minutesAgo)
        assertEquals(18, model.delta) // vs the reading exactly 5 min before the latest (140)
        assertEquals(60, model.tirTodayPercent) // 3 of 5 in 70–180
        assertFalse(model.stale)
        assertEquals(5, model.graph.size)
    }

    @Test
    fun `old readings are stale, and the graph window honours the option`() {
        val model = WidgetModel.build(listOf(at(200, 120), at(15, 110)), now, ZoneOffset.UTC, graphHours = 1)
        assertTrue(model.stale) // 15 min old
        assertEquals(1, model.graph.size) // the 200-min-old point is outside 1 h
        assertTrue(WidgetModel.build(listOf(at(1, 100)), now, ZoneOffset.UTC, graphHours = 0).graph.isEmpty())
    }

    @Test
    fun `no readings yet is a stale empty model, not a crash`() {
        val model = WidgetModel.build(emptyList(), now, ZoneOffset.UTC, graphHours = 3)
        assertNull(model.mgDl)
        assertTrue(model.stale)
    }

    @Test
    fun `arrows distinguish all five trend buckets`() {
        assertEquals(5, TrendDirection.entries.map { it.arrow }.toSet().size)
    }
}
