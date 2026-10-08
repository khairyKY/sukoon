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
        // 0.8.1 adds Standard Arabic as "ar", so an earlier "ar" pick (which was Egyptian) moves to "ar-EG", once.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val prefs = getSharedPreferences("sukoon_prefs", MODE_PRIVATE)
            if (!prefs.getBoolean("locale_eg_moved", false)) {
                val manager = getSystemService(android.app.LocaleManager::class.java)
                if (manager.applicationLocales.toLanguageTags() == "ar") manager.applicationLocales = android.os.LocaleList.forLanguageTags("ar-EG")
                prefs.edit().putBoolean("locale_eg_moved", true).apply()
            }
        }
    }
}
