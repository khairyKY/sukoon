package com.sukoon.app.emergency

import com.sukoon.app.alarms.AlarmState
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.data.source.GlucoseReading
import java.time.Duration
import java.time.Instant

/** Where an unanswered-alarm escalation stands. Kept in memory, like [AlarmState]. */
sealed interface EscalationPhase {
    data object Idle : EscalationPhase

    /** Contacts get texted when this runs out, unless someone answers first. */
    data class Countdown(val type: AlarmType, val startedAt: Instant) : EscalationPhase {
        val endsAt: Instant get() = startedAt.plus(Escalation.COUNTDOWN)
    }

    data class Sent(val type: AlarmType, val at: Instant, val to: List<String>) : EscalationPhase
}

enum class EscalationAction { START_COUNTDOWN, CANCEL, SEND, SEND_RECOVERED }

data class EscalationStep(val phase: EscalationPhase, val action: EscalationAction? = null)

/**
 * Emergency escalation (A8), pure so every rule is unit-tested. An alarm nobody answers is the
 * signal — not a raw number — because it catches the person who is asleep or has passed out:
 * - urgent low left unanswered for the chosen minutes, or
 * - a no-signal alarm that began while glucose was under 70 (someone unconscious can't answer it,
 *   and the phone may simply be out of range of them).
 * Any answer (a button, even swiping the notification away) restarts the clock. Then a 60 s
 * countdown anyone can stop, then texts + a call; again after 30 min if still unanswered; and a
 * "back to normal" text once a fresh reading is 70+.
 */
object Escalation {
    val COUNTDOWN: Duration = Duration.ofSeconds(60)
    val REPEAT: Duration = Duration.ofMinutes(30)
    const val LOW_MG_DL = 70

    /** The alarm that has gone [afterMinutes] without an answer, if any. [last] = the newest reading, however old. */
    fun unanswered(alarms: AlarmState, last: GlucoseReading?, now: Instant, afterMinutes: Int): AlarmType? {
        val limit = Duration.ofMinutes(afterMinutes.toLong())
        fun overdue(type: AlarmType) = clockStart(alarms, type)?.let { Duration.between(it, now) >= limit } == true
        return when {
            overdue(AlarmType.URGENT_LOW) -> AlarmType.URGENT_LOW
            last != null && last.glucoseMgDl < LOW_MG_DL && overdue(AlarmType.SIGNAL_LOSS) -> AlarmType.SIGNAL_LOSS
            else -> null
        }
    }

    /** When [type]'s no-answer clock started: the alarm itself, or the last answer to it if that came later. */
    fun clockStart(alarms: AlarmState, type: AlarmType): Instant? {
        val active = alarms.activeSince[type] ?: return null
        val answered = alarms.acknowledgedAt[type]
        return if (answered != null && answered.isAfter(active)) answered else active
    }

    /** One step. [recovered] = a fresh reading at 70 or more. Sent's recipients are filled in by the caller. */
    fun step(phase: EscalationPhase, unanswered: AlarmType?, recovered: Boolean, now: Instant): EscalationStep = when (phase) {
        EscalationPhase.Idle ->
            if (unanswered != null) EscalationStep(EscalationPhase.Countdown(unanswered, now), EscalationAction.START_COUNTDOWN) else EscalationStep(phase)

        is EscalationPhase.Countdown -> when {
            unanswered == null -> EscalationStep(EscalationPhase.Idle, EscalationAction.CANCEL)
            !now.isBefore(phase.endsAt) -> EscalationStep(EscalationPhase.Sent(phase.type, now, emptyList()), EscalationAction.SEND)
            else -> EscalationStep(phase)
        }

        is EscalationPhase.Sent -> when {
            recovered -> EscalationStep(EscalationPhase.Idle, EscalationAction.SEND_RECOVERED)
            unanswered != null && Duration.between(phase.at, now) >= REPEAT ->
                EscalationStep(EscalationPhase.Countdown(unanswered, now), EscalationAction.START_COUNTDOWN)
            else -> EscalationStep(phase)
        }
    }
}
