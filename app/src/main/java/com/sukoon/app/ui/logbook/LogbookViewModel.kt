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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.insights.MeterCheck
import kotlin.math.roundToInt
import com.sukoon.app.data.source.nearestTo
import com.sukoon.app.insulin.InsulinAction
import com.sukoon.app.insulin.InsulinOnBoard
import com.sukoon.app.ui.home.HomeUiStateMapper
import java.time.Duration
import com.sukoon.app.data.repository.EntryPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn

/** Drives the Logbook screen — today's window of logged events, newest first, from Room via [LogbookRepository]. */
class LogbookViewModel(
    private val repository: LogbookRepository,
    glucose: GlucoseRepository,
    private val gemini: GeminiClient,
    private val insulinAction: () -> InsulinAction = { InsulinAction() },
    private val photos: EntryPhotos? = null,
) : ViewModel() {
    /** Bumped after a photo is written, since the entry itself was saved (and shown) a moment before. */
    private val photoVersion = MutableStateFlow(0)


    private val since = System.currentTimeMillis() - WINDOW_MILLIS

    // Readings ride along so each finger-prick can show what the sensor said at that moment.
    val uiState: StateFlow<LogbookUiState> = combine(repository.eventsSince(since), glucose.readingsSince(since - MeterCheck.WINDOW_MS), photoVersion) { events, readings, _ ->
        LogbookUiState(
            events = events.sortedByDescending { it.timestampMillis },
            meterChecks = events.filter { it.logType == LogEventType.FINGERSTICK }.mapNotNull { e ->
                e.value?.let { MeterCheck.of(it.roundToInt(), e.timestampMillis, readings) }?.let { e.id to it }
            }.toMap(),
            glucoseAt = events.mapNotNull { e -> readings.nearestTo(e.timestampMillis)?.let { e.id to it } }.toMap(),
            insulinOnBoard = InsulinOnBoard.total(events, Instant.now(), insulinAction()),
            photos = photos?.all().orEmpty(),
            glucoseNow = readings.lastOrNull()?.takeIf { Duration.between(it.timestamp, Instant.now()) <= HomeUiStateMapper.STALE_AFTER },
        )
    }
        .flowOn(Dispatchers.Default) // nearest-reading lookups and the photo folder stay off the main thread
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = LogbookUiState(),
        )

    fun log(type: LogEventType, value: Double?, note: String?, at: Instant = Instant.now(), photo: ByteArray? = null) {
        viewModelScope.launch {
            val id = repository.log(type, value, note, at)
            if (photo != null) setPhotoNow(id, photo)
        }
    }

    /** A meal plus the rapid insulin for it; the insulin is stamped [preBolusMinutes] before the meal. */
    fun logMeal(carbs: Double, note: String?, insulinUnits: Double, preBolusMinutes: Int, at: Instant = Instant.now(), photo: ByteArray? = null) {
        viewModelScope.launch {
            val now = at
            repository.log(LogEventType.INSULIN, insulinUnits, null, at = now.minusSeconds(preBolusMinutes * 60L))
            val meal = repository.log(LogEventType.CARB, carbs, note, at = now)
            if (photo != null) setPhotoNow(meal, photo)
        }
    }

    fun updateEvent(event: EventEntity) {
        viewModelScope.launch { repository.update(event) }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch {
            repository.delete(event)
            setPhotoNow(event.id, null)
        }
    }

    /** Attach, replace (bytes) or remove (null) an entry's photo. */
    fun setPhoto(id: Long, jpeg: ByteArray?) {
        viewModelScope.launch { setPhotoNow(id, jpeg) }
    }

    private suspend fun setPhotoNow(id: Long, jpeg: ByteArray?) {
        val store = photos ?: return
        withContext(Dispatchers.IO) { if (jpeg != null) store.save(id, jpeg) else store.delete(id) }
        photoVersion.value++
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

        fun factory(repository: LogbookRepository, glucose: GlucoseRepository, gemini: GeminiClient, photos: EntryPhotos, insulinAction: () -> InsulinAction) = viewModelFactory {
            initializer { LogbookViewModel(repository, glucose, gemini, insulinAction, photos) }
        }
    }
}
