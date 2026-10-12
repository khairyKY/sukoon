package com.sukoon.app.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sukoon.app.MainActivity
import com.sukoon.app.R
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.data.repository.LogbookRepository
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.ui.widget.arrow
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A gentle check, not an alarm (docs/insights-rework.md): 30 minutes after a logged workout ends
 * (yours, or one MyFitnessPal brought in), a notification with the number and its arrow, and one
 * line when it's falling. Lows still have their own alarms; this is the look before one.
 */
class AfterWorkoutCheck(
    private val context: Context,
    private val settings: SettingsPrefs,
    private val logbook: LogbookRepository,
    private val glucose: GlucoseRepository,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)

    fun start() {
        scope.launch {
            while (true) {
                runCatching { check(Instant.now()) }
                delay(60_000)
            }
        }
    }

    private suspend fun check(now: Instant) {
        if (!settings.afterWorkoutCheck) return
        val events = logbook.eventsSince(now.minus(LOOK_BACK).toEpochMilli()).first()
        val due = dueWorkouts(events, now, prefs.getStringSet(KEY_DONE, null).orEmpty())
        if (due.isEmpty()) return
        val latest = glucose.latestReading.first()?.takeIf { Duration.between(it.timestamp, now) <= Duration.ofMinutes(10) }
        if (latest != null) notify(latest)
        // Marked done either way: an hour-old check without a number is no use.
        prefs.edit().putStringSet(KEY_DONE, prefs.getStringSet(KEY_DONE, null).orEmpty() + due.map { it.id.toString() }).apply()
    }

    private fun notify(reading: GlucoseReading) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.workout_check_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 7, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val falling = reading.trend == TrendDirection.FALLING || reading.trend == TrendDirection.FALLING_FAST
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_walk) // the walk mark (Redesign Canvas §8); a line icon, tinted like any status icon
            .setContentTitle(context.getString(R.string.workout_check_title, reading.glucoseMgDl, reading.trend.arrow))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (falling) builder.setContentText(context.getString(R.string.workout_check_falling))
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        private const val CHANNEL = "gentle_checks"
        private const val NOTIFICATION_ID = 7
        private const val KEY_DONE = "workout_checks_done"
        private val AFTER: Duration = Duration.ofMinutes(30)
        /** Past this, the check is too late to be useful and is skipped. */
        private val LATE: Duration = Duration.ofMinutes(90)
        private val LOOK_BACK: Duration = Duration.ofHours(6)

        /** Workouts whose end was 30 to 90 minutes ago and haven't had their check. Pure, for tests. */
        fun dueWorkouts(events: List<EventEntity>, now: Instant, done: Set<String>): List<EventEntity> = events.filter { e ->
            if (e.logType != LogEventType.ACTIVITY || e.id.toString() in done) return@filter false
            val end = Instant.ofEpochMilli(e.timestampMillis).plus(Duration.ofSeconds(((e.value ?: 0.0) * 60).toLong()))
            val since = Duration.between(end, now)
            since >= AFTER && since < LATE
        }
    }
}
