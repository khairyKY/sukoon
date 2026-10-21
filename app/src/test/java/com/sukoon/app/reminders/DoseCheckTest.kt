package com.sukoon.app.reminders

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseCheckTest {
    private val now = Instant.parse("2026-10-21T14:00:00Z")
    private fun ago(min: Long) = now.minusSeconds(min * 60)
    private fun dose(minAgo: Long, mealId: Long? = null) = EventEntity(id = 3, timestampMillis = ago(minAgo).toEpochMilli(), type = LogEventType.INSULIN.name, value = 6.0, mealId = mealId)
    private fun meal(minAgo: Long, g: Double = 50.0) = EventEntity(timestampMillis = ago(minAgo).toEpochMilli(), type = LogEventType.CARB.name, value = g)
    /** Readings every 5 minutes from [from] min ago to now, going from [start] to [end]. */
    private fun line(from: Long, start: Int, end: Int) = (from downTo 0 step 5).map { m ->
        val f = (from - m).toDouble() / from
        GlucoseReading(ago(m), (start + (end - start) * f).toInt(), TrendDirection.STEADY, SourceKind.LIBRE_BLE)
    }

    @Test
    fun `a dose when not high with no food asks did you eat`() {
        assertTrue(DoseCheck.noFood(dose(30), listOf(dose(30)), line(60, 130, 110), now))
        assertFalse(DoseCheck.noFood(dose(30), listOf(dose(30), meal(10)), line(60, 130, 110), now)) // food since
        assertFalse(DoseCheck.noFood(dose(30), listOf(dose(30), meal(80)), line(90, 130, 110), now)) // food just before
        assertFalse(DoseCheck.noFood(dose(30), listOf(dose(30)), line(60, 260, 240), now)) // high: a correction
        assertFalse(DoseCheck.noFood(dose(30, mealId = 9), emptyList(), line(60, 130, 110), now)) // a meal's own dose
    }

    @Test
    fun `two hours after a correction says how it went`() {
        assertEquals(DoseCheck.Outcome.COMING_DOWN, DoseCheck.correctionOutcome(dose(120), listOf(dose(120)), line(130, 260, 165), now))
        assertEquals(DoseCheck.Outcome.STILL_HIGH, DoseCheck.correctionOutcome(dose(120), listOf(dose(120)), line(130, 260, 245), now))
        assertEquals(DoseCheck.Outcome.DROPPING, DoseCheck.correctionOutcome(dose(120), listOf(dose(120)), line(130, 260, 100), now))
        assertNull(DoseCheck.correctionOutcome(dose(120), listOf(dose(120)), line(130, 150, 120), now)) // wasn't high: not a correction
        assertNull(DoseCheck.correctionOutcome(dose(120), listOf(dose(120), meal(100)), line(130, 260, 165), now)) // food with it
    }
}
