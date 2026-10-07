package com.sukoon.app.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sukoon.app.SukoonApp
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.platform.SensorService
import java.time.Duration

/**
 * A dead man's switch for the alarms. Every reading pushes this system alarm a little past the
 * no-readings alarm. If the phone kills Sukoon, readings stop and nothing inside the app could
 * sound any more, so Android wakes Sukoon here instead: the app starts again (sensor service,
 * alarms), and the no-readings alarm goes off if the sensor is still quiet. It keeps checking
 * every 5 minutes until readings come back and push it forward again.
 */
class SignalWatchdog : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SukoonApp).container // waking builds it: alarms and sensor start
        if (!container.sourceKind.value.real) return
        SensorService.start(context)
        arm(context, RECHECK)
    }

    companion object {
        private val RECHECK: Duration = Duration.ofMinutes(5)

        /** Wake Sukoon in [after] unless a reading pushes this forward first. Exact where the phone allows it. */
        fun arm(context: Context, after: Duration) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val at = System.currentTimeMillis() + after.toMillis()
            val wake = PendingIntent.getBroadcast(
                context, 0, Intent(context, SignalWatchdog::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            runCatching {
                if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wake)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wake)
            }
        }
    }
}
