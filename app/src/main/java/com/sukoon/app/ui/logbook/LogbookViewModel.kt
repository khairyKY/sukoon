package com.sukoon.app.ui.logbook

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.ZoneId
import java.time.LocalDate
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

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
    private val day = MutableStateFlow<LocalDate?>(null)

    /** Look back at [date]'s entries (null: back to the last 24 hours). */
    fun showDay(date: LocalDate?) {
        day.value = date
    }

    /** A past day's entries, with its readings from half an hour before to 4 hours after (what its last meal did). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pastDay = day.flatMapLatest { d ->
        if (d == null) {
            flowOf(null)
        } else {
            val zone = ZoneId.systemDefault()
            val from = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            combine(repository.eventsBetween(from, to), glucose.readingsBetween(from - MeterCheck.WINDOW_MS, to + TimeUnit.HOURS.toMillis(4))) { e, r -> Triple(d, e, r) }
        }
    }

    // Readings ride along so each finger-prick can show what the sensor said at that moment. Events
    // load for a month: the doses' injection sites suggest the next spot; the list shows the window.
    val uiState: StateFlow<LogbookUiState> = combine(repository.eventsSince(since - SITES_MILLIS), glucose.readingsSince(since - MeterCheck.WINDOW_MS), photoVersion, pastDay) { month, latest, _, past ->
        val events = past?.second ?: month.filter { it.timestampMillis >= since }
        val readings = past?.third ?: latest
        LogbookUiState(
            events = events.sortedByDescending { it.timestampMillis },
            siteHistory = month.filter { it.site != null },
            meterChecks = events.filter { it.logType == LogEventType.FINGERSTICK }.mapNotNull { e ->
                e.value?.let { MeterCheck.of(it.roundToInt(), e.timestampMillis, readings) }?.let { e.id to it }
            }.toMap(),
            glucoseAt = events.mapNotNull { e -> readings.nearestTo(e.timestampMillis)?.let { e.id to it } }.toMap(),
            insulinOnBoard = InsulinOnBoard.total(events, Instant.now(), insulinAction()),
            photos = photos?.all().orEmpty(),
            glucoseNow = readings.lastOrNull()?.takeIf { Duration.between(it.timestamp, Instant.now()) <= HomeUiStateMapper.STALE_AFTER },
            readings = readings,
            day = past?.first,
        )
    }
        .flowOn(Dispatchers.Default) // nearest-reading lookups and the photo folder stay off the main thread
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = LogbookUiState(),
        )

    /**
     * Saves [draft] (a meal's insulin is stamped its pre-bolus minutes before the meal) and returns
     * the new entries' ids, for Undo. Runs in the ViewModel's scope, so leaving the screen can't cut
     * a save in half.
     */
    fun save(draft: EntryDraft): Deferred<List<Long>> = viewModelScope.async {
        val ids = mutableListOf<Long>()
        val n = draft.nutrients
        val id = repository.insert(
            EventEntity(
                timestampMillis = draft.at.toEpochMilli(),
                type = draft.type.name,
                value = draft.amount,
                note = draft.note,
                site = draft.site?.name?.takeIf { draft.type == LogEventType.INSULIN || draft.type == LogEventType.BASAL },
                mealId = draft.mealId,
                fiber = n?.fiber,
                sugar = n?.sugar,
                protein = n?.protein,
                fat = n?.fat,
                kcal = n?.kcal,
            ),
        )
        ids += id
        // A meal saved with its rapid insulin: the dose is linked to it (and stamped its pre-bolus minutes before).
        draft.insulin?.takeIf { it > 0 }?.let { units ->
            val at = draft.at.minusSeconds(draft.preBolusMinutes * 60L)
            ids += repository.insert(EventEntity(timestampMillis = at.toEpochMilli(), type = LogEventType.INSULIN.name, value = units, site = draft.site?.name, mealId = id))
        }
        if (draft.photos.isNotEmpty()) setPhotoNow(id, draft.photos)
        ids
    }

    /** Takes back what a save just added. */
    fun undo(ids: List<Long>) {
        viewModelScope.launch {
            ids.forEach { id ->
                repository.deleteById(id)
                setPhotoNow(id, emptyList())
            }
        }
    }

    fun updateEvent(event: EventEntity) {
        viewModelScope.launch { repository.update(event) }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch {
            repository.delete(event)
            setPhotoNow(event.id, emptyList())
        }
    }

    /** An entry's photos become [jpegs] (none: removed). */
    fun setPhotos(id: Long, jpegs: List<ByteArray>) {
        viewModelScope.launch { setPhotoNow(id, jpegs) }
    }

    private suspend fun setPhotoNow(id: Long, jpegs: List<ByteArray>) {
        val store = photos ?: return
        withContext(Dispatchers.IO) { store.save(id, jpegs) }
        photoVersion.value++
    }

    /** AI carb estimate for the quick-entry sheet — a suggestion only; the sheet's Save logs it. */
    suspend fun estimateCarbs(description: String, photos: List<ByteArray>): CarbEstimate {
        val prompt = description.ifBlank { if (photos.size > 1) "Estimate the carbs in this meal: these ${photos.size} photos are all the same meal." else "Estimate the carbs in this meal." }
        val arabic = Locale.getDefault().language == "ar"
        val reply = gemini.generate(AiPrompts.carbSystemPrompt(arabic), listOf(ChatTurn(fromUser = true, text = prompt)), photos, jsonOutput = true)
        return CarbEstimate.parse(reply)
    }

    companion object {
        private val WINDOW_MILLIS = TimeUnit.HOURS.toMillis(24)
        private val SITES_MILLIS = TimeUnit.DAYS.toMillis(30)

        fun factory(repository: LogbookRepository, glucose: GlucoseRepository, gemini: GeminiClient, photos: EntryPhotos, insulinAction: () -> InsulinAction) = viewModelFactory {
            initializer { LogbookViewModel(repository, glucose, gemini, insulinAction, photos) }
        }
    }
}
