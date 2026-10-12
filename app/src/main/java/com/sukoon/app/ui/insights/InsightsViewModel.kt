package com.sukoon.app.ui.insights

import com.sukoon.app.domain.metrics.TargetRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.repository.LogbookRepository
import com.sukoon.app.insights.InEffect
import com.sukoon.app.insights.Insight
import com.sukoon.app.insights.InsightDigest
import com.sukoon.app.insights.InsightDigestBuilder
import com.sukoon.app.insights.InsightEngine
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class InsightsUiState(
    val acknowledged: Boolean = false,
    val insights: List<Insight>? = null,
    val digest: InsightDigest? = null,
    /** What's already on, so the one thing's button never offers it again. */
    val inEffect: InEffect = InEffect(),
)

/** Re-analyzes the last 14 days whenever readings or the logbook change (cheap: ~20k points), or a setting the actions touch. */
class InsightsViewModel(
    glucose: GlucoseRepository,
    logbook: LogbookRepository,
    private val settings: SettingsPrefs,
    private val calibrationOn: () -> Boolean = { false },
) : ViewModel() {

    private val acknowledged = MutableStateFlow(settings.insightsAcknowledged)
    private val settingsChanged = MutableStateFlow(0)

    val uiState: StateFlow<InsightsUiState> = combine(
        glucose.readingsSince(since()),
        logbook.eventsSince(since()),
        acknowledged.asStateFlow(),
        settingsChanged,
    ) { readings, events, ack, _ ->
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val insights = InsightEngine.analyze(readings, events, now, zone, TargetRange.high)
        InsightsUiState(ack, insights, InsightDigestBuilder.build(insights, events, now, zone), inEffect())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState(settings.insightsAcknowledged))

    private fun inEffect() = InEffect(
        goingLow = settings.alarmSettings.goingLowEnabled,
        workoutCheck = settings.afterWorkoutCheck,
        doseSuggestions = settings.doseSettings.enabled,
        calibration = calibrationOn(),
    )

    fun acknowledge() {
        settings.insightsAcknowledged = true
        acknowledged.value = true
    }

    /** After an action changed a setting: the button moves on to the next useful one. */
    fun settingsChanged() {
        settingsChanged.value++
    }

    private fun since() = System.currentTimeMillis() - Duration.ofDays(InsightEngine.WINDOW_DAYS).toMillis()

    companion object {
        fun factory(glucose: GlucoseRepository, logbook: LogbookRepository, settings: SettingsPrefs, calibrationOn: () -> Boolean = { false }) = viewModelFactory {
            initializer { InsightsViewModel(glucose, logbook, settings, calibrationOn) }
        }
    }
}
