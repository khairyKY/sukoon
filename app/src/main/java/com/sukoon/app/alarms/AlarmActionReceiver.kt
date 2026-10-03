package com.sukoon.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sukoon.app.SukoonApp
import kotlinx.coroutines.launch

/** Notification buttons and swipe-away: silence the sound; a button press also snoozes. */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SNOOZE) return
        val type = AlarmType.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TYPE) } ?: return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val container = (context.applicationContext as SukoonApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.alarms.acknowledge(type, minutes)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.sukoon.app.alarms.SNOOZE"
        const val EXTRA_TYPE = "type"
        const val EXTRA_MINUTES = "minutes"
    }
}
