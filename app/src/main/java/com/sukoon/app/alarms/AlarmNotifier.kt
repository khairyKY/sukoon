package com.sukoon.app.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sukoon.app.MainActivity
import com.sukoon.app.R
import android.os.PowerManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns alarm decisions into things a person notices. Urgent low is built to wake someone up:
 * alarm-stream sound looping for up to a minute (DND "alarms allowed" lets it through; with DND
 * access granted the channel also bypasses DND), a full-screen alert over the lock screen, and —
 * with "display over other apps" — the alert screen opened directly. Snooze is always an explicit
 * button, never a swipe.
 *
 * Sounds are played here rather than by the notification channels, so each alarm can use the
 * user's chosen sound (a phone sound or their own file) and a sound that won't play falls back to
 * a default instead of leaving the alarm silent.
 */
class AlarmNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var playing: AlarmType? = null
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sukoon:emergency")
        .apply { setReferenceCounted(false) }

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
        val channel = channelFor(alert.type)
        val builder = NotificationCompat.Builder(context, channel)
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
        val posted = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE
        if (posted) manager.notify(notificationId(alert.type), builder.build())
        // Lows always sound; highs and signal loss stay quiet if the user blocked their notifications.
        when {
            alert.type == AlarmType.URGENT_LOW -> play(alert.type, settings, loop = true, URGENT_SOUND_MS)
            alert.type.loud || posted -> play(alert.type, settings, loop = false, SOUND_MS)
        }
    }

    /** Keeps the CPU up through the emergency countdown, so sleep can't delay the texts. */
    fun keepAwake(ms: Long) = wakeLock.acquire(ms)

    /** The emergency countdown: urgent channel and sound, a live countdown, the full-screen alert, and "I'm OK". */
    fun showCountdown(endsAt: Instant, settings: AlarmSettings) {
        val screen = PendingIntent.getActivity(context, 2, UrgentAlarmActivity.intent(context, null), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, channelFor(AlarmType.URGENT_LOW))
            .setSmallIcon(R.drawable.ic_stat_sukoon)
            .setContentTitle(context.getString(R.string.emergency_countdown_title))
            .setContentText(context.getString(R.string.emergency_countdown_body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setWhen(endsAt.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .addAction(0, context.getString(R.string.emergency_im_ok), imOkIntent())
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) manager.notify(COUNTDOWN_ID, builder.build())
        if (Settings.canDrawOverlays(context)) {
            runCatching { context.startActivity(UrgentAlarmActivity.intent(context, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        play(AlarmType.URGENT_LOW, settings, loop = true, URGENT_SOUND_MS)
    }

    fun cancelCountdown() {
        manager.cancel(COUNTDOWN_ID)
        stopSound()
    }

    /** After the texts: who was told and when (or that texting failed), for when the user comes round. */
    fun showEmergencySent(names: List<String>, at: Instant) {
        val title = context.getString(if (names.isEmpty()) R.string.emergency_send_failed_title else R.string.emergency_sent_title)
        val body = if (names.isEmpty()) context.getString(R.string.emergency_send_failed) else context.getString(R.string.emergency_sent_body, names.joinToString(), TIME.format(at))
        val screen = PendingIntent.getActivity(context, 3, UrgentAlarmActivity.intent(context, null), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channelFor(AlarmType.URGENT_LOW))
            .setSmallIcon(R.drawable.ic_stat_sukoon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(screen)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) manager.notify(SENT_ID, notification)
    }

    private fun imOkIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 200,
        Intent(context, AlarmActionReceiver::class.java).setAction(AlarmActionReceiver.ACTION_IM_OK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** You → Alarms → a sound's "Play it": a few seconds of what this alarm will sound like. */
    fun preview(type: AlarmType, settings: AlarmSettings) = play(type, settings, loop = true, PREVIEW_MS)

    @Synchronized
    fun cancel(type: AlarmType) {
        manager.cancel(notificationId(type))
        if (type == playing) stopSound()
    }

    @Synchronized
    fun stopSound() {
        main.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        playing = null
    }

    /**
     * Plays [type]'s sound for up to [durationMs]: the user's choice, else the phone's default for
     * that kind of alert, else any default that plays — a deleted or unreadable file must never
     * leave an alarm silent. Lows use the alarm stream; highs and signal loss the notification one.
     */
    @Synchronized
    private fun play(type: AlarmType, settings: AlarmSettings, loop: Boolean, durationMs: Long) {
        stopSound()
        val attributes = AudioAttributes.Builder()
            .setUsage(if (type.loud) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val defaults = listOf(if (type.loud) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE)
            .mapNotNull { RingtoneManager.getActualDefaultRingtoneUri(context, it) }
        val candidates = (listOfNotNull(settings.sounds[type]?.uri?.let(Uri::parse)) + defaults).distinct()
        player = candidates.firstNotNullOfOrNull { start(context, it, attributes, loop) }
        playing = type
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
     * The channels are silent (vibration only): [play] makes the sound.
     */
    private fun channelFor(type: AlarmType): String {
        val dnd = manager.isNotificationPolicyAccessGranted
        return when (type) {
            AlarmType.URGENT_LOW -> ensure("alarm2_urgent" + if (dnd) "_dnd" else "", R.string.alarm_channel_urgent, NotificationManager.IMPORTANCE_HIGH, bypassDnd = dnd, vibration = URGENT_VIBRATION)
            AlarmType.LOW, AlarmType.GOING_LOW -> ensure("alarm2_low" + if (dnd) "_dnd" else "", R.string.alarm_channel_low, NotificationManager.IMPORTANCE_HIGH, bypassDnd = dnd, vibration = LOW_VIBRATION)
            AlarmType.HIGH -> ensure("alarm2_high", R.string.alarm_channel_high, NotificationManager.IMPORTANCE_HIGH, bypassDnd = false, vibration = LOW_VIBRATION)
            AlarmType.SIGNAL_LOSS -> ensure("alarm2_signal", R.string.alarm_channel_signal, NotificationManager.IMPORTANCE_DEFAULT, bypassDnd = false, vibration = null)
        }
    }

    private fun ensure(id: String, nameRes: Int, importance: Int, bypassDnd: Boolean, vibration: LongArray?): String {
        if (manager.getNotificationChannel(id) == null) {
            // Drop the pre-DND-access twin so the user doesn't see two copies in settings, and the
            // first-generation channels, which played their own (fixed) sound.
            if (id.endsWith("_dnd")) manager.deleteNotificationChannel(id.removeSuffix("_dnd"))
            LEGACY_CHANNELS.forEach(manager::deleteNotificationChannel)
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(nameRes), importance).apply {
                    setSound(null, null)
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

    companion object {
        private const val TAG = "AlarmNotifier"
        private const val URGENT_SOUND_MS = 60_000L
        private const val SOUND_MS = 30_000L
        private const val PREVIEW_MS = 5_000L
        private const val COUNTDOWN_ID = 2000
        private const val SENT_ID = 2001
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        private val URGENT_VIBRATION = longArrayOf(0, 800, 400, 800, 400, 800, 400, 1600)
        private val LOW_VIBRATION = longArrayOf(0, 600, 300, 600)
        private val LEGACY_CHANNELS = listOf("alarm_urgent", "alarm_urgent_dnd", "alarm_low", "alarm_low_dnd", "alarm_high", "alarm_signal")

        /** A started player for [uri], or null if it can't play (missing file, lost permission, unsupported format). */
        private fun start(context: Context, uri: Uri, attributes: AudioAttributes, loop: Boolean): MediaPlayer? {
            val player = MediaPlayer()
            return try {
                player.setAudioAttributes(attributes)
                player.setDataSource(context, uri)
                player.isLooping = loop
                player.prepare()
                player.start()
                player
            } catch (e: Exception) {
                Log.w(TAG, "Can't play $uri", e)
                player.release()
                null
            }
        }

        /** Whether [uri] would play: checked when the user picks a file, before any alarm relies on it. */
        fun canPlay(context: Context, uri: Uri): Boolean {
            val player = MediaPlayer()
            return try {
                player.setDataSource(context, uri)
                player.prepare()
                true
            } catch (e: Exception) {
                Log.w(TAG, "Picked sound won't play: $uri", e)
                false
            } finally {
                player.release()
            }
        }
    }
}
