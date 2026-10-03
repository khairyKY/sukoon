package com.sukoon.app.data.repository

import com.sukoon.app.data.db.ReadingDao
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.GlucoseSource
import com.sukoon.app.data.source.SimulatedSource
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.SourceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The single source of truth for glucose data (A2, docs/track-a-plan.md). It runs a collector
 * that persists every reading from the active [GlucoseSource] into Room, and exposes Room-backed
 * Flows the UI observes — so the display survives navigation, config changes, and app relaunch,
 * and so Logbook/Graph/Insights all read the same persisted stream.
 *
 * Held app-scoped (see di/AppContainer) rather than per-ViewModel, so persistence keeps running
 * while the user is on other tabs. (Surviving full backgrounding/kill is the foreground service,
 * A11.) The active source is a [StateFlow] so the You tab can switch it live (Demo ↔ the paired
 * Libre sensor) — same repository, nothing downstream changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseRepository(
    private val sources: StateFlow<GlucoseSource>,
    private val readingDao: ReadingDao,
    private val scope: CoroutineScope,
    /** Minimum minutes between saved readings (You → Readings: 1, 2, 3, 5 or 15). */
    private val saveIntervalMinutes: () -> Int = { 1 },
) {
    private val _live = MutableSharedFlow<GlucoseReading>(extraBufferCapacity = 64)

    /**
     * Every reading the source delivers, before the save-interval filter. Alarms watch this, so a
     * sparser saved log can never delay an urgent-low alert.
     */
    val liveReadings: SharedFlow<GlucoseReading> = _live.asSharedFlow()

    val status: StateFlow<SourceStatus> = sources
        .flatMapLatest { it.status }
        .stateIn(scope, SharingStarted.Eagerly, SourceStatus.Disconnected)

    val latestReading: Flow<GlucoseReading?> =
        readingDao.latest().map { it?.toGlucoseReading() }

    /** Most-recent [limit] readings in chronological order (oldest → newest) for the graph. */
    fun recentReadings(limit: Int): Flow<List<GlucoseReading>> =
        readingDao.latestN(limit).map { rows -> rows.asReversed().map { it.toGlucoseReading() } }

    /** Readings at or after [sinceMillis], chronological — backs the time-ranged graph (A3). */
    fun readingsSince(sinceMillis: Long): Flow<List<GlucoseReading>> =
        readingDao.since(sinceMillis).map { rows -> rows.map { it.toGlucoseReading() } }

    /** For readings that arrive outside the collector — the NFC pairing tap's 8-hour backfill. */
    suspend fun ingest(reading: GlucoseReading) = readingDao.insert(reading.toEntity())

    /**
     * Starts the persist-to-Room collector for whichever source is active, restarting it on every
     * switch (collectLatest cancels the old collection; disconnect stops a hot source's own job).
     * Call once at app start.
     */
    fun start() {
        scope.launch {
            var previous: GlucoseSource? = null
            sources.collectLatest { source ->
                previous?.disconnect()
                previous = source
                if (source !is SimulatedSource) readingDao.deleteBySource(SourceKind.SIMULATED.name)
                // coroutineScope (not the outer launch's scope) so collectLatest's cancel reaches it.
                coroutineScope {
                    launch {
                        source.readings.collect { reading ->
                            _live.tryEmit(reading)
                            if (shouldSave(reading, saveIntervalMinutes())) readingDao.insert(reading.toEntity())
                        }
                    }
                    source.connect()
                }
            }
        }
    }

    /**
     * Spacing rule: save a reading only if no saved reading lies within (interval − 30 s) of it.
     * Works for any interval (unlike aligning to a clock grid), keeps one live reading every N
     * minutes, and still lets a reconnect's backfill fill a real gap — without re-adding the minutes
     * that were skipped live. Duplicates of an already-saved reading fall out the same way.
     */
    private suspend fun shouldSave(reading: GlucoseReading, intervalMinutes: Int): Boolean {
        if (intervalMinutes <= 1) return true // every reading; the unique index drops exact repeats
        val t = reading.timestamp.toEpochMilli()
        val margin = intervalMinutes * 60_000L - 30_000L
        return readingDao.countBetween(t - margin, t + margin) == 0
    }
}
