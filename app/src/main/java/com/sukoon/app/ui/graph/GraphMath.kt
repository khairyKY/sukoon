package com.sukoon.app.ui.graph

import com.sukoon.app.data.source.GlucoseReading
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Mean per [bucketMillis] window, stamped at the window's middle, so a 14-day chart draws ~1.3k
 * points instead of ~20k. ponytail: means can soften a dip shorter than a bucket; at 7–14 days a
 * bucket is under a pixel wide, and the stats and readings list always use the raw data.
 */
internal fun downsample(readings: List<GlucoseReading>, bucketMillis: Long): List<GlucoseReading> =
    readings.groupBy { it.timestamp.toEpochMilli() / bucketMillis }.map { (bucket, inBucket) ->
        inBucket.last().copy(
            timestamp = Instant.ofEpochMilli(bucket * bucketMillis + bucketMillis / 2),
            glucoseMgDl = inBucket.map { it.glucoseMgDl }.average().roundToInt(),
        )
    }

/** Top of the y-axis: the highest reading plus headroom, rounded up to 50, kept within 250–400 so the 70–180 band keeps its shape. */
internal fun yMaxFor(readings: List<GlucoseReading>): Int =
    (((readings.maxOfOrNull { it.glucoseMgDl } ?: 0) + 10 + 49) / 50 * 50).coerceIn(250, 400)

/** Whole-hour clock times in [startMillis, endMillis] every [stepHours] hours, aligned to local midnight (24/48 = day ticks). */
internal fun timeTicks(startMillis: Long, endMillis: Long, stepHours: Int, zone: ZoneId): List<Long> {
    val ticks = mutableListOf<Long>()
    var t = Instant.ofEpochMilli(startMillis).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
    while (t.toInstant().toEpochMilli() <= endMillis) {
        if ((t.toLocalDate().toEpochDay() * 24 + t.hour) % stepHours == 0L) ticks += t.toInstant().toEpochMilli()
        t = t.plusHours(1)
    }
    return ticks
}
