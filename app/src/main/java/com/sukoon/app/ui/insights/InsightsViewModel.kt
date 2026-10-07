package com.sukoon.app.ui.insights

import com.sukoon.app.domain.metrics.TargetRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.repository.LogbookRepository
import com.sukoon.app.insights.Insight
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

data class InsightsUiState(val acknowledged: Boolean = false, val insights: List<Insight>? = null)

/** Re-analyzes the last 14 days whenever readings or the logbook change (cheap: ~20k points). */
class InsightsViewModel(
    glucose: GlucoseRepository,
    logbook: LogbookRepository,
    private val settings: SettingsPrefs,
) : ViewModel() {

    private val acknowledged = MutableStateFlow(settings.insightsAcknowledged)

    val uiState: StateFlow<InsightsUiState> = combine(
        glucose.readingsSince(since()),
        logbook.eventsSince(since()),
        acknowledged.asStateFlow(),
    ) { readings, events, ack ->
        InsightsUiState(ack, InsightEngine.analyze(readings, events, Instant.now(), ZoneId.systemDefault(), TargetRange.high))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState(settings.insightsAcknowledged))

    fun acknowledge() {
        settings.insightsAcknowledged = true
        acknowledged.value = true
    }

    private fun since() = System.currentTimeMillis() - Duration.ofDays(InsightEngine.WINDOW_DAYS).toMillis()

    companion object {
        fun factory(glucose: GlucoseRepository, logbook: LogbookRepository, settings: SettingsPrefs) = viewModelFactory {
            initializer { InsightsViewModel(glucose, logbook, settings) }
        }
    }
}
