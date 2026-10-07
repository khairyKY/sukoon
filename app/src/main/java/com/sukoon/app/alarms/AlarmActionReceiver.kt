package com.sukoon.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sukoon.app.SukoonApp
import kotlinx.coroutines.launch

/**
 * Notification buttons and swipe-away: silence the sound (any of these counts as answering the
 * alarm); a button press also snoozes. "I'm OK" answers an emergency countdown.
 */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val imOk = intent.action == ACTION_IM_OK
        val type = AlarmType.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TYPE) }
        if (!imOk && (intent.action != ACTION_SNOOZE || type == null)) return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val person = intent.getStringExtra(EXTRA_PERSON) // set on alerts about someone this phone follows
        val container = (context.applicationContext as SukoonApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                when {
                    imOk -> container.alarms.imOk()
                    person != null -> container.followerWatch.acknowledge(person, requireNotNull(type), minutes)
                    else -> container.alarms.acknowledge(requireNotNull(type), minutes, treated = intent.getBooleanExtra(EXTRA_TREATED, false))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.sukoon.app.alarms.SNOOZE"
        const val ACTION_IM_OK = "com.sukoon.app.alarms.IM_OK"
        const val EXTRA_TYPE = "type"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_PERSON = "person"
        const val EXTRA_TREATED = "treated" // "I'm treating it": Home starts the 15-minute wait
    }
}
