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
    private val log: AlarmLog,
    /** Survives restarts: snoozes, answers, "treated at" and where an emergency stands. */
    private val store: AlarmStore? = null,
    private val emergency: EmergencyAlerts,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
    /** Re-arms [SignalWatchdog] (wake Sukoon after this long without a reading). */
    private val watchdog: (Duration) -> Unit = {},
) {
    private val mutex = Mutex()
    private val restored = store?.load()
    private var state = restored?.state ?: AlarmState()
    private val recent = TreeMap<Instant, GlucoseReading>()

    /** Newest reading however old: losing the signal after a low is escalated long after the 30-min window empties. */
    private var newest: GlucoseReading? = null

    private val _escalation = MutableStateFlow(restored?.phase ?: EscalationPhase.Idle)

    /** For the alert screen: idle, counting down to texting the contacts, or texts sent. */
    val escalation: StateFlow<EscalationPhase> = _escalation.asStateFlow()

    private val _active = MutableStateFlow<Set<AlarmType>>(emptySet())

    /** Alarms going on right now (snoozed ones too): the alert screen closes itself once its alarm is over. */
    val active: StateFlow<Set<AlarmType>> = _active.asStateFlow()

    private val _treatedAt = MutableStateFlow(restored?.treatedAt)

    /** When a low was last marked treated: Home counts the 15-15 rule's minutes from it. */
    val treatedAt: StateFlow<Instant?> = _treatedAt.asStateFlow()

    fun start() {
        scope.launch {
            repository.readingsSince(System.currentTimeMillis() - WINDOW.toMillis()).first().forEach(::keep)
            // However old: after a restart with the sensor quiet for an hour, "no readings" must still go off.
            repository.latestReading.first()?.let(::keep)
            val minuteTicks = flow {
                while (true) {
                    emit(null)
                    delay(60_000)
                }
            }
            merge(repository.liveReadings.map<GlucoseReading, GlucoseReading?> { it }, minuteTicks).collect { reading ->
                mutex.withLock {
                    if (reading != null) {
                        keep(reading)
                        if (enabled()) watchdog(Duration.ofMinutes(settings.alarmSettings.signalLossMinutes + WATCHDOG_SLACK_MINUTES))
                    }
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
        val kind = when {
            treated -> AlarmLog.Kind.TREATED
            minutes > 0 -> AlarmLog.Kind.SNOOZED
            else -> AlarmLog.Kind.DISMISSED
        }
        log.add(AlarmLog.Entry(Instant.now(), type, kind, minutes = minutes))
        notifier.stopSound()
        mutex.withLock {
            val now = Instant.now()
            state = AlarmEngine.acknowledge(state, type, now)
            if (minutes > 0) state = AlarmEngine.snooze(state, type, minutes, now)
            remember()
            if (enabled()) escalateLocked(now)
        }
        if (minutes > 0) notifier.cancel(type)
    }

    /**
     * Home's "I've treated it": answers whichever low is going off (an urgent low checks back in 5
     * minutes, a low after its snooze) and starts the 15 minutes Home counts down. Works with no
     * alarm going off too: the countdown is the point.
     */
    suspend fun treated() {
        val lows = _active.value.filter { it == AlarmType.URGENT_LOW || it == AlarmType.LOW || it == AlarmType.GOING_LOW }.ifEmpty { listOf(AlarmType.LOW) }
        lows.forEach { acknowledge(it, if (it == AlarmType.URGENT_LOW) 5 else settings.alarmSettings.lowSnoozeMinutes, treated = true) }
    }

    /** Answering a test alarm: stop it without touching a real alarm's state. */
    fun endTest(type: AlarmType) = notifier.cancel(type)

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

    /** You → Alarms → See and hear: [type]'s real path (sound, notification, full screen) with a sample value, marked as a test. */
    fun test(type: AlarmType) = notifier.show(Alert(type, TEST_VALUES[type], 25), settings.alarmSettings, test = true)

    /** You → Alarms → a sound's "Play it". */
    fun preview(type: AlarmType) = notifier.preview(type, settings.alarmSettings)

    /** A pack's low-alarm sound, before choosing it (at the volume a low alarm plays). */
    fun previewPack(pack: SoundPack) = notifier.preview(AlarmType.LOW, settings.alarmSettings.let { it.copy(soundPack = pack, sounds = it.sounds - AlarmType.LOW) })

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
            _active.value = emptySet()
            if (_escalation.value !is EscalationPhase.Idle) notifier.cancelCountdown()
            _escalation.value = EscalationPhase.Idle
            return
        }
        // The newest reading always counts, however old: the 30-minute window alone would forget it and end
        // "no readings" (and its emergency escalation) half an hour after the sensor went quiet.
        val result = AlarmEngine.evaluate((recent.values + listOfNotNull(newest)).distinct(), now, settings.alarmSettings, state)
        state = result.state
        _active.value = state.activeSince.keys // before showing: the alert screen checks it as it opens
        result.cleared.forEach {
            notifier.cancel(it)
            log.add(AlarmLog.Entry(now, it, AlarmLog.Kind.RESOLVED))
        }
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
        remember()
    }

    /** Keep the alarms' state for a restart (written only when it changed). */
    private fun remember() {
        store?.save(AlarmStore.Snapshot(state, _treatedAt.value, _escalation.value))
    }

    private companion object {
        val WINDOW: Duration = Duration.ofMinutes(30)
        const val WATCHDOG_SLACK_MINUTES = 2L
        val TEST_VALUES = mapOf(AlarmType.URGENT_LOW to 52, AlarmType.LOW to 64, AlarmType.GOING_LOW to 82, AlarmType.HIGH to 262)
    }
}
