package com.sukoon.app.reminders

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
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** You → Insulin: a nudge at [minuteOfDay] when long-acting insulin isn't logged by then. Off until turned on. */
data class BasalReminderSettings(val enabled: Boolean = false, val minuteOfDay: Int = 22 * 60)

/**
 * The long-acting reminder. Once a day at the chosen time it looks at the logbook: a long-acting
 * dose in the last 12 hours counts as today's, and then nothing happens. Otherwise a notification
 * ("Took it · 20 U" logs the last dose amount, "In 30 min" waits), repeated every 30 minutes up to
 * three times until the dose is logged. Logging it anywhere clears the notification.
 */
class BasalReminder : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SukoonApp).container
        val done = goAsync()
        container.appScope.launch {
            try {
                val settings = container.settings.basalReminder
                val now = Instant.now()
                val events = container.logbookRepository.eventsSince(now.minus(Duration.ofDays(30)).toEpochMilli()).first()
                when (intent.action) {
                    ACTION_TOOK -> {
                        lastDose(events)?.let { container.logbookRepository.log(LogEventType.BASAL, it) }
                        dismiss(context)
                    }
                    ACTION_LATER -> {
                        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                        at(context, now.plus(NAG_EVERY), nag(context, intent.getIntExtra(EXTRA_NAG, 0)))
                    }
                    else -> {
                        val nag = intent.getIntExtra(EXTRA_NAG, 0)
                        if (nag == 0) schedule(context, settings) // tomorrow's
                        if (!settings.enabled) return@launch
                        if (taken(events, now)) {
                            dismiss(context)
                        } else {
                            notify(context, lastDose(events), nag)
                            if (nag < NAGS) at(context, now.plus(NAG_EVERY), nag(context, nag + 1))
                        }
                    }
                }
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        private const val ACTION_CHECK = "com.sukoon.app.reminders.BASAL_CHECK"
        private const val ACTION_TOOK = "com.sukoon.app.reminders.BASAL_TOOK"
        private const val ACTION_LATER = "com.sukoon.app.reminders.BASAL_LATER"
        private const val EXTRA_NAG = "nag"
        private const val CHANNEL = "reminders"
        private const val NOTIFICATION_ID = 3000
        private const val NAGS = 3
        private val NAG_EVERY: Duration = Duration.ofMinutes(30)
        private val TAKEN_WITHIN: Duration = Duration.ofHours(12)

        /** Sets (or, when off, cancels) the daily check. Safe to call any time: it replaces itself. */
        fun schedule(context: Context, settings: BasalReminderSettings) {
            val daily = pending(context, 0, Intent(context, BasalReminder::class.java).setAction(ACTION_CHECK))
            if (!settings.enabled) {
                context.getSystemService(AlarmManager::class.java).cancel(daily)
                context.getSystemService(AlarmManager::class.java).cancel(nag(context, 1))
                dismiss(context)
                return
            }
            at(context, nextAt(ZonedDateTime.now(), settings.minuteOfDay).toInstant(), daily)
        }

        /** Takes the notification away (the dose is logged, or the reminder was turned off). */
        fun dismiss(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            context.getSystemService(AlarmManager::class.java).cancel(nag(context, 1))
        }

        /** The next time [minuteOfDay] comes round, strictly after [now]. */
        internal fun nextAt(now: ZonedDateTime, minuteOfDay: Int): ZonedDateTime {
            val today = now.with(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)).withSecond(0).withNano(0)
            return if (today.isAfter(now)) today else today.plusDays(1)
        }

        /** A long-acting dose in the last 12 hours counts as today's. */
        internal fun taken(events: List<EventEntity>, now: Instant): Boolean =
            events.any { it.logType == LogEventType.BASAL && Instant.ofEpochMilli(it.timestampMillis).let { t -> t > now.minus(TAKEN_WITHIN) && t <= now } }

        /** The last long-acting amount logged: what "Took it" logs again. */
        internal fun lastDose(events: List<EventEntity>): Double? =
            events.filter { it.logType == LogEventType.BASAL && (it.value ?: 0.0) > 0 }.maxByOrNull { it.timestampMillis }?.value

        /** When you usually log long-acting (minute of day, the median of the last 7), to offer as the reminder time. */
        fun usualMinute(events: List<EventEntity>, zone: ZoneId): Int? = events.filter { it.logType == LogEventType.BASAL }
            .sortedBy { it.timestampMillis }.takeLast(7)
            .map { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).let { t -> t.hour * 60 + t.minute } }
            .sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

        private fun nag(context: Context, n: Int) =
            pending(context, 1, Intent(context, BasalReminder::class.java).setAction(ACTION_CHECK).putExtra(EXTRA_NAG, n))

        private fun pending(context: Context, code: Int, intent: Intent): PendingIntent =
            PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        private fun at(context: Context, at: Instant, wake: PendingIntent) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), wake)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), wake)
            }
        }

        private fun notify(context: Context, dose: Double?, nag: Int) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH))
            }
            val units = dose?.let { String.format(Locale.getDefault(), "%.0f", it) }
            val open = PendingIntent.getActivity(
                context, 10,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ENTRY, LogEventType.BASAL.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_sukoon)
                .setContentTitle(context.getString(R.string.reminder_basal_title))
                .setContentText(context.getString(if (units != null) R.string.reminder_basal_body_dose else R.string.reminder_basal_body, units))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
            if (units != null) builder.addAction(0, context.getString(R.string.reminder_basal_took, units), pending(context, 2, Intent(context, BasalReminder::class.java).setAction(ACTION_TOOK)))
            else builder.addAction(0, context.getString(R.string.reminder_basal_log), open)
            builder.addAction(0, context.getString(R.string.reminder_basal_later), pending(context, 3, Intent(context, BasalReminder::class.java).setAction(ACTION_LATER).putExtra(EXTRA_NAG, nag)))
            runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
        }
    }
}
