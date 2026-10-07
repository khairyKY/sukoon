package com.sukoon.app.ui.home

import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.data.source.GlucoseReading

/**
 * Every visual state the Home screen can be in (docs/design-screens.md §2, screens
 * 5a/7b, 5b/7c, 8e, 8f, 8g, 8h, 8i). A calm Home and an urgent-low Home are effectively two
 * designs — this sealed interface is what makes the screen exhaustively `when`-dispatchable
 * rather than a pile of nullable flags.
 */
sealed interface HomeUiState {
    data class InRange(
        val glucoseMgDl: Int,
        val trend: TrendDirection,
        val recentReadings: List<GlucoseReading>,
    ) : HomeUiState

    data class Low(val glucoseMgDl: Int, val trend: TrendDirection) : HomeUiState

    data class High(
        val glucoseMgDl: Int,
        val trend: TrendDirection,
        val recentReadings: List<GlucoseReading>,
    ) : HomeUiState

    data class Urgent(val glucoseMgDl: Int, val trend: TrendDirection) : HomeUiState

    data class WarmingUp(val minutesRemaining: Int) : HomeUiState

    data class Stale(
        val lastGlucoseMgDl: Int,
        val minutesAgo: Int,
        val recentReadings: List<GlucoseReading>,
    ) : HomeUiState

    data object NoSensor : HomeUiState

    /** The paired sensor reached the end of its life: time for a new one. */
    data object SensorEnded : HomeUiState
}
