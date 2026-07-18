package com.sukoon.app.ui.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.repository.LogbookRepository
import com.sukoon.app.data.source.GlucoseReading
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Selectable window for the graph (design 8j). */
enum class GraphRange(val hours: Int) {
    H3(3), H6(6), H12(12), H24(24);

    val millis: Long get() = TimeUnit.HOURS.toMillis(hours.toLong())
}

data class GraphUiState(
    val range: GraphRange = GraphRange.H3,
    val readings: List<GlucoseReading> = emptyList(),
    val events: List<EventEntity> = emptyList(),
)

class GraphViewModel(
    glucoseRepository: GlucoseRepository,
    logbookRepository: LogbookRepository,
) : ViewModel() {

    private val _range = MutableStateFlow(GraphRange.H3)
    val range: StateFlow<GraphRange> = _range.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val readings = _range.flatMapLatest { range ->
        glucoseRepository.readingsSince(System.currentTimeMillis() - range.millis)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val events = _range.flatMapLatest { range ->
        logbookRepository.eventsSince(System.currentTimeMillis() - range.millis)
    }

    val uiState: StateFlow<GraphUiState> = combine(_range, readings, events) { range, readings, events ->
        GraphUiState(range = range, readings = readings, events = events)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = GraphUiState(),
    )

    fun selectRange(range: GraphRange) {
        _range.value = range
    }

    companion object {
        fun factory(glucoseRepository: GlucoseRepository, logbookRepository: LogbookRepository) = viewModelFactory {
            initializer { GraphViewModel(glucoseRepository, logbookRepository) }
        }
    }
}
