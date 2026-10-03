package com.sukoon.app.alarms

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.ui.home.HomeUiStateMapper
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class AlarmType {
    URGENT_LOW, LOW, GOING_LOW, HIGH, SIGNAL_LOSS;

    /** Lows sound on the alarm stream (through silent mode and DND's "alarms"); highs and signal loss on the notification stream. */
    val loud: Boolean get() = this == URGENT_LOW || this == LOW || this == GOING_LOW
}

/** A chosen alarm sound: a content URI (a phone sound or the user's own file) and the name to show for it. */
data class AlarmSound(val uri: String, val name: String)

/** You → Alarms. Urgent low (< [URGENT_LOW_MG_DL]) is deliberately not configurable or switchable. */
data class AlarmSettings(
    val lowEnabled: Boolean = true,
    val lowMgDl: Int = 70,
    val goingLowEnabled: Boolean = true,
    val highEnabled: Boolean = true,
    val highMgDl: Int = 250,
    val signalLossEnabled: Boolean = true,
    val signalLossMinutes: Int = 20,
    val lowSnoozeMinutes: Int = 15,
    val highSnoozeMinutes: Int = 60,
    /** Per-alarm sound; absent = the phone's default for that kind of alert. */
    val sounds: Map<AlarmType, AlarmSound> = emptyMap(),
    /** Local hours with no high alarm (wrapping midnight); -1 = off. Lows are never quiet. */
    val quietHighsFrom: Int = -1,
    val quietHighsTo: Int = -1,
) {
    fun quietAt(hour: Int): Boolean {
        if (quietHighsFrom !in 0..23 || quietHighsTo !in 0..23 || quietHighsFrom == quietHighsTo) return false
        return if (quietHighsFrom < quietHighsTo) hour in quietHighsFrom until quietHighsTo else hour >= quietHighsFrom || hour < quietHighsTo
    }

    /** Clamp anything stored (or hand-edited) into safe ranges: a low alarm can't sit below urgent. */
    fun sanitized() = copy(
        lowMgDl = lowMgDl.coerceIn(URGENT_LOW_MG_DL + 5, 110),
        highMgDl = highMgDl.coerceIn(150, 400),
        signalLossMinutes = signalLossMinutes.coerceIn(10, 120),
        lowSnoozeMinutes = lowSnoozeMinutes.coerceIn(5, 60),
        highSnoozeMinutes = highSnoozeMinutes.coerceIn(15, 240),
        quietHighsFrom = quietHighsFrom.coerceIn(-1, 23),
        quietHighsTo = quietHighsTo.coerceIn(-1, 23),
    )

    companion object {
        const val URGENT_LOW_MG_DL = 55
    }
}

/** Per-alarm episode bookkeeping. Absent from [activeSince] = not currently alarming. */
data class AlarmState(
    val activeSince: Map<AlarmType, Instant> = emptyMap(),
    val lastAlertAt: Map<AlarmType, Instant> = emptyMap(),
    val snoozedUntil: Map<AlarmType, Instant> = emptyMap(),
    /** Last time someone answered each alarm (any button, or swiping it away): the emergency escalation's clock. */
    val acknowledgedAt: Map<AlarmType, Instant> = emptyMap(),
)

data class Alert(val type: AlarmType, val mgDl: Int?, val minutesSinceReading: Long?)

data class Evaluation(val state: AlarmState, val fire: List<Alert>, val cleared: Set<AlarmType>)

/**
 * Pure alarm rules (A5). Called on every live reading and once a minute; returns what to sound,
 * what has resolved, and the new state. Rules:
 * - Glucose alarms only judge a *fresh* reading (≤ 10 min old) — never an old one.
 * - Hysteresis: a low clears only 5 above its threshold and a high 10 below it, so a value
 *   hovering on the line doesn't flap on and off.
 * - Repeat while still true: urgent low every 5 min (snooze can't silence it longer), low after
 *   its snooze, high after its snooze, signal loss every 30 min, "going low soon" once per episode.
 * - Urgent low supersedes low; low supersedes going-low.
 */
object AlarmEngine {

    private val URGENT_REPEAT = Duration.ofMinutes(5)
    private val SIGNAL_REPEAT = Duration.ofMinutes(30)
    private val NEVER = Duration.ofDays(365)
    private const val LOW_HYSTERESIS = 5
    private const val HIGH_HYSTERESIS = 10
    private const val PROJECTION_MINUTES = 20.0

