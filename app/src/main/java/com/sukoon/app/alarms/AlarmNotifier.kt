package com.sukoon.app.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sukoon.app.MainActivity
import com.sukoon.app.R

/**
 * Turns alarm decisions into things a person notices. Urgent low is built to wake someone up:
 * alarm-stream sound looping for up to a minute (DND "alarms allowed" lets it through; with DND
 * access granted the channel also bypasses DND), a full-screen alert over the lock screen, and —
 * with "display over other apps" — the alert screen opened directly. Snooze is always an explicit
 * button, never a swipe.
 */
class AlarmNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null

    private val alarmAudio = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun show(alert: Alert, settings: AlarmSettings, test: Boolean = false) {
        val (titleRes, bodyRes) = when (alert.type) {
            AlarmType.URGENT_LOW -> R.string.alarm_urgent_title to R.string.alarm_urgent_body
            AlarmType.LOW -> R.string.alarm_low_title to R.string.alarm_low_body
            AlarmType.GOING_LOW -> R.string.alarm_going_low_title to R.string.alarm_going_low_body
            AlarmType.HIGH -> R.string.alarm_high_title to R.string.alarm_high_body
            AlarmType.SIGNAL_LOSS -> R.string.alarm_signal_title to R.string.alarm_signal_body
        }
        val value = alert.mgDl?.let { context.getString(R.string.alarm_value, it) }
        val title = listOfNotNull(if (test) context.getString(R.string.alarm_test_prefix) else null, context.getString(titleRes), value.takeIf { alert.type != AlarmType.SIGNAL_LOSS })
            .joinToString(" ")
        val body = if (alert.type == AlarmType.SIGNAL_LOSS) context.getString(bodyRes, alert.minutesSinceReading ?: 0) else context.getString(bodyRes)

        val (snoozeMinutes, snoozeLabel) = when (alert.type) {
            AlarmType.URGENT_LOW -> 5 to context.getString(R.string.alarm_action_treating)
            AlarmType.LOW -> settings.lowSnoozeMinutes to context.getString(R.string.alarm_action_snooze, settings.lowSnoozeMinutes)
            AlarmType.GOING_LOW -> 60 to context.getString(R.string.alarm_action_ok)
            AlarmType.HIGH -> settings.highSnoozeMinutes to context.getString(R.string.alarm_action_snooze, settings.highSnoozeMinutes)
            AlarmType.SIGNAL_LOSS -> 30 to context.getString(R.string.alarm_action_snooze, 30)
        }
        val builder = NotificationCompat.Builder(context, channelFor(alert.type))
            .setSmallIcon(R.drawable.ic_stat_sukoon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setDeleteIntent(actionIntent(alert.type, minutes = 0, requestCode = alert.type.ordinal + 100))
            .addAction(0, snoozeLabel, actionIntent(alert.type, snoozeMinutes, requestCode = alert.type.ordinal))

        if (alert.type == AlarmType.URGENT_LOW) {
            val fullScreen = PendingIntent.getActivity(
                context, 1,
                UrgentAlarmActivity.intent(context, alert.mgDl),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setFullScreenIntent(fullScreen, true)
            // With "display over other apps" Android lets us open the alert screen from the background.
            if (Settings.canDrawOverlays(context)) {
                runCatching { context.startActivity(UrgentAlarmActivity.intent(context, alert.mgDl).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            manager.notify(notificationId(alert.type), builder.build())
        }
        if (alert.type == AlarmType.URGENT_LOW) playLoud(URGENT_SOUND_MS)
    }

    fun cancel(type: AlarmType) {
        manager.cancel(notificationId(type))
        if (type == AlarmType.URGENT_LOW) stopSound()
    }

    fun stopSound() {
        main.removeCallbacksAndMessages(null)
        ringtone?.stop()
        ringtone = null
    }

    /** Alarm-stream ringtone, looping until acknowledged or [durationMs] passes. */
    private fun playLoud(durationMs: Long) {
        stopSound()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = alarmAudio
            if (Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }
        main.postDelayed(::stopSound, durationMs)
    }

    private fun actionIntent(type: AlarmType, minutes: Int, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, AlarmActionReceiver::class.java)
            .setAction(AlarmActionReceiver.ACTION_SNOOZE)
            .putExtra(AlarmActionReceiver.EXTRA_TYPE, type.name)
            .putExtra(AlarmActionReceiver.EXTRA_MINUTES, minutes),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Channels are immutable once created, and DND bypass only sticks if DND access was granted
     * at creation — so the urgent/low channel ids carry a suffix and are recreated once access is.
     */
    private fun channelFor(type: AlarmType): String {
        val dnd = manager.isNotificationPolicyAccessGranted
        return when (type) {
            AlarmType.URGENT_LOW -> ensure("alarm_urgent" + if (dnd) "_dnd" else "", R.string.alarm_channel_urgent, NotificationManager.IMPORTANCE_HIGH, alarmSound = true, bypassDnd = dnd, vibration = URGENT_VIBRATION)
            AlarmType.LOW, AlarmType.GOING_LOW -> ensure("alarm_low" + if (dnd) "_dnd" else "", R.string.alarm_channel_low, NotificationManager.IMPORTANCE_HIGH, alarmSound = true, bypassDnd = dnd, vibration = LOW_VIBRATION)
            AlarmType.HIGH -> ensure("alarm_high", R.string.alarm_channel_high, NotificationManager.IMPORTANCE_HIGH, alarmSound = false, bypassDnd = false, vibration = LOW_VIBRATION)
            AlarmType.SIGNAL_LOSS -> ensure("alarm_signal", R.string.alarm_channel_signal, NotificationManager.IMPORTANCE_DEFAULT, alarmSound = false, bypassDnd = false, vibration = null)
        }
    }

    private fun ensure(id: String, nameRes: Int, importance: Int, alarmSound: Boolean, bypassDnd: Boolean, vibration: LongArray?): String {
        if (manager.getNotificationChannel(id) == null) {
            // Drop the pre-DND-access twin so the user doesn't see two copies in settings.
            if (id.endsWith("_dnd")) manager.deleteNotificationChannel(id.removeSuffix("_dnd"))
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(nameRes), importance).apply {
                    if (alarmSound) setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAudio)
                    if (vibration != null) {
                        enableVibration(true)
                        vibrationPattern = vibration
                    }
                    setBypassDnd(bypassDnd)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
            )
        }
        return id
    }

    private fun notificationId(type: AlarmType) = 1000 + type.ordinal

    private companion object {
        const val URGENT_SOUND_MS = 60_000L
        val URGENT_VIBRATION = longArrayOf(0, 800, 400, 800, 400, 800, 400, 1600)
        val LOW_VIBRATION = longArrayOf(0, 600, 300, 600)
    }
}
