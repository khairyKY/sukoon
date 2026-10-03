package com.sukoon.app.ui.logbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sukoon.app.ai.AiPrompts
import com.sukoon.app.ai.CarbEstimate
import com.sukoon.app.ai.ChatTurn
import com.sukoon.app.ai.GeminiClient
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.repository.LogbookRepository
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the Logbook screen — today's window of logged events, newest first, from Room via [LogbookRepository]. */
class LogbookViewModel(private val repository: LogbookRepository, private val gemini: GeminiClient) : ViewModel() {

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

    /** A meal plus the rapid insulin for it; the insulin is stamped [preBolusMinutes] before the meal. */
    fun logMeal(carbs: Double, note: String?, insulinUnits: Double, preBolusMinutes: Int) {
        viewModelScope.launch {
            val now = Instant.now()
            repository.log(LogEventType.INSULIN, insulinUnits, null, at = now.minusSeconds(preBolusMinutes * 60L))
            repository.log(LogEventType.CARB, carbs, note, at = now)
        }
    }

    fun updateEvent(event: EventEntity) {
        viewModelScope.launch { repository.update(event) }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch { repository.delete(event) }
    }

    /** AI carb estimate for the quick-entry sheet — a suggestion only; the sheet's Save logs it. */
    suspend fun estimateCarbs(description: String, photoJpeg: ByteArray?): CarbEstimate {
        val prompt = description.ifBlank { "Estimate the carbs in this meal." }
        val arabic = Locale.getDefault().language == "ar"
        val reply = gemini.generate(AiPrompts.carbSystemPrompt(arabic), listOf(ChatTurn(fromUser = true, text = prompt)), photoJpeg, jsonOutput = true)
        return CarbEstimate.parse(reply)
    }

    companion object {
        private val WINDOW_MILLIS = TimeUnit.HOURS.toMillis(24)

        fun factory(repository: LogbookRepository, gemini: GeminiClient) = viewModelFactory {
            initializer { LogbookViewModel(repository, gemini) }
        }
    }
}
