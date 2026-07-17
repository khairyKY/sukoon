package com.sukoon.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sukoon.app.data.repository.GlucoseRepository
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the Home screen. Thin by design: it just observes the app-scoped [GlucoseRepository]'s
 * Room-backed Flows (latest reading + recent window + source status) and maps them to a
 * [HomeUiState] via the pure, unit-tested [HomeUiStateMapper]. It no longer owns the source or
 * any in-memory buffer — persistence and the source lifecycle live in the repository (A2), so
 * data keeps flowing while the user is on other tabs and survives config changes / relaunch.
 */
class HomeViewModel(repository: GlucoseRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.status,
        repository.latestReading,
        repository.recentReadings(WINDOW_SIZE),
    ) { status, latest, recent ->
        HomeUiStateMapper.map(status, latest, recent.map { it.glucoseMgDl }, Instant.now())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState.NoSensor,
    )

    companion object {
        // ~last 12 samples for the Home mini-graph.
        private const val WINDOW_SIZE = 12
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(repository: GlucoseRepository) = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
