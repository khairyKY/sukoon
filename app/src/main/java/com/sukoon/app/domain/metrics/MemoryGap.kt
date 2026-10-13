package com.sukoon.app.domain.metrics

import com.sukoon.app.data.source.GlucoseReading
import java.time.Duration
import java.time.Instant

/**
 * A hole in the readings that one NFC tap can fill: the sensor's memory holds the last 8 hours (every
 * 15 minutes), so a gap of [MIN] or more that started within that window is worth a tap. The newest
 * such gap, or null. Readings in any order.
 */
object MemoryGap {
    val MIN: Duration = Duration.ofMinutes(30)
    val MEMORY: Duration = Duration.ofHours(8)

    fun fillable(readings: List<GlucoseReading>, now: Instant): Pair<Instant, Instant>? {
        val since = now.minus(MEMORY)
        val times = readings.map { it.timestamp }.filter { !it.isBefore(since) && !it.isAfter(now) }.sorted()
        if (times.isEmpty()) return null // nothing at all: that's "no readings", not a gap to fill
        return times.zipWithNext().lastOrNull { (a, b) -> Duration.between(a, b) >= MIN }
    }
}
