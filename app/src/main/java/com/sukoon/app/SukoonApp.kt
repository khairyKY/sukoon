package com.sukoon.app

import android.app.Application
import com.sukoon.app.di.AppContainer

/**
 * Application entry point. Builds the [AppContainer] (which starts the glucose persistence
 * collector) once, at process start. Registered via android:name in the manifest.
 */
class SukoonApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
