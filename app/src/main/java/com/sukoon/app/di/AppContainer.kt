package com.sukoon.app.di

import android.content.Context
import com.sukoon.app.ai.GeminiClient
import com.sukoon.app.alarms.AlarmMonitor
import com.sukoon.app.alarms.AlarmNotifier
import com.sukoon.app.data.db.AppDatabase
import com.sukoon.app.data.export.CsvExport
import com.sukoon.app.data.export.NightscoutUploader
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
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.sukoon.app.emergency.EmergencyAlerts
import com.sukoon.app.BuildConfig
import com.sukoon.app.sharing.Supabase
import com.sukoon.app.sharing.Sharing
import com.sukoon.app.sharing.FollowerWatch
import com.sukoon.app.health.HealthConnectSync
import com.sukoon.app.data.source.libre.SensorLife
import com.sukoon.app.data.source.libre.SensorLifecycle
import com.sukoon.app.platform.SensorLifeNotices
import com.sukoon.app.calibration.Calibration
import com.sukoon.app.calibration.CalibrationManager
import com.sukoon.app.insulin.InsulinOnBoard
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.sukoon.app.data.repository.EntryPhotos
import com.sukoon.app.alarms.AlarmLog
import com.sukoon.app.alarms.SignalWatchdog
import com.sukoon.app.reminders.BasalReminder
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.sharing.LibreLinkUp
import com.sukoon.app.sharing.CloudSource
import com.sukoon.app.sharing.DexcomShare
import com.sukoon.app.R
import com.sukoon.app.platform.Updates

/**
 * Manual dependency container (ponytail: no Hilt/Koin for a graph this small). Owns the
 * app-lifetime singletons — the DB, the app CoroutineScope, the active glucose source, the
 * repositories and the AI client — and starts the persistence collector once at app start.
 *
 * The active source follows [sourceKind] (You tab): the paired Libre sensor, or demo data.
 */
