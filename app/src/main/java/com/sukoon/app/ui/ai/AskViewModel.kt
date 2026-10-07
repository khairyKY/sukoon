package com.sukoon.app.ui.ai

import com.sukoon.app.insulin.Profile
import com.sukoon.app.insulin.DoseSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sukoon.app.ai.AiPrompts
import com.sukoon.app.ai.ChatTurn
import com.sukoon.app.ai.GeminiClient
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.repository.LogbookRepository
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.sukoon.app.insulin.InsulinAction

data class AskUiState(
    val turns: List<ChatTurn> = emptyList(),
    val thinking: Boolean = false,
    val error: String? = null,
    /** A question whose answer failed — dropped from [turns] (Gemini needs strictly alternating turns) and handed back to the input box. */
    val unanswered: String? = null,
)

/**
 * "Ask" chat (Trends → Ask). Each question re-reads the last 7 days from Room so answers track
 * the newest readings, and sends the whole conversation so follow-ups have context.
 * ponytail: the chat lives in memory only — it's a lens on the data, not a record worth keeping.
 */
class AskViewModel(
    private val glucoseRepository: GlucoseRepository,
    private val logbookRepository: LogbookRepository,
    private val gemini: GeminiClient,
    private val doseSettings: () -> DoseSettings = { DoseSettings() },
    private val profile: () -> Profile = { Profile() },
    private val insulinAction: () -> InsulinAction = { InsulinAction() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(AskUiState())
    val uiState: StateFlow<AskUiState> = _uiState.asStateFlow()

    val hasKey: Boolean get() = gemini.hasKey

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _uiState.value.thinking) return
        val turns = _uiState.value.turns + ChatTurn(fromUser = true, text = text)
        _uiState.value = AskUiState(turns = turns, thinking = true)
        viewModelScope.launch {
            val since = System.currentTimeMillis() - WINDOW_MILLIS
            val result = runCatching {
                val system = AiPrompts.askSystemPrompt(
                    readings = glucoseRepository.readingsSince(since).first(),
                    events = logbookRepository.eventsSince(since).first(),
                    now = Instant.now(),
                    zone = ZoneId.systemDefault(),
                    insulinAction = insulinAction(),
                    dose = doseSettings(),
                    profile = profile(),
                )
                gemini.generate(system, turns)
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { reply -> state.copy(turns = state.turns + ChatTurn(fromUser = false, text = reply), thinking = false) },
                    onFailure = { e ->
                        state.copy(turns = state.turns.dropLast(1), thinking = false, error = e.message ?: "Something went wrong.", unanswered = text)
                    },
                )
            }
        }
    }

    fun clear() {
        if (!_uiState.value.thinking) _uiState.value = AskUiState()
    }

    companion object {
        private val WINDOW_MILLIS = TimeUnit.DAYS.toMillis(7)

        fun factory(glucoseRepository: GlucoseRepository, logbookRepository: LogbookRepository, gemini: GeminiClient, doseSettings: () -> DoseSettings = { DoseSettings() }, profile: () -> Profile = { Profile() }, insulinAction: () -> InsulinAction = { InsulinAction() }) = viewModelFactory {
            initializer { AskViewModel(glucoseRepository, logbookRepository, gemini, doseSettings, profile, insulinAction) }
        }
    }
}
