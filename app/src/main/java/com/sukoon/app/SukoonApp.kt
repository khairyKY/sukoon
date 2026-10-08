package com.sukoon.app

import com.sukoon.app.platform.TimeFormat
import com.sukoon.app.domain.metrics.TargetRange
import android.app.Application
import com.sukoon.app.di.AppContainer
import com.sukoon.app.data.backup.Backup

/**
 * Application entry point. Builds the [AppContainer] (which starts the glucose persistence
 * collector) once, at process start. Registered via android:name in the manifest.
 */
class SukoonApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Backup.applyStaged(this) // a restore waiting from the last run: in place before anything opens it
        container = AppContainer(this)
        TargetRange.high = container.settings.targetHigh
        TimeFormat.init(this, container.settings.timeFormat)
    }
}
