package com.sukoon.app.data.repository

import com.sukoon.app.data.db.ReadingDao
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.GlucoseSource
import com.sukoon.app.data.source.SourceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The single source of truth for glucose data (A2, docs/track-a-plan.md). It runs a collector
 * that persists every reading from the active [GlucoseSource] into Room, and exposes Room-backed
 * Flows the UI observes — so the display survives navigation, config changes, and app relaunch,
 * and so Logbook/Graph/Insights all read the same persisted stream.
 *
 * Held app-scoped (see di/AppContainer) rather than per-ViewModel, so persistence keeps running
 * while the user is on other tabs. (Surviving full backgrounding/kill is the foreground service,
 * A11.) The source is swappable: SimulatedSource today, LibreBleSource later — same repository.
 */
class GlucoseRepository(
    private val source: GlucoseSource,
    private val readingDao: ReadingDao,
    private val scope: CoroutineScope,
) {
    val status: StateFlow<SourceStatus> = source.status

    val latestReading: Flow<GlucoseReading?> =
        readingDao.latest().map { it?.toGlucoseReading() }

    /** Most-recent [limit] readings in chronological order (oldest → newest) for the graph. */
    fun recentReadings(limit: Int): Flow<List<GlucoseReading>> =
        readingDao.latestN(limit).map { rows -> rows.asReversed().map { it.toGlucoseReading() } }

    /** Readings at or after [sinceMillis], chronological — backs the time-ranged graph (A3). */
    fun readingsSince(sinceMillis: Long): Flow<List<GlucoseReading>> =
        readingDao.since(sinceMillis).map { rows -> rows.map { it.toGlucoseReading() } }

    /** Starts the source and the persist-to-Room collector. Idempotent-safe to call once at app start. */
    fun start() {
        scope.launch { source.connect() }
        scope.launch {
            source.readings.collect { reading ->
                readingDao.insert(reading.toEntity())
            }
        }
    }
}
