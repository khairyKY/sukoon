package com.sukoon.app.ai

import com.sukoon.app.data.db.LogEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLogTest {
    @Test
    fun `entries come out typed, rounded and range-checked`() {
        val json = """```json
            {"entries": [
              {"type": "carbs", "amount": 84.6, "name": "كشري", "estimated": true},
              {"type": "rapid", "amount": 6.3},
              {"type": "long", "amount": 20},
              {"type": "activity", "amount": 60, "activity": "STRENGTH", "effort": "HARD"},
              {"type": "carbs", "amount": 15, "name": "juice", "treatment": true},
              {"type": "rapid", "amount": null},
              {"type": "rapid", "amount": 400},
              {"type": "note", "name": "felt shaky"}
            ]}
        ```"""
        val e = VoiceLog.parse(json)
        assertEquals(listOf(LogEventType.CARB, LogEventType.INSULIN, LogEventType.BASAL, LogEventType.ACTIVITY, LogEventType.CARB, LogEventType.NOTE), e.map { it.type })
        assertEquals(85.0, e[0].amount!!, 1e-9)
        assertTrue(e[0].estimated)
        assertEquals(6.5, e[1].amount!!, 1e-9) // to the half unit
        assertEquals("STRENGTH", e[3].name)
        assertEquals("HARD", e[3].effort)
        assertTrue(e[4].treatment)
        // insulin without a number, or an impossible one, never becomes an entry
        assertEquals(1, e.count { it.type == LogEventType.INSULIN })
    }
}