class AppContainer(private val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    internal val database = AppDatabase.get(context)

    val settings = SettingsPrefs(context)

    val pairingStore = SensorPairingStore(context)

    /** You → Appearance, applied at once by MainActivity. */
    val themeMode = MutableStateFlow(settings.themeMode)

    /** Wearer, follower or both (chosen at sign-up): what Home and Trends show. */
    val role = MutableStateFlow(settings.role)

    private val _sourceKind = MutableStateFlow(settings.sourceKind)
    val sourceKind: StateFlow<SourceKind> = _sourceKind.asStateFlow()

    /** Abbott's follow service: people sharing from Abbott's Libre app (Libre 3 too). */
    val libreLinkUp = LibreLinkUp(context)

    /** Updates from Sukoon's GitHub releases. */
    val updates = Updates(context, appScope)

    /** Dexcom Share: a Dexcom wearer's readings through Dexcom's servers. */
    val dexcom = DexcomShare(context)

    private val source = MutableStateFlow(sourceFor(_sourceKind.value))

    private val calibrationInForce = MutableStateFlow<Calibration?>(null)

    val glucoseRepository: GlucoseRepository = GlucoseRepository(
        sources = source,
        readingDao = database.readingDao(),
        scope = appScope,
        saveIntervalMinutes = { settings.saveIntervalMinutes },
        calibration = calibrationInForce,
    )

    val logbookRepository: LogbookRepository = LogbookRepository(eventDao = database.eventDao())

    val entryPhotos = EntryPhotos(context.filesDir)

    val calibration = CalibrationManager(context, calibrationInForce, glucoseRepository, logbookRepository, appScope)

    /** Rapid insulin still active (Logbook doses on the exponential curve): every minute, and at once on a new dose. */
    val insulinOnBoard: Flow<Double> = merge(
        logbookRepository.eventsSince(0).map { },
        ticks(60_000),
    ).map {
        val now = Instant.now()
        InsulinOnBoard.total(logbookRepository.eventsSince(now.minus(Duration.ofHours(9)).toEpochMilli()).first(), now, settings.insulinAction)
    }

    val gemini = GeminiClient(apiKey = { settings.geminiApiKey })

    val nightscout = NightscoutUploader(context, glucoseRepository, logbookRepository, appScope)

    val emergency = EmergencyAlerts(context, settings)

    val supabase = Supabase(context, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)

    val sharing = Sharing(context, supabase, glucoseRepository, appScope, libreLinkUp, dexcom)

    /** What the alarms did (You → Alarms). */
    val alarmLog = AlarmLog(context)

    private val notifier = AlarmNotifier(context, alarmLog)

    val alarms = AlarmMonitor(
        repository = glucoseRepository,
        settings = settings,
        notifier = notifier,
        log = alarmLog,
        emergency = emergency,
        scope = appScope,
        enabled = { _sourceKind.value.real },
        watchdog = { SignalWatchdog.arm(context, it) },
    )

    val followerWatch = FollowerWatch(context, sharing, settings, notifier, appScope)

    val healthConnect = HealthConnectSync(context, glucoseRepository, logbookRepository, appScope)

    /** The paired sensor's life while it's the source (null on demo data or with no sensor). */
    fun sensorLife(now: Instant = Instant.now()): SensorLife? =
        if (_sourceKind.value != SourceKind.LIBRE_BLE) null else pairingStore.load()?.let { SensorLifecycle.of(it.startMillis, it.lifetimeMinutes, now) }

    private val sensorNotices = SensorLifeNotices(context) {
        pairingStore.load()
            ?.takeIf { _sourceKind.value == SourceKind.LIBRE_BLE }
            ?.let { it.serial to SensorLifecycle.of(it.startMillis, it.lifetimeMinutes, Instant.now()) }
    }

    /** A new entry something outside the app asked for (a reminder's "Log it"); MainScaffold opens it. */
    val requestedEntry = MutableStateFlow<LogEventType?>(null)

    init {
        glucoseRepository.start()
        BasalReminder.schedule(context, settings.basalReminder)
        updates.checkIfDue()
        // Long-acting logged anywhere clears its reminder.
        appScope.launch {
            logbookRepository.eventsSince(System.currentTimeMillis() - Duration.ofHours(12).toMillis()).collect { events ->
                if (BasalReminder.taken(events, Instant.now())) BasalReminder.dismiss(context)
            }
        }
        if (_sourceKind.value.real) SensorService.start(context)
        refreshWidgets()
        alarms.start()
        nightscout.start()
        sharing.start()
        followerWatch.start()
        healthConnect.start()
        calibration.start()
        // The sensor's ongoing notification shows the newest reading; re-drawn each minute for its age.
        appScope.launch {
            combine(glucoseRepository.latestReading, ticks(60_000)) { reading, _ -> reading }.collect { reading ->
                if (_sourceKind.value.real) SensorService.show(context, reading, Instant.now())
            }
        }
        appScope.launch {
            while (true) {
                runCatching { sensorNotices.check() }
                delay(5 * 60_000L)
            }
        }
    }

    private fun ticks(periodMillis: Long) = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
    }

    /** CSV of the last [days] days (0 = everything) → (text, data rows). */
    suspend fun exportCsv(days: Int): Pair<String, Int> {
        val since = if (days <= 0) 0L else System.currentTimeMillis() - days * 86_400_000L
        val readings = glucoseRepository.readingsSince(since).first()
        val events = logbookRepository.eventsSince(since).first()
        return CsvExport.build(readings, events, ZoneId.systemDefault()) to readings.size + events.size
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
            merge(glucoseRepository.latestReading.map { }, logbookRepository.eventsSince(0).map { }, followerWatch.people.map { }, minuteTicks).conflate().collect {
                runCatching { GlucoseWidget.refreshAll(context) }
                delay(WIDGET_MIN_INTERVAL_MS)
            }
        }
    }

    fun selectSource(kind: SourceKind) {
        settings.sourceKind = kind
        _sourceKind.value = kind
        source.value = sourceFor(kind) // a fresh instance even for the same kind: re-pairing restarts the connection
        if (kind.real) SensorService.start(context) else SensorService.stop(context)
    }

    /**
     * Called after an NFC tap that enabled streaming: remembers the sensor, backfills the ~8 h of
     * history its memory holds, and switches to it. [scannedAtMillis] anchors sensor minute 0.
     */
    fun pairSensor(read: LibreNfc.SensorRead, scannedAtMillis: Long) {
        val fram = read.fram ?: return
        val pairing = SensorPairing(read.uid, read.patchInfo, fram, scannedAtMillis - Libre2.sensorInfo(fram).ageMinutes * LibreBleSource.MINUTE_MS)
        pairingStore.save(pairing)
        pairingStore.bleAddress = read.bleMac
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
        // ponytail: shares the connected-device foreground service that keeps the process alive; a
        // data-sync service of its own if a phone ever refuses it without Bluetooth permission.
        SourceKind.LIBRE_LINK_UP -> CloudSource(
            fetch = { libreLinkUp.ownPatientId?.let { libreLinkUp.readingsOf(it, 0) } },
            notSetUp = context.getString(R.string.source_llu_not_set),
            scope = appScope,
        )
        SourceKind.DEXCOM_SHARE -> CloudSource(
            fetch = { if (dexcom.account.value == null) null else dexcom.readings() },
            notSetUp = context.getString(R.string.source_dexcom_not_set),
            scope = appScope,
        )
    }
}
