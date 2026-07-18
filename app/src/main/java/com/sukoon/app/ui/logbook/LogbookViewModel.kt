package com.sukoon.app.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.repository.LogbookRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the Logbook screen — today's window of logged events, newest first, from Room via [LogbookRepository]. */
class LogbookViewModel(private val repository: LogbookRepository) : ViewModel() {

    val uiState: StateFlow<LogbookUiState> = repository
        .eventsSince(System.currentTimeMillis() - WINDOW_MILLIS)
        .map { events -> LogbookUiState(events = events.sortedByDescending { it.timestampMillis }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = LogbookUiState(),
        )

    fun log(type: LogEventType, value: Double?, note: String?) {
        viewModelScope.launch { repository.log(type, value, note) }
    }

    fun updateEvent(event: EventEntity) {
        viewModelScope.launch { repository.update(event) }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch { repository.delete(event) }
    }

    companion object {
        private val WINDOW_MILLIS = TimeUnit.HOURS.toMillis(24)

        fun factory(repository: LogbookRepository) = viewModelFactory {
            initializer { LogbookViewModel(repository) }
        }
    }
}
