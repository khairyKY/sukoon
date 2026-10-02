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
 * Keeps the process alive while Sukoon reads the sensor, so the BLE connection (owned by the
 * app-scoped LibreBleSource in AppContainer) survives screen-off and backgrounding. It does no
 * work itself — the persistent notification is the price Android charges for staying alive.
 */
class SensorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(getString(R.string.service_body))
            .setSmallIcon(R.drawable.ic_stat_sukoon)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            START_STICKY
        } catch (e: SecurityException) {
            // Android 14 refuses a connectedDevice service before Bluetooth permission is granted.
            stopSelf()
            START_NOT_STICKY
        }
    }

    companion object {
        private const val CHANNEL = "sensor"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, SensorService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SensorService::class.java))
        }
    }
}
