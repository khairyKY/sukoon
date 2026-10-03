package com.sukoon.app.calibration

import android.content.Context
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.repository.LogbookRepository
import com.sukoon.app.data.source.nearestTo
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Opt-in calibration (You → Calibration). Refits from the logged finger-pricks whenever the
 * logbook changes (and every 15 minutes, as pairs age out of the 7-day window), and publishes the
 * result to [current], which the repository applies to every reading it hands out. Raw readings
 * stay raw in the database, so turning this off (or a new fit) changes nothing stored.
 */
class CalibrationManager(
    context: Context,
    private val current: MutableStateFlow<Calibration?>,
    private val glucose: GlucoseRepository,
    private val logbook: LogbookRepository,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)
    private val _fit = MutableStateFlow<FitResult?>(null)

    /** The latest fit (pairs used and skipped), for You → Calibration; null while off. */
    val fit: StateFlow<FitResult?> = _fit.asStateFlow()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ON, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ON, value).apply()
            scope.launch { refit() }
        }

    fun start() {
        val ticks = flow {
            while (true) {
                emit(Unit)
                delay(TimeUnit.MINUTES.toMillis(15))
            }
        }
        scope.launch {
            merge(logbook.eventsSince(0).map { }, ticks).collect { runCatching { refit() } }
        }
    }

    suspend fun refit() {
        if (!enabled) {
            current.value = null
            _fit.value = null
            return
        }
        val now = Instant.now()
        val since = now.minus(Calibrator.MAX_AGE).toEpochMilli()
        val pricks = logbook.eventsSince(since).first().filter { it.logType == LogEventType.FINGERSTICK && (it.value ?: 0.0) > 0 }
        val raw = glucose.rawReadingsSince(since - 10 * 60_000L).first()
        val pairs = pricks.mapNotNull { prick ->
            raw.nearestTo(prick.timestampMillis)?.let { reading ->
                CalibrationPair(Instant.ofEpochMilli(prick.timestampMillis), reading.glucoseMgDl, prick.value!!.roundToInt(), reading.trend)
            }
        }
        val result = Calibrator.fit(pairs, now)
        current.value = result.calibration
        _fit.value = result
    }

    private companion object {
        const val KEY_ON = "calibration_on"
    }
}
