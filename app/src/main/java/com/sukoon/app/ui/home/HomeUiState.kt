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

    /** [provisionalMgDl]: a first-hour reading the sensor sent anyway: shown, but not counted anywhere. */
    data class WarmingUp(val minutesRemaining: Int, val provisionalMgDl: Int? = null) : HomeUiState

    data class Stale(
        val lastGlucoseMgDl: Int,
        val minutesAgo: Int,
        val recentReadings: List<GlucoseReading>,
    ) : HomeUiState

    data object NoSensor : HomeUiState

    /** The paired sensor reached the end of its life: time for a new one. */
    data object SensorEnded : HomeUiState

    /**
     * Finger-prick mode: no sensor reading to show ([why]), so Now runs on the last prick ([last],
     * null before the first one) and today's pricks ([today], oldest first).
     */
    data class FingerPrick(
        val why: Why,
        val last: com.sukoon.app.domain.metrics.FingerPricks.Prick?,
        val today: List<com.sukoon.app.domain.metrics.FingerPricks.Prick>,
        /** Minutes since the last sensor reading, for [Why.NO_READINGS]. */
        val minutesSinceReading: Int? = null,
    ) : HomeUiState {
        enum class Why { SENSOR_ENDED, NO_READINGS, NO_SENSOR }
    }
}
