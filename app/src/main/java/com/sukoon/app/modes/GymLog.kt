package com.sukoon.app.modes

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The last workout as one line of Kai's training ledger (vault: Fitness And Health/LOG.md):
 * `date | session | last meal + time | last rapid + time | how it felt | BG before / after / +2h`.
 * The sensor already has the glucose; the line saves typing it. "How it felt" stays his to write.
 */
object GymLog {
    private val NEAR: Duration = Duration.ofMinutes(15)
    private val BEFORE: Duration = Duration.ofHours(6)

    /** [glucose]: sensor readings and finger-pricks, (time, mg/dL). Null when no workout is logged. */
    fun line(events: List<EventEntity>, glucose: List<Pair<Instant, Int>>, zone: ZoneId): String? {
        val workout = events.filter { it.logType == LogEventType.ACTIVITY }.maxByOrNull { it.timestampMillis } ?: return null
        val start = Instant.ofEpochMilli(workout.timestampMillis)
        val minutes = (workout.value ?: 0.0).toLong()
        val end = start.plus(Duration.ofMinutes(minutes))
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(zone)
        val time = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(zone)
        fun before(type: LogEventType) = events.filter { it.logType == type && it.timestampMillis <= workout.timestampMillis && Instant.ofEpochMilli(it.timestampMillis).isAfter(start.minus(BEFORE)) }
            .maxByOrNull { it.timestampMillis }
        fun at(t: Instant) = glucose.filter { Duration.between(it.first, t).abs() <= NEAR }.minByOrNull { Duration.between(it.first, t).abs() }?.second?.toString() ?: "—"
        val session = listOfNotNull(workout.note?.takeIf { it.isNotBlank() }?.lowercase(), "$minutes min").joinToString(" ")
        val meal = before(LogEventType.CARB)?.let { m ->
            listOfNotNull(m.note?.takeIf { it.isNotBlank() } ?: m.value?.let { String.format(Locale.US, "%.0f g", it) }, time.format(Instant.ofEpochMilli(m.timestampMillis))).joinToString(" ")
        } ?: "—"
        val rapid = before(LogEventType.INSULIN)?.let { d -> String.format(Locale.US, "%su %s", (d.value ?: 0.0).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }, time.format(Instant.ofEpochMilli(d.timestampMillis))) } ?: "—"
        return listOf(date.format(start), session, meal, rapid, "", "${at(start)} / ${at(end)} / ${at(end.plus(Duration.ofHours(2)))}").joinToString(" | ")
    }
}
