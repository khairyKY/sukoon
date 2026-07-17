package com.sukoon.app.data.repository

import com.sukoon.app.data.db.ReadingEntity
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingMappersTest {

    @Test
    fun `round-trips through entity and back`() {
        // Milli-aligned so the epoch-millis storage round-trips exactly.
        val reading = GlucoseReading(
            timestamp = Instant.ofEpochMilli(1_784_000_000_000),
            glucoseMgDl = 137,
            trend = TrendDirection.RISING_FAST,
            source = SourceKind.SIMULATED,
        )

        val restored = reading.toEntity().toGlucoseReading()

        assertEquals(reading, restored)
    }

    @Test
    fun `entity carries enum names as strings`() {
        val entity = GlucoseReading(
            timestamp = Instant.ofEpochMilli(0),
            glucoseMgDl = 100,
            trend = TrendDirection.FALLING,
            source = SourceKind.LIBRE_BLE,
        ).toEntity()

        assertEquals("FALLING", entity.trend)
        assertEquals("LIBRE_BLE", entity.source)
    }

    @Test
    fun `unrecognized enum strings fall back to safe defaults instead of crashing`() {
        // A row a future/renamed build might leave behind — must not blow up the readings Flow.
        val corrupt = ReadingEntity(
            id = 1,
            timestampMillis = 0,
            glucoseMgDl = 100,
            trend = "SIDEWAYS_WOBBLE",
            source = "SOME_FUTURE_SOURCE",
        )

        val model = corrupt.toGlucoseReading()

        assertEquals(TrendDirection.STEADY, model.trend)
        assertEquals(SourceKind.SIMULATED, model.source)
    }
}
