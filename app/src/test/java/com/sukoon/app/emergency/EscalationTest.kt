package com.sukoon.app.emergency

import com.sukoon.app.alarms.AlarmState
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EscalationTest {

    private val t0 = Instant.parse("2026-10-03T03:00:00Z")
    private fun at(minutes: Long, seconds: Long = 0) = t0.plusSeconds(minutes * 60 + seconds)
    private fun reading(minute: Long, mgDl: Int) = GlucoseReading(at(minute), mgDl, TrendDirection.FALLING, SourceKind.LIBRE_BLE)
    private val urgent = AlarmState(activeSince = mapOf(AlarmType.URGENT_LOW to t0))

    @Test
    fun `urgent low unanswered for the chosen minutes escalates`() {
        assertNull(Escalation.unanswered(urgent, reading(9, 48), at(9), afterMinutes = 10))
        assertEquals(AlarmType.URGENT_LOW, Escalation.unanswered(urgent, reading(10, 48), at(10), afterMinutes = 10))
    }

    @Test
    fun `any answer restarts the clock`() {
        val answered = urgent.copy(acknowledgedAt = mapOf(AlarmType.URGENT_LOW to at(6)))
        assertNull(Escalation.unanswered(answered, reading(12, 48), at(12), afterMinutes = 10))
        assertEquals(AlarmType.URGENT_LOW, Escalation.unanswered(answered, reading(16, 48), at(16), afterMinutes = 10))
    }

    @Test
    fun `losing the signal only escalates when it happened while low`() {
        val silent = AlarmState(activeSince = mapOf(AlarmType.SIGNAL_LOSS to t0))
        assertEquals(AlarmType.SIGNAL_LOSS, Escalation.unanswered(silent, reading(-20, 62), at(10), afterMinutes = 10))
        assertNull(Escalation.unanswered(silent, reading(-20, 120), at(10), afterMinutes = 10))
    }

    @Test
    fun `countdown, send, then a recovery text`() {
        var step = Escalation.step(EscalationPhase.Idle, AlarmType.URGENT_LOW, recovered = false, now = at(10))
        assertEquals(EscalationAction.START_COUNTDOWN, step.action)
        step = Escalation.step(step.phase, AlarmType.URGENT_LOW, recovered = false, now = at(10, 59))
        assertNull(step.action)
        step = Escalation.step(step.phase, AlarmType.URGENT_LOW, recovered = false, now = at(11))
        assertEquals(EscalationAction.SEND, step.action)
        step = Escalation.step(step.phase, AlarmType.URGENT_LOW, recovered = false, now = at(20))
        assertNull(step.action) // still low, already told them
        step = Escalation.step(step.phase, null, recovered = true, now = at(25))
        assertEquals(EscalationAction.SEND_RECOVERED, step.action)
        assertEquals(EscalationPhase.Idle, step.phase)
    }

    @Test
    fun `an answer during the countdown cancels it`() {
        val countdown = EscalationPhase.Countdown(AlarmType.URGENT_LOW, at(10))
        val step = Escalation.step(countdown, unanswered = null, recovered = false, now = at(10, 30))
        assertEquals(EscalationAction.CANCEL, step.action)
        assertEquals(EscalationPhase.Idle, step.phase)
    }

    @Test
    fun `still unanswered half an hour after the texts starts another countdown`() {
        val sent = EscalationPhase.Sent(AlarmType.URGENT_LOW, at(11), listOf("Mum"))
        assertNull(Escalation.step(sent, AlarmType.URGENT_LOW, recovered = false, now = at(40)).action)
        val again = Escalation.step(sent, AlarmType.URGENT_LOW, recovered = false, now = at(11).plus(Duration.ofMinutes(30)))
        assertEquals(EscalationAction.START_COUNTDOWN, again.action)
    }

    @Test
    fun `phone numbers become international`() {
        assertEquals("+201012345678", PhoneNumbers.normalize("010 1234 5678", "20"))
        assertEquals("+201012345678", PhoneNumbers.normalize("+20 101-234-5678", "20"))
        assertEquals("+201012345678", PhoneNumbers.normalize("00201012345678", "20"))
        assertEquals("201012345678", PhoneNumbers.whatsappDigits("+201012345678"))
        assertEquals("966", PhoneNumbers.callingCodeFor("SA"))
        assertEquals("20", PhoneNumbers.callingCodeFor(null))
    }

    @Test
    fun `contacts survive a round trip and corrupt storage reads as none`() {
        val contacts = listOf(EmergencyContact("Mum", "+201012345678"), EmergencyContact("Dad, \"Baba\"", "+201112345678"))
        assertEquals(contacts, EmergencySettings.contactsFromJson(EmergencySettings.contactsToJson(contacts)))
        assertEquals(emptyList<EmergencyContact>(), EmergencySettings.contactsFromJson("{not json"))
        assertEquals(emptyList<EmergencyContact>(), EmergencySettings.contactsFromJson(null))
    }
}
