package com.sukoon.app.ui.graph

import java.time.ZoneId
import java.time.LocalDate
import com.sukoon.app.domain.metrics.TargetRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.repository.LogbookRepository
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.insights.RangeSummary
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** Selectable window for the graph (design 8j); [tickHours] spaces the clock labels. Days ranges carry GMI. */
enum class GraphRange(val hours: Int, val tickHours: Int) {
    H3(3, 1), H6(6, 2), H12(12, 3), H24(24, 6), D7(24 * 7, 24), D14(24 * 14, 48);

    val millis: Long get() = TimeUnit.HOURS.toMillis(hours.toLong())
}

data class GraphUiState(
    val range: GraphRange = GraphRange.H3,
    val readings: List<GlucoseReading> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    /** Time in range, mean and GMI for [readings] (the same math as Insights); null when there are none. */
    val summary: RangeSummary? = null,
    /** The past day shown, and where its chart ends; null for up to now. */
    val day: LocalDate? = null,
    val endMillis: Long? = null,
)

class GraphViewModel(
    glucoseRepository: GlucoseRepository,
    logbookRepository: LogbookRepository,
) : ViewModel() {

    private val _range = MutableStateFlow(GraphRange.H3)
    val range: StateFlow<GraphRange> = _range.asStateFlow()
    private val day = MutableStateFlow<LocalDate?>(null)

    /** Look back at [date] (null: up to now). A past day is shown whole. */
    fun showDay(date: LocalDate?) {
        day.value = date
        if (date != null) _range.value = GraphRange.H24
    }

    /** Where the chart ends: the chosen day's midnight, or now (null). */
    private fun endOf(date: LocalDate?): Long? = date?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val readings = combine(_range, day) { r, d -> r to d }.flatMapLatest { (range, d) ->
        val end = endOf(d)
        if (end == null) glucoseRepository.readingsSince(System.currentTimeMillis() - range.millis) else glucoseRepository.readingsBetween(end - range.millis, end)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val events = combine(_range, day) { r, d -> r to d }.flatMapLatest { (range, d) ->
        val end = endOf(d)
        if (end == null) logbookRepository.eventsSince(System.currentTimeMillis() - range.millis) else logbookRepository.eventsBetween(end - range.millis, end)
    }

    val uiState: StateFlow<GraphUiState> = combine(_range, readings, events, day) { range, readings, events, d ->
        val end = endOf(d)
        GraphUiState(range = range, readings = readings, events = events, summary = InsightEngine.summary(readings, end?.let(Instant::ofEpochMilli) ?: Instant.now(), TargetRange.high), day = d, endMillis = end)
    }
        .flowOn(Dispatchers.Default) // 14 days is ~20k readings: keep the summary off the main thread
        .stateIn(
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
