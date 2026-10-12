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
import kotlinx.coroutines.flow.combine
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.domain.metrics.Spikes
import kotlinx.coroutines.launch
import com.sukoon.app.insulin.InsulinAction

data class AskUiState(
    val turns: List<ChatTurn> = emptyList(),
    val thinking: Boolean = false,
    val error: String? = null,
    /** A question whose answer failed — dropped from [turns] (Gemini needs strictly alternating turns) and handed back to the input box. */
    val unanswered: String? = null,
    /** What the answers are based on, shown under the title. */
    val basedOn: BasedOn? = null,
)

/** The same data the AI gets (AiPrompts.dataBrief): real readings, or demo only when that's all there is. */
data class BasedOn(val days: Int, val readings: Int, val entries: Int, val demo: Boolean)

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

    init {
        val since = System.currentTimeMillis() - WINDOW_MILLIS
        viewModelScope.launch {
            combine(glucoseRepository.readingsSince(since), logbookRepository.eventsSince(since)) { readings, events ->
                val real = readings.filter { it.source != SourceKind.SIMULATED }
                val demo = real.isEmpty() && readings.isNotEmpty()
                val shown = Spikes.without(if (demo) readings else real)
                BasedOn(
                    days = shown.map { it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() }.distinct().size,
                    readings = shown.size,
                    entries = events.size,
                    demo = demo,
                )
            }.collect { based -> _uiState.update { it.copy(basedOn = based) } }
        }
    }

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _uiState.value.thinking) return
        val turns = _uiState.value.turns + ChatTurn(fromUser = true, text = text)
        _uiState.update { it.copy(turns = turns, thinking = true, error = null, unanswered = null) }
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
        if (!_uiState.value.thinking) _uiState.update { AskUiState(basedOn = it.basedOn) }
    }

    companion object {
        private val WINDOW_MILLIS = TimeUnit.DAYS.toMillis(7)

        fun factory(glucoseRepository: GlucoseRepository, logbookRepository: LogbookRepository, gemini: GeminiClient, doseSettings: () -> DoseSettings = { DoseSettings() }, profile: () -> Profile = { Profile() }, insulinAction: () -> InsulinAction = { InsulinAction() }) = viewModelFactory {
            initializer { AskViewModel(glucoseRepository, logbookRepository, gemini, doseSettings, profile, insulinAction) }
        }
    }
}
