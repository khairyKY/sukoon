package com.sukoon.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sukoon.app.data.repository.GlucoseRepository
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import com.sukoon.app.data.source.libre.SensorLife
import kotlinx.coroutines.flow.map
import com.sukoon.app.data.repository.LogbookRepository
import com.sukoon.app.insulin.InsulinAction
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn

/**
 * Drives the Home screen. Thin by design: it just observes the app-scoped [GlucoseRepository]'s
 * Room-backed Flows (latest reading + recent window + source status) and maps them to a
 * [HomeUiState] via the pure, unit-tested [HomeUiStateMapper]. It no longer owns the source or
 * any in-memory buffer — persistence and the source lifecycle live in the repository (A2), so
 * data keeps flowing while the user is on other tabs and survives config changes / relaunch.
 */
class HomeViewModel(
    repository: GlucoseRepository,
    private val lifeOf: () -> SensorLife? = { null },
    logbook: LogbookRepository? = null,
    treatedAt: Flow<Instant?> = flowOf(null),
    private val actionOf: () -> InsulinAction = { InsulinAction() },
    /** Today's steps and water from Health Connect (absent: not allowed or nothing logged). */
    private val healthToday: (suspend () -> Map<HomeStat, Double>)? = null,
) : ViewModel() {

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
        HomeUiStateMapper.map(status, latest, recent, Instant.now(), lifeOf())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState.NoSensor,
    )

    /**
     * Home's message and next step ([HomeBriefs]) from the last day of readings and logbook, judged
     * again on every reading and tick. ponytail: the window starts when Home opens and only grows
     * while it stays open; the rules look back from now, so older rows only cost a little memory.
     */
    private val since = System.currentTimeMillis() - BRIEF_WINDOW_MS
    private val dayReadings = repository.readingsSince(since).shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)
    private val dayEvents = (logbook?.eventsSince(since) ?: flowOf(emptyList())).shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    val brief: StateFlow<Brief?> = combine(dayReadings, dayEvents, treatedAt, ticker) { readings, events, treated, _ ->
        HomeBriefs.of(readings, events, Instant.now(), ZoneId.systemDefault(), actionOf(), lifeOf(), treated)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Health Connect's steps and water, asked again every few minutes (they come from other apps). */
    private val health = flow {
        while (true) {
            emit(healthToday?.let { runCatching { it() }.getOrNull() }.orEmpty())
            delay(HEALTH_EVERY_MS)
        }
    }

    /** Home's chosen numbers for today ([todayStats] plus Health Connect's). */
    val stats: StateFlow<Map<HomeStat, Double>> = combine(dayReadings, dayEvents, health, ticker) { readings, events, fromHealth, _ ->
        todayStats(events, readings, Instant.now(), ZoneId.systemDefault()) + fromHealth
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** The paired sensor's life, for Home's "ends soon" banner (null on demo data). */
    val sensorLife: StateFlow<SensorLife?> = ticker.map { lifeOf() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    companion object {
        // Home chart window: 180 samples ≈ its 3 hours at the sensor's 1-minute cadence (the chart
        // itself cuts at 3 hours, so a sparser save interval just draws fewer points).
        private const val WINDOW_SIZE = 180
        private const val BRIEF_WINDOW_MS = 24 * 3_600_000L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TICK_MS = 30_000L
        private const val HEALTH_EVERY_MS = 5 * 60_000L

        fun factory(
            repository: GlucoseRepository,
            lifeOf: () -> SensorLife? = { null },
            logbook: LogbookRepository? = null,
            treatedAt: Flow<Instant?> = flowOf(null),
            actionOf: () -> InsulinAction = { InsulinAction() },
            healthToday: (suspend () -> Map<HomeStat, Double>)? = null,
        ) = viewModelFactory {
            initializer { HomeViewModel(repository, lifeOf, logbook, treatedAt, actionOf, healthToday) }
        }
    }
}
