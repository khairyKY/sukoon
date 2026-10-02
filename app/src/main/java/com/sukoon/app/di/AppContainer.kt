package com.sukoon.app.di

import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.sukoon.app.ai.GeminiClient
import com.sukoon.app.data.db.AppDatabase
import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.data.prefs.SourceConfig
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.repository.LogbookRepository
import com.sukoon.app.data.source.BroadcastSource
import com.sukoon.app.data.source.GlucoseSource
import com.sukoon.app.data.source.NightscoutSource
import com.sukoon.app.data.source.SimulatedSource
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.XDripBroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manual dependency container (ponytail: no Hilt/Koin for a graph this small). Owns the
 * app-lifetime singletons — the DB, the app CoroutineScope, the active glucose source, the
 * repositories and the AI client — and starts the persistence collector once at app start.
 *
 * The active source follows [sourceConfig] (You tab). Track B adds a LIBRE_BLE branch to
 * [sourceFor]; nothing downstream (repository, ViewModels, UI) changes.
 */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = AppDatabase.get(context)

    val settings = SettingsPrefs(context)

    private val _sourceConfig = MutableStateFlow(settings.loadSourceConfig())
    val sourceConfig: StateFlow<SourceConfig> = _sourceConfig.asStateFlow()

    private val source = MutableStateFlow(sourceFor(_sourceConfig.value))

    val glucoseRepository: GlucoseRepository = GlucoseRepository(
        sources = source,
        readingDao = database.readingDao(),
        scope = appScope,
    )

    val logbookRepository: LogbookRepository = LogbookRepository(eventDao = database.eventDao())

    val gemini = GeminiClient(apiKey = { settings.geminiApiKey })

    init {
        glucoseRepository.start()
        // Runtime twin of the manifest receiver: Android 8+ only delivers *implicit* broadcasts
        // to receivers registered by a live process (see XDripBroadcastReceiver).
        ContextCompat.registerReceiver(
            context,
            XDripBroadcastReceiver(),
            IntentFilter(XDripBroadcastReceiver.ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun updateSourceConfig(config: SourceConfig) {
        settings.saveSourceConfig(config)
        _sourceConfig.value = config
        source.value = sourceFor(config)
    }

    private fun sourceFor(config: SourceConfig): GlucoseSource = when (config.kind) {
        SourceKind.NIGHTSCOUT -> NightscoutSource(config.nightscoutUrl, config.nightscoutToken)
        SourceKind.BROADCAST -> BroadcastSource
        // LIBRE_BLE isn't selectable until Track B ships it; demo data until then.
        SourceKind.SIMULATED, SourceKind.LIBRE_BLE -> SimulatedSource(scope = appScope)
    }
}
