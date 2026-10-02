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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * A11.) The active source is a [StateFlow] so the You tab can switch it live (Demo ↔ DiaBox
 * broadcast ↔ Nightscout ↔ later LibreBleSource) — same repository, nothing downstream changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseRepository(
    private val sources: StateFlow<GlucoseSource>,
    private val readingDao: ReadingDao,
    private val scope: CoroutineScope,
) {
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

    /** For push-style sources (the xDrip broadcast receiver) that deliver outside the collector. */
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
                    launch { source.readings.collect { readingDao.insert(it.toEntity()) } }
                    source.connect()
                }
            }
        }
    }
}
