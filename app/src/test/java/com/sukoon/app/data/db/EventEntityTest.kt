package com.sukoon.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class EventEntityTest {

    @Test
    fun `logType parses a known type name`() {
        val event = EventEntity(timestampMillis = 0, type = "ACTIVITY", value = 20.0)
        assertEquals(LogEventType.ACTIVITY, event.logType)
    }

    @Test
    fun `unrecognized type string falls back to NOTE instead of crashing`() {
        // A row a future/renamed build might leave behind — must not blow up the Logbook Flow.
        val corrupt = EventEntity(timestampMillis = 0, type = "SOME_FUTURE_TYPE")
        assertEquals(LogEventType.NOTE, corrupt.logType)
    }
}
