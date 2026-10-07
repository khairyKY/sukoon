package com.sukoon.app.platform

import com.sukoon.app.domain.metrics.TargetRange
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sukoon.app.MainActivity
import com.sukoon.app.R
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.ui.home.HomeUiStateMapper
import com.sukoon.app.ui.widget.arrow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Keeps the process alive while Sukoon reads the sensor, so the BLE connection (owned by the
 * app-scoped LibreBleSource in AppContainer) survives screen-off and backgrounding. Its ongoing
 * notification earns its keep: the newest reading and arrow, with the number itself as the
 * status-bar icon, so glucose is one glance away without opening anything.
 */
class SensorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this, shown, Instant.now()), type)
            running = true
            START_STICKY
        } catch (e: SecurityException) {
            // Android 14 refuses a connectedDevice service before Bluetooth permission is granted.
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "sensor"
        private const val NOTIFICATION_ID = 1

        @Volatile private var running = false
        @Volatile private var shown: GlucoseReading? = null

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, SensorService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SensorService::class.java))
        }

        /** The newest reading into the ongoing notification (called on every reading and once a minute for its age). */
        fun show(context: Context, reading: GlucoseReading?, now: Instant) {
            shown = reading
            if (running) context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(context, reading, now))
        }

        private fun notification(context: Context, reading: GlucoseReading?, now: Instant): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val builder = Notification.Builder(context, CHANNEL)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
            val minutes = reading?.let { Duration.between(it.timestamp, now).toMinutes().coerceAtLeast(0) }
            when {
                reading == null || minutes == null -> builder
                    .setSmallIcon(R.drawable.ic_stat_sukoon)
                    .setContentTitle(context.getString(R.string.service_title))
                    .setContentText(context.getString(R.string.service_body))
                minutes > HomeUiStateMapper.STALE_AFTER.toMinutes() -> builder
                    .setSmallIcon(R.drawable.ic_stat_sukoon)
                    .setContentTitle(context.resources.getQuantityString(R.plurals.service_stale_title, minutes.toInt(), minutes.toInt()))
                    .setContentText(context.getString(R.string.service_stale_body, reading.glucoseMgDl, TIME.format(reading.timestamp)))
                else -> builder
                    .setSmallIcon(numberIcon(reading.glucoseMgDl))
                    .setContentTitle("${String.format(Locale.getDefault(), "%d", reading.glucoseMgDl)} ${reading.trend.arrow} ${context.getString(R.string.home_unit_mgdl)}")
                    .setContentText("${trendWord(context, reading.trend)} · ${if (minutes < 1) context.getString(R.string.graph_just_now) else context.resources.getQuantityString(R.plurals.graph_min_ago, minutes.toInt(), minutes.toInt())}")
                    .setColor(rangeColor(reading.glucoseMgDl))
            }
            return builder.build()
        }

        /** The value drawn as a white-on-transparent bitmap: Android tints it like any status-bar icon. */
        private fun numberIcon(mgDl: Int): Icon {
            val size = 96
            val text = mgDl.toString() // Western digits: the narrowest at this size
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                textSize = size * 0.9f
            }
            while (paint.measureText(text) > size && paint.textSize > 12f) paint.textSize -= 2f
            Canvas(bitmap).drawText(text, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
            return Icon.createWithBitmap(bitmap)
        }

        private fun trendWord(context: Context, trend: TrendDirection) = context.getString(
            when (trend) {
                TrendDirection.FALLING_FAST -> R.string.home_trend_falling_fast
                TrendDirection.FALLING -> R.string.home_trend_falling
                TrendDirection.STEADY -> R.string.home_trend_steady
                TrendDirection.RISING, TrendDirection.RISING_FAST -> R.string.home_trend_rising
            },
        )

        private fun rangeColor(mgDl: Int) = when {
            mgDl < 70 -> 0xFFC9564B.toInt()
            mgDl > TargetRange.high -> 0xFFC88A3E.toInt()
            else -> 0xFF3E7A63.toInt()
        }

        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }
}
