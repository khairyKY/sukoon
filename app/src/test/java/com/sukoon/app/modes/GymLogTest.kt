package com.sukoon.app.modes

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GymLogTest {
    private fun t(hhmm: String) = Instant.parse("2026-10-29T$hhmm:00Z")
    private fun e(type: LogEventType, at: String, value: Double, note: String? = null) = EventEntity(timestampMillis = t(at).toEpochMilli(), type = type.name, value = value, note = note)

    @Test
    fun `the last workout as a ledger line`() {
        val events = listOf(
            e(LogEventType.CARB, "15:30", 60.0, "ful + bread"),
            e(LogEventType.INSULIN, "15:35", 6.0),
            e(LogEventType.ACTIVITY, "17:00", 55.0, "STRENGTH"),
        )
        val glucose = listOf(t("16:58") to 142, t("17:56") to 118, t("19:50") to 96, t("12:00") to 200)
        assertEquals(
            "2026-10-29 | strength 55 min | ful + bread 15:30 | 6u 15:35 |  | 142 / 118 / 96",
            GymLog.line(events, glucose, ZoneOffset.UTC),
        )
    }

    @Test
    fun `missing pieces are dashes, and no workout is no line`() {
        assertEquals("2026-10-29 | 30 min | — | — |  | — / — / —", GymLog.line(listOf(e(LogEventType.ACTIVITY, "08:00", 30.0)), emptyList(), ZoneOffset.UTC))
        assertNull(GymLog.line(listOf(e(LogEventType.CARB, "08:00", 30.0)), emptyList(), ZoneOffset.UTC))
    }
}
