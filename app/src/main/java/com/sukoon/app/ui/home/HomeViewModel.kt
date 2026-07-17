package com.sukoon.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SimulatedSource
import com.sukoon.app.data.source.SourceStatus
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Home screen from a [SimulatedSource] (Track A — see docs/PLAN.md §0). Deliberately
 * thin: it owns the source's lifecycle (the source runs on `viewModelScope`, so it's torn down
 * automatically when the ViewModel clears) and keeps the rolling reading window; all the actual
 * state-derivation logic lives in the pure, unit-tested [HomeUiStateMapper].
 *
 * ponytail: SimulatedSource is hardcoded here rather than injected. When LibreBleSource lands,
 * swap it behind the GlucoseSource interface (constructor param + a factory/DI) — not worth the
 * indirection for a single implementation today.
 */
class HomeViewModel : ViewModel() {

    private val source = SimulatedSource(scope = viewModelScope)

    private val recent = ArrayDeque<Int>()
    private var latest: GlucoseReading? = null

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.NoSensor)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            source.readings.collect { reading ->
                latest = reading
                recent.addLast(reading.glucoseMgDl)
                while (recent.size > WINDOW_SIZE) recent.removeFirst()
                recompute(source.status.value)
            }
        }
        // Status transitions that don't coincide with a new reading (e.g. going Stale) still
        // need to re-render.
        viewModelScope.launch {
            source.status.collect { status -> recompute(status) }
        }
        viewModelScope.launch { source.connect() }
    }

    private fun recompute(status: SourceStatus) {
        _uiState.value = HomeUiStateMapper.map(status, latest, recent.toList(), Instant.now())
    }

    private companion object {
        // ~last 12 samples for the Home mini-graph.
        const val WINDOW_SIZE = 12
    }
}
