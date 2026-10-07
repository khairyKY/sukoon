package com.sukoon.app.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sukoon.app.MainActivity
import com.sukoon.app.R
import com.sukoon.app.data.source.libre.SensorLife
import com.sukoon.app.data.source.libre.SensorLifecycle
import com.sukoon.app.data.source.libre.SensorNotice
import com.sukoon.app.ui.components.sensorTime
import java.time.Instant

/**
 * The paired sensor's one-time heads-ups: ready after warm-up, a day left, an hour left, ended.
 * Each is shown once per sensor (by serial); if several fall due together (the phone was off),
 * only the most important one is shown.
 */
class SensorLifeNotices(private val context: Context, private val current: () -> Pair<String, SensorLife>?) {

    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun check(now: Instant = Instant.now()) {
        val (serial, life) = current() ?: return
        val key = KEY_SHOWN + serial
        val shown = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { name -> SensorNotice.entries.firstOrNull { it.name == name } }.toSet()
        val due = SensorLifecycle.due(life, now)
        val notice = (due - shown).maxByOrNull { it.ordinal } ?: return
        post(notice, life)
        prefs.edit().putStringSet(key, (shown + due).map { it.name }.toSet()).apply()
    }

    private fun post(notice: SensorNotice, life: SensorLife) {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.life_channel), NotificationManager.IMPORTANCE_HIGH))
        val ends = when (life) {
            is SensorLife.Running -> sensorTime(life.endsAt)
            is SensorLife.Ended -> sensorTime(life.endedAt)
            is SensorLife.WarmingUp -> ""
        }
        val (title, body) = when (notice) {
            SensorNotice.WARMED_UP -> context.getString(R.string.life_warmed_title) to context.getString(R.string.life_warmed_body)
            SensorNotice.DAY_LEFT -> context.getString(R.string.life_day_title) to context.getString(R.string.life_day_body, ends)
            SensorNotice.HOUR_LEFT -> context.getString(R.string.life_hour_title) to context.getString(R.string.life_hour_body, ends)
            SensorNotice.ENDED -> context.getString(R.string.life_ended_title) to context.getString(R.string.life_ended_body)
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sukoon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL = "sensor_life"
        const val NOTIFICATION_ID = 4
        const val KEY_SHOWN = "sensor_notices_"
    }
}
