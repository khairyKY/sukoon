package com.sukoon.app.alarms

import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.emergency.EmergencyAlerts
import com.sukoon.app.emergency.Escalation
import com.sukoon.app.emergency.EscalationAction
import com.sukoon.app.emergency.EscalationPhase
import com.sukoon.app.ui.home.HomeUiStateMapper
import java.time.Duration
import java.time.Instant
import java.util.TreeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs [AlarmEngine] against every *live* reading (before the save-interval filter, so saving
 * every 15 min never delays an alarm) and once a minute for signal loss, then the emergency
 * [Escalation] on top. Only while [enabled] (the real sensor is the source — demo data must
 * never wake anyone up, let alone text their family).
 *
 * ponytail: alarm and escalation state live in memory; a process restart forgets snoozes and
 * restarts the escalation clock. Persist them if that proves a problem.
 */
class AlarmMonitor(
    private val repository: GlucoseRepository,
    private val settings: SettingsPrefs,
    private val notifier: AlarmNotifier,
    private val emergency: EmergencyAlerts,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
) {
    private val mutex = Mutex()
    private var state = AlarmState()
    private val recent = TreeMap<Instant, GlucoseReading>()

    /** Newest reading however old: losing the signal after a low is escalated long after the 30-min window empties. */
    private var newest: GlucoseReading? = null

    private val _escalation = MutableStateFlow<EscalationPhase>(EscalationPhase.Idle)

    /** For the alert screen: idle, counting down to texting the contacts, or texts sent. */
    val escalation: StateFlow<EscalationPhase> = _escalation.asStateFlow()

    private val _treatedAt = MutableStateFlow<Instant?>(null)

    /** When a low was last marked treated: Home counts the 15-15 rule's minutes from it. */
    val treatedAt: StateFlow<Instant?> = _treatedAt.asStateFlow()

    fun start() {
        scope.launch {
            repository.readingsSince(System.currentTimeMillis() - WINDOW.toMillis()).first().forEach(::keep)
            val minuteTicks = flow {
                while (true) {
                    emit(null)
                    delay(60_000)
                }
            }
            merge(repository.liveReadings.map<GlucoseReading, GlucoseReading?> { it }, minuteTicks).collect { reading ->
                mutex.withLock {
                    if (reading != null) keep(reading)
                    evaluateLocked()
                }
            }
        }
    }

    /**
     * A notification button / alert screen / Home button / swipe-away: stop the sound, count it as
     * an answer (which stops an emergency countdown at once) and, if [minutes] > 0, snooze.
     * [treated]: the answer was "I'm treating it" (fast carbs taken), not just a snooze.
     */
    suspend fun acknowledge(type: AlarmType, minutes: Int, treated: Boolean = false) {
        if (treated) _treatedAt.value = Instant.now()
        notifier.stopSound()
        mutex.withLock {
            val now = Instant.now()
            state = AlarmEngine.acknowledge(state, type, now)
            if (minutes > 0) state = AlarmEngine.snooze(state, type, minutes, now)
            if (enabled()) escalateLocked(now)
        }
        if (minutes > 0) notifier.cancel(type)
    }

    /** "I'm OK": answers the escalated alarm; if the texts already went out, tells the contacts so they can stand down. */
    suspend fun imOk() {
        val phase = _escalation.value
        val type = when (phase) {
            is EscalationPhase.Countdown -> phase.type
            is EscalationPhase.Sent -> phase.type
            EscalationPhase.Idle -> return
        }
        acknowledge(type, if (type == AlarmType.URGENT_LOW) 5 else 30)
        if (phase is EscalationPhase.Sent) {
            mutex.withLock {
                emergency.textAll(emergency.respondedText(newest))
                _escalation.value = EscalationPhase.Idle
            }
        }
    }

    /** You → Alarms → Test: the real urgent-low path (sound, notification, full screen), marked as a test. */
    fun test() = notifier.show(Alert(AlarmType.URGENT_LOW, 52, 0), settings.alarmSettings, test = true)

    /** You → Alarms → a sound's "Play it". */
    fun preview(type: AlarmType) = notifier.preview(type, settings.alarmSettings)

    private fun keep(reading: GlucoseReading) {
        recent[reading.timestamp] = reading
        if (newest?.timestamp?.isBefore(reading.timestamp) != false) newest = reading
    }

    private fun evaluateLocked() {
        val now = Instant.now()
        recent.headMap(now.minus(WINDOW)).clear()
        if (!enabled()) {
            state.activeSince.keys.forEach(notifier::cancel)
            state = AlarmState()
            if (_escalation.value !is EscalationPhase.Idle) notifier.cancelCountdown()
            _escalation.value = EscalationPhase.Idle
            return
        }
        val result = AlarmEngine.evaluate(recent.values.toList(), now, settings.alarmSettings, state)
        state = result.state
        result.cleared.forEach(notifier::cancel)
        result.fire.forEach { notifier.show(it, settings.alarmSettings) }
        escalateLocked(now)
    }

    private fun escalateLocked(now: Instant) {
        val config = settings.emergency
        val last = newest
        val unanswered = if (config.contacts.isEmpty()) null else Escalation.unanswered(state, last, now, config.afterMinutes)
        val recovered = last != null && Duration.between(last.timestamp, now) <= HomeUiStateMapper.STALE_AFTER && last.glucoseMgDl >= Escalation.LOW_MG_DL
        val step = Escalation.step(_escalation.value, unanswered, recovered, now)
        var phase = step.phase
        when (step.action) {
            EscalationAction.START_COUNTDOWN -> {
                val endsAt = (phase as EscalationPhase.Countdown).endsAt
                notifier.keepAwake(Escalation.COUNTDOWN.toMillis() + 30_000)
                emergency.refreshLocation()
                notifier.showCountdown(endsAt, settings.alarmSettings)
                // Send on time rather than at the next reading or minute tick.
                scope.launch {
                    delay(Duration.between(Instant.now(), endsAt).toMillis().coerceAtLeast(0) + 500)
                    mutex.withLock { evaluateLocked() }
                }
            }
            EscalationAction.CANCEL -> notifier.cancelCountdown()
            EscalationAction.SEND -> {
                notifier.cancelCountdown()
                val sent = phase as EscalationPhase.Sent
                val minutes = Escalation.clockStart(state, sent.type)?.let { Duration.between(it, now).toMinutes() } ?: config.afterMinutes.toLong()
                val names = emergency.textAll(emergency.alertText(sent.type, last, minutes))
                emergency.callFirst()
                notifier.showEmergencySent(names, now)
                phase = sent.copy(to = names)
            }
            EscalationAction.SEND_RECOVERED -> last?.let { emergency.textAll(emergency.recoveredText(it)) }
            null -> Unit
        }
        _escalation.value = phase
    }

    private companion object {
        val WINDOW: Duration = Duration.ofMinutes(30)
    }
}
