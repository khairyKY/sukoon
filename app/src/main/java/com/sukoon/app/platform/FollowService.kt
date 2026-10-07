package com.sukoon.app.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sukoon.app.MainActivity
import com.sukoon.app.R

/**
 * Keeps the process alive while this phone watches the people it follows (sharing/FollowerWatch),
 * so their lows can sound here in the background. Its notification shows their latest readings.
 */
class FollowService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this, latest ?: getString(R.string.follow_service_body)), type)
            running = true
            START_STICKY
        } catch (e: Exception) {
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "following"
        private const val NOTIFICATION_ID = 3

        @Volatile private var running = false
        @Volatile private var latest: String? = null

        fun start(context: Context) {
            if (!running) runCatching { ContextCompat.startForegroundService(context, Intent(context, FollowService::class.java)) }
        }

        fun stop(context: Context) {
            latest = null
            context.stopService(Intent(context, FollowService::class.java))
        }

        /** "Kai 142 ↗ · 2 min ago" in the ongoing notification, once the service runs. */
        fun update(context: Context, text: String) {
            latest = text
            if (running) context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(context, text))
        }

        private fun notification(context: Context, text: String): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.follow_service_channel), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            return Notification.Builder(context, CHANNEL)
                .setContentTitle(context.getString(R.string.follow_service_title))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_stat_sukoon)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
    }
}
