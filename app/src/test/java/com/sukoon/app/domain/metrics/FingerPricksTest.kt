package com.sukoon.app.domain.metrics

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerPricksTest {
    private val now = Instant.parse("2026-10-29T17:00:00Z")
    private fun ago(minutes: Long) = now.minusSeconds(minutes * 60)
    private fun reading(minutesAgo: Long, mgDl: Int = 120) = GlucoseReading(ago(minutesAgo), mgDl, TrendDirection.RISING, SourceKind.LIBRE_BLE)

    @Test
    fun `a fresh sensor reading wins over a prick`() {
        val c = FingerPricks.current(reading(3), listOf(FingerPricks.Prick(ago(1), 200)), now)!!
        assertEquals(120, c.mgDl)
        assertEquals(TrendDirection.RISING, c.trend)
    }

    @Test
    fun `no fresh reading - a prick under 15 min counts, with no arrow`() {
        val c = FingerPricks.current(reading(40), listOf(FingerPricks.Prick(ago(20), 90), FingerPricks.Prick(ago(14), 214)), now)!!
        assertEquals(214, c.mgDl)
        assertNull(c.trend)
        assertTrue(c.prick)
    }

    @Test
    fun `an old prick, a warm-up reading or nothing gives nothing`() {
        assertNull(FingerPricks.current(null, listOf(FingerPricks.Prick(ago(16), 150)), now))
        assertNull(FingerPricks.current(reading(2).copy(warmup = true), emptyList(), now))
        assertNull(FingerPricks.current(null, emptyList(), now))
    }

    @Test
    fun `pricks come from finger-prick entries only, oldest first`() {
        val events = listOf(
            EventEntity(timestampMillis = ago(5).toEpochMilli(), type = LogEventType.FINGERSTICK.name, value = 101.6, site = "LEFT_MIDDLE"),
            EventEntity(timestampMillis = ago(60).toEpochMilli(), type = LogEventType.FINGERSTICK.name, value = 88.0),
            EventEntity(timestampMillis = ago(10).toEpochMilli(), type = LogEventType.CARB.name, value = 40.0),
        )
        assertEquals(listOf(FingerPricks.Prick(ago(60), 88), FingerPricks.Prick(ago(5), 102, "LEFT_MIDDLE")), FingerPricks.of(events))
    }
}
