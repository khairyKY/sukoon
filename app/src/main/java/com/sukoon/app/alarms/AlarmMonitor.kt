package com.sukoon.app.alarms

import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.source.GlucoseReading
import java.time.Duration
import java.time.Instant
import java.util.TreeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs [AlarmEngine] against every *live* reading (before the save-interval filter, so saving
 * every 15 min never delays an alarm) and once a minute for signal loss. Only while [enabled]
 * (the real sensor is the source — demo data must never wake anyone up).
 *
 * ponytail: alarm state lives in memory; a process restart forgets snoozes and re-announces an
 * ongoing episode once. Persist AlarmState if that proves annoying.
 */
class AlarmMonitor(
    private val repository: GlucoseRepository,
    private val settings: SettingsPrefs,
    private val notifier: AlarmNotifier,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
) {
    private val mutex = Mutex()
    private var state = AlarmState()
    private val recent = TreeMap<Instant, GlucoseReading>()

    fun start() {
        scope.launch {
            repository.readingsSince(System.currentTimeMillis() - WINDOW.toMillis()).first().forEach { recent[it.timestamp] = it }
            val minuteTicks = flow {
                while (true) {
                    emit(null)
                    delay(60_000)
                }
            }
            merge(repository.liveReadings.map<GlucoseReading, GlucoseReading?> { it }, minuteTicks).collect { reading ->
                mutex.withLock {
                    if (reading != null) recent[reading.timestamp] = reading
                    evaluateLocked()
                }
            }
        }
    }

    /** A notification button / alert screen / Home button: stop the sound and (if minutes > 0) snooze. */
    suspend fun acknowledge(type: AlarmType, minutes: Int) {
        notifier.stopSound()
        if (minutes > 0) {
            mutex.withLock { state = AlarmEngine.snooze(state, type, minutes, Instant.now()) }
            notifier.cancel(type)
        }
    }

    /** You → Alarms → Test: the real urgent-low path (sound, notification, full screen), marked as a test. */
    fun test() = notifier.show(Alert(AlarmType.URGENT_LOW, 52, 0), settings.alarmSettings, test = true)

    /** You → Alarms → a sound's "Play it". */
    fun preview(type: AlarmType) = notifier.preview(type, settings.alarmSettings)

    private fun evaluateLocked() {
        val now = Instant.now()
        recent.headMap(now.minus(WINDOW)).clear()
        if (!enabled()) {
            state.activeSince.keys.forEach(notifier::cancel)
            state = AlarmState()
            return
        }
        val result = AlarmEngine.evaluate(recent.values.toList(), now, settings.alarmSettings, state)
        state = result.state
        result.cleared.forEach(notifier::cancel)
        result.fire.forEach { notifier.show(it, settings.alarmSettings) }
    }

    private companion object {
        val WINDOW: Duration = Duration.ofMinutes(30)
    }
}
