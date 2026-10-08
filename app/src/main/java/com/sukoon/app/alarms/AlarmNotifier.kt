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
import android.media.AudioManager
import android.os.Build
import kotlin.math.ceil

/** The alarm's name, for the notification and the alert screen. */
internal val AlarmType.titleRes: Int
    get() = when (this) {
        AlarmType.URGENT_LOW -> R.string.alarm_urgent_title
        AlarmType.LOW -> R.string.alarm_low_title
        AlarmType.GOING_LOW -> R.string.alarm_going_low_title
        AlarmType.HIGH -> R.string.alarm_high_title
        AlarmType.SIGNAL_LOSS -> R.string.alarm_signal_title
    }

/** The alarm's message; about [who] when it's someone this phone follows. */
internal fun alarmBody(context: Context, type: AlarmType, minutesSinceReading: Long?, who: String?): String = when {
    who != null && type == AlarmType.SIGNAL_LOSS -> context.getString(R.string.follow_alarm_signal_body, who, minutesSinceReading ?: 0)
    who != null -> context.getString(R.string.follow_alarm_body, who)
    else -> when (type) {
        AlarmType.URGENT_LOW -> context.getString(R.string.alarm_urgent_body)
        AlarmType.LOW -> context.getString(R.string.alarm_low_body)
        AlarmType.GOING_LOW -> context.getString(R.string.alarm_going_low_body)
        AlarmType.HIGH -> context.getString(R.string.alarm_high_body)
        AlarmType.SIGNAL_LOSS -> if (!com.sukoon.app.ui.components.bluetoothOn(context)) context.getString(R.string.alarm_signal_bt_off)
        else context.resources.getQuantityString(R.plurals.alarm_signal_body, (minutesSinceReading ?: 0).toInt(), minutesSinceReading ?: 0)
    }
}

/** What the alarm's own button ("OK", "Snooze N min", "I'm treating it") snoozes it for. */
internal fun AlarmType.defaultSnooze(settings: AlarmSettings): Int = when (this) {
    AlarmType.URGENT_LOW -> 5
    AlarmType.LOW -> settings.lowSnoozeMinutes
    AlarmType.GOING_LOW -> 30
    AlarmType.HIGH -> settings.highSnoozeMinutes
    AlarmType.SIGNAL_LOSS -> 30
}

/**
 * Turns alarm decisions into things a person notices. Urgent low is built to wake someone up:
 * alarm-stream sound looping for up to a minute (DND "alarms allowed" lets it through; with DND
 * access granted the channel also bypasses DND). Every alarm opens the full-screen alert
 * ([AlarmActivity]) over the lock screen and, with "display over other apps", over whatever is
 * open. Snooze is always an explicit button, never a swipe.
 *
 * Sounds are played here rather than by the notification channels, so each alarm can use the
 * user's chosen sound (a phone sound or their own file) and a sound that won't play falls back to
 * a default instead of leaving the alarm silent.
 */
class AlarmNotifier(private val context: Context, private val log: AlarmLog) {

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var playing: AlarmType? = null
    private var playingFor: String? = null // whose alarm is sounding: a followed person's id, or null for this phone's own
    private val audio = context.getSystemService(AudioManager::class.java)
    private var restoreVolume: Int? = null // the alarm volume before a low raised it
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sukoon:emergency")
        .apply { setReferenceCounted(false) }

