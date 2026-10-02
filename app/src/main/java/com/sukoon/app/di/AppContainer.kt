package com.sukoon.app.di

import android.content.Context
import com.sukoon.app.ai.GeminiClient
import com.sukoon.app.data.db.AppDatabase
import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.repository.LogbookRepository
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.GlucoseSource
import com.sukoon.app.data.source.SimulatedSource
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.data.source.libre.Libre2
import com.sukoon.app.data.source.libre.LibreBleSource
import com.sukoon.app.data.source.libre.LibreNfc
import com.sukoon.app.data.source.libre.SensorPairing
import com.sukoon.app.data.source.libre.SensorPairingStore
import com.sukoon.app.platform.SensorService
import com.sukoon.app.ui.widget.GlucoseWidget
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manual dependency container (ponytail: no Hilt/Koin for a graph this small). Owns the
 * app-lifetime singletons — the DB, the app CoroutineScope, the active glucose source, the
 * repositories and the AI client — and starts the persistence collector once at app start.
 *
 * The active source follows [sourceKind] (You tab): the paired Libre sensor, or demo data.
 */
class AppContainer(private val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = AppDatabase.get(context)

    val settings = SettingsPrefs(context)

    val pairingStore = SensorPairingStore(context)

    private val _sourceKind = MutableStateFlow(settings.sourceKind)
    val sourceKind: StateFlow<SourceKind> = _sourceKind.asStateFlow()

    private val source = MutableStateFlow(sourceFor(_sourceKind.value))

    val glucoseRepository: GlucoseRepository = GlucoseRepository(
        sources = source,
        readingDao = database.readingDao(),
        scope = appScope,
    )

    val logbookRepository: LogbookRepository = LogbookRepository(eventDao = database.eventDao())

    val gemini = GeminiClient(apiKey = { settings.geminiApiKey })

    init {
        glucoseRepository.start()
        if (_sourceKind.value == SourceKind.LIBRE_BLE) SensorService.start(context)
        refreshWidgets()
    }

    /**
     * Keeps home-screen widgets current: on every new reading, and once a minute so "x min ago"
     * and the stale grey-out move on even when no reading arrives. conflate + delay caps it at one
     * re-render per 15 s (demo data ticks every 2 s) while always ending on the latest state.
     */
    private fun refreshWidgets() {
        val minuteTicks = flow {
            while (true) {
                emit(Unit)
                delay(MINUTE_MS)
            }
        }
        appScope.launch {
            merge(glucoseRepository.latestReading.map { }, minuteTicks).conflate().collect {
                runCatching { GlucoseWidget.refreshAll(context) }
                delay(WIDGET_MIN_INTERVAL_MS)
            }
        }
    }

    fun selectSource(kind: SourceKind) {
        settings.sourceKind = kind
        _sourceKind.value = kind
        source.value = sourceFor(kind) // a fresh instance even for the same kind: re-pairing restarts the connection
        if (kind == SourceKind.LIBRE_BLE) SensorService.start(context) else SensorService.stop(context)
    }

    /**
     * Called after an NFC tap that enabled streaming: remembers the sensor, backfills the ~8 h of
     * history its memory holds, and switches to it. [scannedAtMillis] anchors sensor minute 0.
     */
    fun pairSensor(read: LibreNfc.SensorRead, scannedAtMillis: Long) {
        val fram = read.fram ?: return
        val pairing = SensorPairing(read.uid, read.patchInfo, fram, scannedAtMillis - Libre2.sensorInfo(fram).ageMinutes * LibreBleSource.MINUTE_MS)
        pairingStore.save(pairing)
        appScope.launch {
            for (point in Libre2.parseFram(pairing.calibration, fram)) {
                if (point.mgDl !in LibreBleSource.PLAUSIBLE_MG_DL) continue
                val at = Instant.ofEpochMilli(pairing.startMillis + point.minute * LibreBleSource.MINUTE_MS)
                glucoseRepository.ingest(GlucoseReading(at, point.mgDl, TrendDirection.STEADY, SourceKind.LIBRE_BLE))
            }
        }
        selectSource(SourceKind.LIBRE_BLE)
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val WIDGET_MIN_INTERVAL_MS = 15_000L
    }

    private fun sourceFor(kind: SourceKind): GlucoseSource = when (kind) {
        SourceKind.LIBRE_BLE -> LibreBleSource(context, pairingStore)
        SourceKind.SIMULATED -> SimulatedSource(scope = appScope)
    }
}
