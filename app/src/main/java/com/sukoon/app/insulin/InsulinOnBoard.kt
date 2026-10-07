package com.sukoon.app.insulin

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import java.time.Duration
import java.time.Instant
import kotlin.math.exp

/** How the rapid-acting insulin works over time: when its effect peaks and when it is done. */
data class InsulinAction(val peakMinutes: Int = 75, val durationMinutes: Int = 300) {
    /** The curve needs the peak well before half the duration. */
    fun sanitized(): InsulinAction {
        val duration = durationMinutes.coerceIn(DURATION_RANGE)
        return InsulinAction(peakMinutes.coerceIn(PEAK_RANGE.first, minOf(PEAK_RANGE.last, duration / 2 - 15)), duration)
    }

    companion object {
        val PEAK_RANGE = 35..120
        val DURATION_RANGE = 180..480
    }
}

/**
 * Insulin on board: rapid insulin from logged doses that hasn't acted yet (long-acting basal is
 * not counted). Uses the exponential activity curve OpenAPS and Loop use (Dragan Maksimovic,
 * 2017): it rises to the peak and is fully spent at the duration.
 */
object InsulinOnBoard {

    /** Share of a dose still to act [minutes] after it was taken. */
    fun remaining(minutes: Double, action: InsulinAction): Double {
        val td = action.durationMinutes.toDouble()
        val tp = action.peakMinutes.toDouble()
        if (minutes <= 0) return 1.0
        if (minutes >= td) return 0.0
        val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
        val a = 2 * tau / td
        val s = 1 / (1 - a + (1 + a) * exp(-td / tau))
        return (1 - s * (1 - a) * ((minutes * minutes / (tau * td * (1 - a)) - minutes / tau - 1) * exp(-minutes / tau) + 1)).coerceIn(0.0, 1.0)
    }

    fun total(events: List<EventEntity>, now: Instant, action: InsulinAction): Double =
        events.filter { it.logType == LogEventType.INSULIN }.sumOf { dose ->
            val minutes = Duration.between(Instant.ofEpochMilli(dose.timestampMillis), now).toMillis() / 60_000.0
            (dose.value ?: 0.0) * remaining(minutes, action)
        }
}
