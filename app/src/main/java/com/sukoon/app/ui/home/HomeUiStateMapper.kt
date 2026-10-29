package com.sukoon.app.ui.home

import com.sukoon.app.domain.metrics.TargetRange
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceStatus
import com.sukoon.app.domain.metrics.GlucoseMetrics
import com.sukoon.app.domain.metrics.GlucoseMetrics.RangeBracket
import java.time.Duration
import java.time.Instant
import com.sukoon.app.data.source.libre.SensorLife
import com.sukoon.app.alarms.AlarmSettings
import com.sukoon.app.domain.metrics.FingerPricks
import java.time.ZoneId

/**
 * Pure mapping from raw source signals (status + latest reading + recent window) to [HomeUiState].
 * Side-effect-free and clock-injected (`now`) so every state transition is unit-testable without
 * a running source. [HomeViewModel] is the thin shell that feeds this from SimulatedSource today —
 * and from LibreBleSource later, unchanged: the mapping is the same regardless of source.
 *
 * Glucose-range brackets deliberately reuse [GlucoseMetrics.bracketFor] so the Home states and the
 * (future) Insights TIR chart can never disagree about what counts as "low".
 */
object HomeUiStateMapper {

    /** PLAN.md §5: a reading older than this is never presented as current, whatever the source says. */
    val STALE_AFTER: Duration = Duration.ofMinutes(10)

    fun map(
        status: SourceStatus,
        latest: GlucoseReading?,
        /** The last few hours, oldest first: Home's chart. */
        recent: List<GlucoseReading>,
        now: Instant,
        /** The paired sensor's life (null on demo data): the real warm-up countdown, and "ended". */
        life: SensorLife? = null,
        /** The logbook's finger-pricks, oldest first: Now runs on them when the sensor has nothing. */
        pricks: List<FingerPricks.Prick> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): HomeUiState {
        val base = when (life) {
            is SensorLife.Ended -> HomeUiState.SensorEnded
            is SensorLife.WarmingUp -> HomeUiState.WarmingUp(life.minutesLeft, provisional(latest, recent, now))
            // A first-hour reading is never shown as a real one, whatever the countdown says.
            else -> if (latest?.warmup == true) HomeUiState.WarmingUp(0, provisional(latest, recent, now)) else byStatus(status, latest, recent, now)
        }
        return fingerPrick(base, pricks, now, zone) ?: base
    }

    /**
     * Finger-prick mode, on by itself: the sensor ended, its readings stopped [FingerPricks.MODE_AFTER]
     * ago, or there's no sensor but you've pricked in the last day. Off by itself once readings return.
     */
    private fun fingerPrick(base: HomeUiState, pricks: List<FingerPricks.Prick>, now: Instant, zone: ZoneId): HomeUiState? {
        val past = pricks.filter { !it.at.isAfter(now) }
        val why = when {
            base is HomeUiState.SensorEnded -> HomeUiState.FingerPrick.Why.SENSOR_ENDED
            base is HomeUiState.Stale && base.minutesAgo >= FingerPricks.MODE_AFTER.toMinutes() -> HomeUiState.FingerPrick.Why.NO_READINGS
            base is HomeUiState.NoSensor && past.any { Duration.between(it.at, now) <= Duration.ofDays(1) } -> HomeUiState.FingerPrick.Why.NO_SENSOR
            else -> return null
        }
        val today = now.atZone(zone).toLocalDate()
        return HomeUiState.FingerPrick(why, past.lastOrNull(), past.filter { it.at.atZone(zone).toLocalDate() == today }, (base as? HomeUiState.Stale)?.minutesAgo)
    }

    private fun byStatus(status: SourceStatus, latest: GlucoseReading?, recent: List<GlucoseReading>, now: Instant): HomeUiState = when (status) {
        SourceStatus.Disconnected -> HomeUiState.NoSensor

        // The countdown comes from the paired sensor's start (life, above); this is only a fallback.
        SourceStatus.WarmingUp -> HomeUiState.WarmingUp(minutesRemaining = 0, provisionalMgDl = provisional(latest, recent, now))

        SourceStatus.Stale -> latest?.let { stale(it, recent, now) } ?: HomeUiState.NoSensor

        // Freshness comes from the reading's own age, not the status: a source can report Connected
        // while no packet has arrived for a while, and a transient Error (one bad BLE packet)
        // shouldn't hide a reading that's still minutes old.
        SourceStatus.Connecting,
        SourceStatus.Connected,
        is SourceStatus.Error -> when {
            latest == null -> HomeUiState.NoSensor
            Duration.between(latest.timestamp, now) > STALE_AFTER -> stale(latest, recent, now)
            else -> connected(latest, recent)
        }
    }

    /** The newest reading while the sensor warms up, if it is fresh: shown in grey, never acted on. */
    private fun provisional(latest: GlucoseReading?, recent: List<GlucoseReading>, now: Instant): Int? =
        com.sukoon.app.domain.metrics.WarmupReadings.settled(recent + listOfNotNull(latest), now)?.glucoseMgDl

    private fun stale(latest: GlucoseReading, recent: List<GlucoseReading>, now: Instant) = HomeUiState.Stale(
        lastGlucoseMgDl = latest.glucoseMgDl,
        minutesAgo = Duration.between(latest.timestamp, now).toMinutes().toInt(),
        recentReadings = recent,
    )

    private fun connected(reading: GlucoseReading, recent: List<GlucoseReading>): HomeUiState =
        // Urgent exactly when the urgent-low alarm sounds (under 55), not at the stats' 54 line.
        if (reading.glucoseMgDl < AlarmSettings.URGENT_LOW_MG_DL) HomeUiState.Urgent(reading.glucoseMgDl, reading.trend)
        else when (GlucoseMetrics.bracketFor(reading.glucoseMgDl, TargetRange.high)) {
            RangeBracket.VERY_LOW, RangeBracket.LOW -> HomeUiState.Low(reading.glucoseMgDl, reading.trend)
            RangeBracket.IN_RANGE -> HomeUiState.InRange(reading.glucoseMgDl, reading.trend, recent)
            RangeBracket.HIGH, RangeBracket.VERY_HIGH -> HomeUiState.High(reading.glucoseMgDl, reading.trend, recent)
        }
}
