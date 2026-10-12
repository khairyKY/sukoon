package com.sukoon.app.domain.metrics

import com.sukoon.app.data.source.GlucoseReading
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * A sensor's first hour starts as noise (Kai's sensor, minute 0 to 3: 1103, 528, 358) and only later
 * reads like glucose. The grey first-hour number on Now and in the status bar shows only once the
 * newest [COUNT] first-hour readings are all within [RANGE] and move no faster than a body can
 * ([Spikes.MAX_MG_DL_PER_MIN]); until then, just the countdown.
 */
object WarmupReadings {
    private const val COUNT = 3
    private val RANGE = 40..400
    private val FRESH: Duration = Duration.ofMinutes(10)

    /** The newest first-hour reading worth showing, from [readings] (any order), or null. */
    fun settled(readings: List<GlucoseReading>, now: Instant): GlucoseReading? {
        val last = readings.filter { it.warmup && Duration.between(it.timestamp, now) <= FRESH }
            .distinctBy { it.timestamp }.sortedBy { it.timestamp }.takeLast(COUNT)
        if (last.size < COUNT || last.any { it.glucoseMgDl !in RANGE }) return null
        val steady = last.zipWithNext().all { (a, b) ->
            val minutes = maxOf(1.0, Duration.between(a.timestamp, b.timestamp).seconds / 60.0)
            abs(b.glucoseMgDl - a.glucoseMgDl) / minutes <= Spikes.MAX_MG_DL_PER_MIN
        }
        return if (steady) last.last() else null
    }
}
