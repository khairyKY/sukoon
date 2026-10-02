package com.sukoon.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sukoon.app.data.repository.GlucoseRepository
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the Home screen. Thin by design: it just observes the app-scoped [GlucoseRepository]'s
 * Room-backed Flows (latest reading + recent window + source status) and maps them to a
 * [HomeUiState] via the pure, unit-tested [HomeUiStateMapper]. It no longer owns the source or
 * any in-memory buffer — persistence and the source lifecycle live in the repository (A2), so
 * data keeps flowing while the user is on other tabs and survives config changes / relaunch.
 */
class HomeViewModel(repository: GlucoseRepository) : ViewModel() {

    // Re-evaluates freshness when no new reading arrives — otherwise a silent source would leave
    // the last value on screen as if live, never tipping into the Stale state.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.status,
        repository.latestReading,
        repository.recentReadings(WINDOW_SIZE),
        ticker,
    ) { status, latest, recent, _ ->
        HomeUiStateMapper.map(status, latest, toBars(recent.map { it.glucoseMgDl }), Instant.now())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState.NoSensor,
    )

    companion object {
        /** Averages the window into ≤[BARS] buckets — MiniGraph is a fixed bar strip, not a line. */
        fun toBars(values: List<Int>): List<Int> {
            val chunk = maxOf(1, (values.size + BARS - 1) / BARS)
            return values.chunked(chunk) { it.average().roundToInt() }
        }

        private const val BARS = 12
        // Home mini-graph window: 180 samples ≈ the "Last 3 hours" label at Libre/DiaBox's 1-min
        // cadence. ponytail: count, not time — the demo source ticks every 2 s so it shows ~6 min.
        private const val WINDOW_SIZE = 180
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TICK_MS = 30_000L

        fun factory(repository: GlucoseRepository) = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
