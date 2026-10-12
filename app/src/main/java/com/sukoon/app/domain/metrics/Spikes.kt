package com.sukoon.app.domain.metrics

import com.sukoon.app.data.source.GlucoseReading
import java.time.Duration
import kotlin.math.abs

/**
 * Readings no body produces: one that jumps more than [MAX_MG_DL_PER_MIN] away from both of its
 * neighbours (glucose moves a few mg/dL a minute at most). They come from a misread, or from the
 * memory of a sensor that wasn't running (0.8.1 saved that noise when it paired an unstarted sensor).
 * Pure, so the rule is unit-tested; a reading with a missing neighbour is always kept.
 */
object Spikes {
    const val MAX_MG_DL_PER_MIN = 10.0
    private val NEIGHBOUR = Duration.ofMinutes(15)

    /** [readings] chronological. */
    fun of(readings: List<GlucoseReading>): List<GlucoseReading> = readings.filterIndexed { i, r ->
        val before = readings.getOrNull(i - 1)
        val after = readings.getOrNull(i + 1)
        before != null && after != null && impossible(before, r) && impossible(r, after)
    }

    fun without(readings: List<GlucoseReading>): List<GlucoseReading> {
        val spikes = of(readings).map { it.timestamp }.toSet()
        return if (spikes.isEmpty()) readings else readings.filterNot { it.timestamp in spikes }
    }

    private fun impossible(a: GlucoseReading, b: GlucoseReading): Boolean {
        val gap = Duration.between(a.timestamp, b.timestamp)
        if (gap.isNegative || gap > NEIGHBOUR) return false
        val minutes = maxOf(1.0, gap.seconds / 60.0)
        return abs(b.glucoseMgDl - a.glucoseMgDl) / minutes > MAX_MG_DL_PER_MIN
    }
}
