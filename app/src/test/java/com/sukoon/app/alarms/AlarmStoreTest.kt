package com.sukoon.app.alarms

import com.sukoon.app.emergency.EscalationPhase
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmStoreTest {

    private val t = Instant.parse("2026-10-07T03:00:00Z")

    @Test
    fun `snoozes, answers, treated-at and texts-sent survive a restart`() {
        val state = AlarmState(
            activeSince = mapOf(AlarmType.LOW to t),
            lastAlertAt = mapOf(AlarmType.LOW to t),
            snoozedUntil = mapOf(AlarmType.LOW to t.plusSeconds(900)),
            acknowledgedAt = mapOf(AlarmType.LOW to t.plusSeconds(30)),
        )
        val sent = EscalationPhase.Sent(AlarmType.URGENT_LOW, t.plusSeconds(600), listOf("Mum", "Dad"))
        val back = AlarmStore.decode(AlarmStore.encode(AlarmStore.Snapshot(state, t.plusSeconds(60), sent)))!!
        assertEquals(state, back.state)
        assertEquals(t.plusSeconds(60), back.treatedAt)
        assertEquals(sent, back.phase)
    }

    @Test
    fun `a countdown cut off by the restart comes back idle, to start afresh`() {
        val back = AlarmStore.decode(AlarmStore.encode(AlarmStore.Snapshot(AlarmState(), null, EscalationPhase.Countdown(AlarmType.URGENT_LOW, t))))!!
        assertEquals(EscalationPhase.Idle, back.phase)
        assertNull(back.treatedAt)
        assertNull(AlarmStore.decode("not json"))
    }
}
