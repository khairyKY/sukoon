package com.sukoon.app.di

import android.content.Context
import com.sukoon.app.data.db.AppDatabase
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.source.GlucoseSource
import com.sukoon.app.data.source.SimulatedSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container (ponytail: no Hilt/Koin for a graph this small). Owns the
 * app-lifetime singletons — the DB, the app CoroutineScope, the active glucose source, and the
 * repository — and starts the persistence collector once at app start.
 *
 * To swap in real-sensor reading later (Track B), change [source] to LibreBleSource; nothing
 * downstream (repository, ViewModels, UI) changes.
 */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = AppDatabase.get(context)

    private val source: GlucoseSource = SimulatedSource(scope = appScope)

    val glucoseRepository: GlucoseRepository = GlucoseRepository(
        source = source,
        readingDao = database.readingDao(),
        scope = appScope,
    )

    init {
        glucoseRepository.start()
    }
}