    /** [who] + [person]: an alarm about someone this phone follows (their name and user id); null = your own. */
    fun show(alert: Alert, settings: AlarmSettings, test: Boolean = false, who: String? = null, person: String? = null) {
        val value = alert.mgDl?.let { context.getString(R.string.alarm_value, it) }
        val title = listOfNotNull(
            if (test) context.getString(R.string.alarm_test_prefix) else null,
            who?.let { "$it ·" },
            context.getString(alert.type.titleRes),
            value.takeIf { alert.type != AlarmType.SIGNAL_LOSS },
        ).joinToString(" ")
        val body = alarmBody(context, alert.type, alert.minutesSinceReading, who)

        val snoozeMinutes = alert.type.defaultSnooze(settings)
        val treating = (alert.type == AlarmType.URGENT_LOW || alert.type == AlarmType.LOW) && person == null
        val snoozeLabel = when {
            treating -> context.getString(R.string.alarm_action_treating)
            alert.type == AlarmType.URGENT_LOW -> context.getString(R.string.alarm_action_ok)
            else -> context.resources.getQuantityString(R.plurals.alarm_action_snooze, snoozeMinutes.toInt(), snoozeMinutes)
        }
        val channel = channelFor(alert.type)
        // Every alarm takes the screen: over the lock screen through the full-screen intent, and
        // straight over whatever is open with "display over other apps".
        val screen = AlarmActivity.intent(context, alert, who, person, test)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_sukoon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setDeleteIntent(actionIntent(alert.type, minutes = 0, requestCode = alert.type.ordinal + 100, person = person))
            .addAction(0, snoozeLabel, actionIntent(alert.type, snoozeMinutes, requestCode = alert.type.ordinal, person = person, treated = treating && !test))
            .setFullScreenIntent(PendingIntent.getActivity(context, 1, screen, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT), true)
        // With "display over other apps" Android lets us open the alert screen from the background.
        val overlaid = Settings.canDrawOverlays(context) && runCatching { context.startActivity(Intent(screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        val posted = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE
        if (posted) manager.notify(person, notificationId(alert.type), builder.build())
        // Lows always sound; highs and signal loss stay quiet if the user blocked their notifications.
        val sounded = if (alert.type.loud || posted) play(alert.type, settings, loop = true, soundFor(alert.type), person) else false
        val fullScreen = posted && (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent())
        log.add(AlarmLog.Entry(Instant.now(), alert.type, AlarmLog.Kind.FIRED, alert.mgDl, sounded = sounded, screen = overlaid || fullScreen, posted = posted, test = test, who = who))
    }

    /** Keeps the CPU up through the emergency countdown, so sleep can't delay the texts. */
    fun keepAwake(ms: Long) = wakeLock.acquire(ms)

    /** The emergency countdown: urgent channel and sound, a live countdown, the full-screen alert, and "I'm OK". */
    fun showCountdown(endsAt: Instant, settings: AlarmSettings) {
        val screen = PendingIntent.getActivity(context, 2, AlarmActivity.intent(context, null), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
            runCatching { context.startActivity(AlarmActivity.intent(context, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
        val screen = PendingIntent.getActivity(context, 3, AlarmActivity.intent(context, null), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
    fun preview(type: AlarmType, settings: AlarmSettings) {
        play(type, settings, loop = true, PREVIEW_MS)
    }

    @Synchronized
    fun cancel(type: AlarmType, person: String? = null) {
        manager.cancel(person, notificationId(type))
        if (type == playing && person == playingFor) stopSound()
    }

    @Synchronized
    fun stopSound() {
        main.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        playing = null
        playingFor = null
        restoreVolume?.let { runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) } }
        restoreVolume = null
    }

    /**
     * Plays [type]'s sound for up to [durationMs]: the user's choice, else the phone's default for
     * that kind of alert, else any default that plays — a deleted or unreadable file must never
     * leave an alarm silent. Lows use the alarm stream; highs and signal loss the notification one.
     */
    @Synchronized
    private fun play(type: AlarmType, settings: AlarmSettings, loop: Boolean, durationMs: Long, person: String? = null): Boolean {
        stopSound()
        if (type.loud) audible(if (type == AlarmType.URGENT_LOW) 0.8 else 0.5)
        val attributes = AudioAttributes.Builder()
            .setUsage(if (type.loud) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val defaults = listOf(if (type.loud) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE)
            .mapNotNull { RingtoneManager.getActualDefaultRingtoneUri(context, it) }
        // Your choice, else this alarm's sound from your pack, else the phone's: one always plays.
        val candidates = (listOfNotNull(settings.sounds[type]?.uri?.let(Uri::parse), SukoonSounds.uri(context, settings.soundPack, SoundRole.of(type))) + defaults).distinct()
        player = candidates.firstNotNullOfOrNull { start(context, it, attributes, loop) }
        playing = type
        playingFor = person
        main.postDelayed(::stopSound, durationMs)
        return player != null
    }

    /**
     * A low must be heard even with the alarm volume turned down: lift it to [share] of the maximum
     * while it sounds, and put it back after ([stopSound]). Never lowers it.
     */
    private fun audible(share: Double) {
        val floor = ceil(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) * share).toInt()
        val now = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        if (now < floor && runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, floor, 0) }.isSuccess) restoreVolume = now
    }

    private fun actionIntent(type: AlarmType, minutes: Int, requestCode: Int, person: String?, treated: Boolean = false): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, AlarmActionReceiver::class.java)
            .setAction(AlarmActionReceiver.ACTION_SNOOZE)
            // Distinct data per person and button, so one person's snooze can't overwrite another's.
            .setData(Uri.parse("sukoon://alarm/${person ?: "me"}/${type.name}/$minutes"))
            .putExtra(AlarmActionReceiver.EXTRA_TYPE, type.name)
            .putExtra(AlarmActionReceiver.EXTRA_MINUTES, minutes)
            .putExtra(AlarmActionReceiver.EXTRA_PERSON, person)
            .putExtra(AlarmActionReceiver.EXTRA_TREATED, treated),
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
            AlarmType.SIGNAL_LOSS -> ensure("alarm3_signal", R.string.alarm_channel_signal, NotificationManager.IMPORTANCE_HIGH, bypassDnd = false, vibration = null)
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

        /** How long each alarm's (short, looping) sound keeps going: urgent a minute, a low half that, the rest long enough to notice. */
        private fun soundFor(type: AlarmType): Long = when (type) {
            AlarmType.URGENT_LOW -> URGENT_SOUND_MS
            AlarmType.LOW -> 30_000L
            AlarmType.GOING_LOW, AlarmType.HIGH, AlarmType.SIGNAL_LOSS -> 8_000L
        }
        private const val PREVIEW_MS = 5_000L
        private const val COUNTDOWN_ID = 2000
        private const val SENT_ID = 2001
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        private val URGENT_VIBRATION = longArrayOf(0, 800, 400, 800, 400, 800, 400, 1600)
        private val LOW_VIBRATION = longArrayOf(0, 600, 300, 600)
        private val LEGACY_CHANNELS = listOf("alarm_urgent", "alarm_urgent_dnd", "alarm_low", "alarm_low_dnd", "alarm_high", "alarm_signal", "alarm2_signal")

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

        /** True when the user switched off one of the alarm channels in the phone's settings (the app's notifications can still be on). */
        fun alarmChannelBlocked(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java).notificationChannels
                .any { it.id.startsWith("alarm") && it.importance == NotificationManager.IMPORTANCE_NONE }

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
