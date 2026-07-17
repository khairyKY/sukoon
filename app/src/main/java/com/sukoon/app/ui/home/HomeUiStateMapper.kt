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

    fun map(
        status: SourceStatus,
        latest: GlucoseReading?,
        recentMgDl: List<Int>,
        now: Instant,
    ): HomeUiState = when (status) {
        SourceStatus.Disconnected, is SourceStatus.Error -> HomeUiState.NoSensor

        // The simulator jumps straight to Connected, so this branch is dead until Track B. A real
        // sensor's warm-up countdown comes from the sensor session (warmupEndsAtMillis), not from
        // this payload-less status — hence the placeholder. LibreBleSource will supply real minutes.
        SourceStatus.WarmingUp -> HomeUiState.WarmingUp(minutesRemaining = 0)

        SourceStatus.Stale -> HomeUiState.Stale(
            lastGlucoseMgDl = latest?.glucoseMgDl ?: 0,
            minutesAgo = latest?.let { Duration.between(it.timestamp, now).toMinutes().toInt() } ?: 0,
            recentReadings = recentMgDl,
        )

        // Connecting can briefly hold a prior reading; show it if present, else the empty state.
        SourceStatus.Connecting,
        SourceStatus.Connected -> latest?.let { connected(it, recentMgDl) } ?: HomeUiState.NoSensor
    }

    private fun connected(reading: GlucoseReading, recentMgDl: List<Int>): HomeUiState =
        when (GlucoseMetrics.bracketFor(reading.glucoseMgDl)) {
            RangeBracket.VERY_LOW -> HomeUiState.Urgent(reading.glucoseMgDl, reading.trend)
            RangeBracket.LOW -> HomeUiState.Low(reading.glucoseMgDl, reading.trend)
            RangeBracket.IN_RANGE -> HomeUiState.InRange(reading.glucoseMgDl, reading.trend, recentMgDl)
            RangeBracket.HIGH, RangeBracket.VERY_HIGH -> HomeUiState.High(reading.glucoseMgDl, reading.trend, recentMgDl)
        }
}
