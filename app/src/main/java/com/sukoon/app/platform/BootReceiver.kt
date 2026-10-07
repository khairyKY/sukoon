package com.sukoon.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sukoon.app.SukoonApp

/**
 * After a phone restart or an app update: just being woken starts the app process, and with it
 * the AppContainer (SukoonApp.onCreate), which reconnects the sensor, re-arms the alarms and
 * resumes sharing and following. Before this, all of that waited until Sukoon was opened.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as SukoonApp).container // touch: make sure it's built
    }
}
