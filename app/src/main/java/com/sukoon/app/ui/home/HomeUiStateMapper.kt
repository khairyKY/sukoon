package com.sukoon.app.ui.home

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceStatus
import com.sukoon.app.domain.metrics.GlucoseMetrics
import com.sukoon.app.domain.metrics.GlucoseMetrics.RangeBracket
import java.time.Duration
import java.time.Instant

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
        recentMgDl: List<Int>,
        now: Instant,
    ): HomeUiState = when (status) {
        SourceStatus.Disconnected -> HomeUiState.NoSensor

        // The simulator jumps straight to Connected, so this branch is dead until Track B. A real
        // sensor's warm-up countdown comes from the sensor session (warmupEndsAtMillis), not from
        // this payload-less status — hence the placeholder. LibreBleSource will supply real minutes.
        SourceStatus.WarmingUp -> HomeUiState.WarmingUp(minutesRemaining = 0)

        SourceStatus.Stale -> latest?.let { stale(it, recentMgDl, now) } ?: HomeUiState.NoSensor

        // Freshness comes from the reading's own age, not the status: a source can report Connected
        // while no packet has arrived for a while, and a transient Error (one bad BLE packet)
        // shouldn't hide a reading that's still minutes old.
        SourceStatus.Connecting,
        SourceStatus.Connected,
        is SourceStatus.Error -> when {
            latest == null -> HomeUiState.NoSensor
            Duration.between(latest.timestamp, now) > STALE_AFTER -> stale(latest, recentMgDl, now)
            else -> connected(latest, recentMgDl)
        }
    }

    private fun stale(latest: GlucoseReading, recentMgDl: List<Int>, now: Instant) = HomeUiState.Stale(
        lastGlucoseMgDl = latest.glucoseMgDl,
        minutesAgo = Duration.between(latest.timestamp, now).toMinutes().toInt(),
        recentReadings = recentMgDl,
    )

    private fun connected(reading: GlucoseReading, recentMgDl: List<Int>): HomeUiState =
        when (GlucoseMetrics.bracketFor(reading.glucoseMgDl)) {
            RangeBracket.VERY_LOW -> HomeUiState.Urgent(reading.glucoseMgDl, reading.trend)
            RangeBracket.LOW -> HomeUiState.Low(reading.glucoseMgDl, reading.trend)
            RangeBracket.IN_RANGE -> HomeUiState.InRange(reading.glucoseMgDl, reading.trend, recentMgDl)
            RangeBracket.HIGH, RangeBracket.VERY_HIGH -> HomeUiState.High(reading.glucoseMgDl, reading.trend, recentMgDl)
        }
}
