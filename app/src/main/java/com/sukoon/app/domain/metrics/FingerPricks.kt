package com.sukoon.app.domain.metrics

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.ui.home.HomeUiStateMapper
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Finger-pricks as glucose, for when the sensor has nothing fresh (it ended, or readings stopped):
 * what counts as "now" for Now, dose suggestions and the logbook.
 */
object FingerPricks {

    /** A prick is acted on for this long (a sensor reading for [HomeUiStateMapper.STALE_AFTER]). */
    val FRESH: Duration = Duration.ofMinutes(15)

    /** No sensor reading for this long: Now switches to finger-pricks. */
    val MODE_AFTER: Duration = Duration.ofMinutes(30)

    data class Prick(val at: Instant, val mgDl: Int, val finger: String? = null)

    /** The glucose to act on: [trend] null for a prick (one drop has no direction). */
    data class Current(val mgDl: Int, val trend: TrendDirection?, val at: Instant, val prick: Boolean)

    /** The logbook's finger-pricks, oldest first. */
    fun of(events: List<EventEntity>): List<Prick> = events
        .filter { it.logType == LogEventType.FINGERSTICK && it.value != null }
        .map { Prick(Instant.ofEpochMilli(it.timestampMillis), it.value!!.roundToInt(), it.site) }
        .sortedBy { it.at }

    /** A fresh sensor reading if there is one, else a prick from the last [FRESH]; null if neither. */
    fun current(latest: GlucoseReading?, pricks: List<Prick>, now: Instant): Current? {
        latest?.takeIf { !it.warmup && !it.timestamp.isAfter(now) && Duration.between(it.timestamp, now) <= HomeUiStateMapper.STALE_AFTER }
            ?.let { return Current(it.glucoseMgDl, it.trend, it.timestamp, prick = false) }
        return pricks.lastOrNull { !it.at.isAfter(now) && Duration.between(it.at, now) <= FRESH }
            ?.let { Current(it.mgDl, null, it.at, prick = true) }
    }
}
