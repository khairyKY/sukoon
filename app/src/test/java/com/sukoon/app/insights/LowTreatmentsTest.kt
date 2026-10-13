package com.sukoon.app.insights

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LowTreatmentsTest {
    private val t0 = Instant.parse("2026-10-10T12:00:00Z")
    private fun at(hours: Double) = t0.plusSeconds((hours * 3600).toLong())
    private fun treat(hours: Double, grams: Double, treatment: Boolean = true) =
        EventEntity(timestampMillis = at(hours).toEpochMilli(), type = LogEventType.CARB.name, value = grams, treatment = treatment)
    private fun r(hours: Double, mgDl: Int) = GlucoseReading(at(hours), mgDl, TrendDirection.STEADY, SourceKind.LIBRE_BLE)

    @Test
    fun `the usual amount, second rounds and rebounds`() {
        val events = listOf(
            treat(0.0, 30.0), treat(0.25, 15.0), // a low that needed a second round
            treat(24.0, 30.0),
            treat(48.0, 15.0),
            treat(60.0, 80.0, treatment = false), // a meal: not counted
        )
        val readings = listOf(r(1.0, 210), r(25.0, 150), r(49.0, 190)) // rebounds after the first and third
        val i = InsightEngine.treatments(events, readings)!!
        assertEquals(3, i.lows)
        assertEquals(30, i.medianGrams) // 15, 15, 30, 30 → the middle
        assertEquals(1, i.secondRounds)
        assertEquals(2, i.rebounds)
    }

    @Test
    fun `nothing to say under three treatments`() {
        assertNull(InsightEngine.treatments(listOf(treat(0.0, 15.0), treat(24.0, 15.0)), emptyList()))
    }
}
