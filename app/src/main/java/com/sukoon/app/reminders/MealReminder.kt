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
import com.sukoon.app.platform.TimeFormat
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * You ate and logged no rapid insulin: [WAIT] after the meal, one notification ("Log insulin" opens
 * the insulin entry for that meal). For meals of 10 g or more, logged here, by voice or from
 * MyFitnessPal; not low treatments or "earlier in the day" catch-ups; off in Insulin & doses.
 */
class MealReminder : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_MEAL, -1)
        val container = (context.applicationContext as SukoonApp).container
        if (id < 0 || !container.settings.mealReminder) return
        val done = goAsync()
        container.appScope.launch {
            try {
                val meal = container.logbookRepository.byId(id) ?: return@launch
                val since = meal.timestampMillis - BEFORE.toMillis()
                val events = container.logbookRepository.eventsSince(since).first()
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val told = prefs.getStringSet(KEY_TOLD, null).orEmpty()
                if (id.toString() in told || !needsInsulin(meal, events, Instant.now())) return@launch
                notify(context, meal)
                prefs.edit().putStringSet(KEY_TOLD, (told + id.toString()).toList().takeLast(50).toSet()).apply()
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        /** How long after the meal it asks: time to have taken it and logged it. */
        val WAIT: Duration = Duration.ofMinutes(20)
        private val BEFORE = Duration.ofMinutes(60) // insulin from an hour before the meal counts as its dose
        private val TOO_LATE = Duration.ofHours(3) // past this a full meal dose is risky; the correction takes over
        private const val EXTRA_MEAL = "meal"
        private const val CHANNEL = "meal_reminder"
        private const val PREFS = "sukoon_meal_reminder"
        private const val KEY_TOLD = "told"

        /** A real meal (10 g+, not a treatment or a catch-up) still within 3 hours, with no rapid insulin from an hour before it until [now]. */
        internal fun needsInsulin(meal: EventEntity, events: List<EventEntity>, now: Instant): Boolean {
            if (meal.logType != LogEventType.CARB || meal.treatment || meal.summary || (meal.value ?: 0.0) < 10) return false
            val at = Instant.ofEpochMilli(meal.timestampMillis)
            if (Duration.between(at, now) > TOO_LATE || at.isAfter(now)) return false
            return events.none { e ->
                e.logType == LogEventType.INSULIN && (e.value ?: 0.0) > 0 &&
                    (e.mealId == meal.id || e.timestampMillis in (meal.timestampMillis - BEFORE.toMillis())..now.toEpochMilli())
            }
        }

        /** Asks [WAIT] after [meal] (or a minute from now, for a meal that arrived late), unless it's past helping. */
        fun schedule(context: Context, meal: EventEntity, now: Instant = Instant.now()) {
            if (meal.logType != LogEventType.CARB || meal.treatment || meal.summary || (meal.value ?: 0.0) < 10) return
            val at = Instant.ofEpochMilli(meal.timestampMillis)
            if (Duration.between(at, now) > TOO_LATE) return
            val fire = maxOf(at.plus(WAIT), now.plusSeconds(60))
            val wake = PendingIntent.getBroadcast(
                context, (meal.id % 100_000).toInt() + 40_000,
                Intent(context, MealReminder::class.java).putExtra(EXTRA_MEAL, meal.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val alarms = context.getSystemService(AlarmManager::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.toEpochMilli(), wake)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.toEpochMilli(), wake)
            }
        }

        private fun notify(context: Context, meal: EventEntity) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.meal_reminder_channel), NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(
                context, (meal.id % 100_000).toInt() + 50_000,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_MEAL_INSULIN, meal.id)
                    .setData(android.net.Uri.parse("sukoon://meal-insulin/${meal.id}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val grams = String.format(Locale.getDefault(), "%d", Math.round(meal.value ?: 0.0))
            val time = TimeFormat.of().format(Instant.ofEpochMilli(meal.timestampMillis))
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_sukoon)
                .setColor(0xFF3E7A63.toInt())
                .setContentTitle(context.getString(R.string.meal_reminder_title))
                .setContentText(context.getString(R.string.meal_reminder_body, meal.note ?: context.getString(R.string.meal_reminder_meal), grams, time))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.meal_reminder_body, meal.note ?: context.getString(R.string.meal_reminder_meal), grams, time)))
                .setContentIntent(open)
                .addAction(0, context.getString(R.string.meal_reminder_log), open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(7_000 + (meal.id % 1000).toInt(), n) }
        }
    }
}