    /** [recent] = the last ~30 min of live readings, any order. */
    fun evaluate(recent: List<GlucoseReading>, now: Instant, settings: AlarmSettings, state: AlarmState, zone: ZoneId = ZoneId.systemDefault()): Evaluation {
        val latest = recent.maxByOrNull { it.timestamp }
        val age = latest?.let { Duration.between(it.timestamp, now) }
        val fresh = age != null && age <= HomeUiStateMapper.STALE_AFTER
        val v = latest?.glucoseMgDl
        fun was(type: AlarmType) = type in state.activeSince

        val active = mutableSetOf<AlarmType>()
        if (settings.signalLossEnabled && (age == null || age >= Duration.ofMinutes(settings.signalLossMinutes.toLong()))) {
            // No reading ever counts only once a source has produced one; a fresh install isn't "lost".
            if (latest != null) active += AlarmType.SIGNAL_LOSS
        }
        if (fresh && v != null) {
            val urgentLine = AlarmSettings.URGENT_LOW_MG_DL + if (was(AlarmType.URGENT_LOW)) LOW_HYSTERESIS else 0
            val lowLine = settings.lowMgDl + if (was(AlarmType.LOW)) LOW_HYSTERESIS else 0
            val highLine = settings.highMgDl - if (was(AlarmType.HIGH)) HIGH_HYSTERESIS else 0
            when {
                v < urgentLine -> active += AlarmType.URGENT_LOW
                settings.lowEnabled && v < lowLine -> active += AlarmType.LOW
                settings.goingLowEnabled && projected(recent, latest) < settings.lowMgDl -> active += AlarmType.GOING_LOW
            }
            if (settings.highEnabled && v > highLine && !settings.quietAt(now.atZone(zone).hour)) active += AlarmType.HIGH
        } else {
            // Stale: keep glucose episodes open (don't announce them as resolved) but don't re-alert.
            active += state.activeSince.keys.filter { it != AlarmType.SIGNAL_LOSS }
        }

        val fire = mutableListOf<Alert>()
        val activeSince = state.activeSince.filterKeys { it in active }.toMutableMap()
        val lastAlertAt = state.lastAlertAt.filterKeys { it in active }.toMutableMap()
        val snoozedUntil = state.snoozedUntil.filterKeys { it in active }.toMutableMap()
        for (type in active) {
            if (type != AlarmType.SIGNAL_LOSS && !fresh) continue
            if (type !in activeSince) {
                activeSince[type] = now
                // Easing out of an urgent low into a plain low is the same episode, not a new alarm.
                if (type == AlarmType.LOW && was(AlarmType.URGENT_LOW)) lastAlertAt[type] = now
            }
            if (snoozedUntil[type]?.isAfter(now) == true) continue
            val last = lastAlertAt[type]
            if (last == null || Duration.between(last, now) >= repeatFor(type, settings)) {
                lastAlertAt[type] = now
                fire += Alert(type, v, age?.toMinutes())
            }
        }
        val cleared = state.activeSince.keys - active
        return Evaluation(AlarmState(activeSince, lastAlertAt, snoozedUntil, state.acknowledgedAt.filterKeys { it in active }), fire, cleared)
    }

    /** Someone answered [type] (a button, or swiping it away); restarts the emergency escalation clock. */
    fun acknowledge(state: AlarmState, type: AlarmType, now: Instant): AlarmState =
        state.copy(acknowledgedAt = state.acknowledgedAt + (type to now))

    /** Silence [type] for [minutes]; urgent low is capped at its 5-minute repeat. */
    fun snooze(state: AlarmState, type: AlarmType, minutes: Int, now: Instant): AlarmState {
        val capped = if (type == AlarmType.URGENT_LOW) minOf(minutes.toLong(), URGENT_REPEAT.toMinutes()) else minutes.toLong()
        return state.copy(snoozedUntil = state.snoozedUntil + (type to now.plus(Duration.ofMinutes(capped))))
    }

    private fun repeatFor(type: AlarmType, s: AlarmSettings): Duration = when (type) {
        AlarmType.URGENT_LOW -> URGENT_REPEAT
        AlarmType.LOW -> Duration.ofMinutes(s.lowSnoozeMinutes.toLong())
        AlarmType.HIGH -> Duration.ofMinutes(s.highSnoozeMinutes.toLong())
        AlarmType.SIGNAL_LOSS -> SIGNAL_REPEAT
        AlarmType.GOING_LOW -> NEVER
    }

    /**
     * Where glucose will be in 20 min at the current rate (linear, over the last ~15 min). Needs at
     * least 3 readings spanning 5+ minutes; otherwise returns +∞ (no prediction, no alarm).
     */
    internal fun projected(recent: List<GlucoseReading>, latest: GlucoseReading): Double {
        val window = recent.filter { Duration.between(it.timestamp, latest.timestamp).toMinutes() in 0..15 }
        val oldest = window.minByOrNull { it.timestamp } ?: return Double.POSITIVE_INFINITY
        val minutes = Duration.between(oldest.timestamp, latest.timestamp).seconds / 60.0
        if (window.size < 3 || minutes < 5) return Double.POSITIVE_INFINITY
        val ratePerMinute = (latest.glucoseMgDl - oldest.glucoseMgDl) / minutes
        return latest.glucoseMgDl + ratePerMinute * PROJECTION_MINUTES
    }
}
