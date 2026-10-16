package com.sukoon.app.modes

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sukoon.app.MainActivity
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.ui.widget.arrow
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The watch after a smaller meal checks in at 30 and 60 minutes: your number and its arrow, and a
 * word to have something if you're dropping. Not an alarm (the low alarm is up at 90 meanwhile).
 */
class MealWatchCheck : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SukoonApp).container
        val done = goAsync()
        container.appScope.launch {
            try {
                if (container.modes.now().mealWatchUntil == null) return@launch
                val r = container.glucoseRepository.latestReading.first() ?: return@launch
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(CHANNEL) == null) {
                    manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.meal_watch_channel), NotificationManager.IMPORTANCE_DEFAULT))
                }
                val open = PendingIntent.getActivity(context, 60, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
                val falling = r.trend.name.startsWith("FALLING")
                val n = NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_sukoon)
                    .setColor(0xFF3E7A63.toInt())
                    .setContentTitle(context.getString(R.string.meal_watch_check_title, String.format(Locale.getDefault(), "%d", r.glucoseMgDl), r.trend.arrow))
                    .setContentText(context.getString(if (falling || r.glucoseMgDl < 100) R.string.meal_watch_check_falling else R.string.meal_watch_check_fine))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                runCatching { manager.notify(6000, n) }
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        private const val CHANNEL = "meal_watch"

        /** Check-ins 30 and 60 minutes from [from]. */
        fun schedule(context: Context, from: Instant) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            listOf(30L, 60L).forEachIndexed { i, minutes ->
                val at = from.plusSeconds(minutes * 60).toEpochMilli()
                val pending = PendingIntent.getBroadcast(context, 70 + i, Intent(context, MealWatchCheck::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                runCatching {
                    if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                    else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                }
            }
        }
    }
}
