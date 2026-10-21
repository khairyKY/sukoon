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
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.domain.metrics.TargetRange
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Check-ins after rapid insulin, once per dose:
 * - [NO_FOOD]: 30 min after a dose taken when you weren't high, with no food logged around it (a meal
 *   dose and then no meal is a low on its way): "Did you eat?" with Log meal / I'm not eating (low watch).
 * - [CORRECTION]: 2 h after a dose taken when high with no food around it: coming down, still high
 *   (ketones, the site), or dropping fast (low watch).
 * Off in Insulin & doses.
 */
class DoseCheck : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SukoonApp).container
        if (intent.action == ACTION_NOT_EATING) {
            // "I'm not eating": the dose works on nothing, so the low watch goes on for its hours.
            container.modes.startMealWatch(Instant.now().plus(Duration.ofHours(4)))
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION + (intent.getLongExtra(EXTRA_DOSE, 0) % 1000).toInt())
            return
        }
        val id = intent.getLongExtra(EXTRA_DOSE, -1)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        if (id < 0 || !container.settings.doseCheckIns) return
        val done = goAsync()
        container.appScope.launch {
            try {
                val dose = container.logbookRepository.byId(id) ?: return@launch
                val since = dose.timestampMillis - Duration.ofHours(2).toMillis()
                val events = container.logbookRepository.eventsSince(since).first()
                val readings = container.glucoseRepository.readingsSince(since).first()
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val key = "$kind:$id"
                val told = prefs.getStringSet(KEY_TOLD, null).orEmpty()
                if (key in told) return@launch
                val now = Instant.now()
                val outcome = when (kind) {
                    NO_FOOD -> if (noFood(dose, events, readings, now)) Outcome.NO_FOOD else null
                    else -> correctionOutcome(dose, events, readings, now)
                }
                if (outcome != null) {
                    if (outcome == Outcome.DROPPING) container.modes.startMealWatch(now.plus(Duration.ofHours(2)))
                    notify(context, dose, outcome, readings.lastOrNull(), glucoseAt(readings, dose.timestampMillis))
                    prefs.edit().putStringSet(KEY_TOLD, (told + key).toList().takeLast(60).toSet()).apply()
                }
            } finally {
                done.finish()
            }
        }
    }

    enum class Outcome { NO_FOOD, COMING_DOWN, STILL_HIGH, DROPPING }

    companion object {
        const val NO_FOOD = "no_food"
        const val CORRECTION = "correction"
        private val NO_FOOD_AFTER: Duration = Duration.ofMinutes(30)
        private val CORRECTION_AFTER: Duration = Duration.ofHours(2)
        private const val EXTRA_DOSE = "dose"
        private const val EXTRA_KIND = "kind"
        private const val ACTION_NOT_EATING = "com.sukoon.app.NOT_EATING"
        private const val CHANNEL = "dose_checks"
        private const val PREFS = "sukoon_dose_checks"
        private const val KEY_TOLD = "told"
        private const val NOTIFICATION = 8_000

        /** The reading nearest [at] within 15 minutes (null when the sensor wasn't reading). */
        internal fun glucoseAt(readings: List<GlucoseReading>, at: Long): Int? =
            readings.minByOrNull { kotlin.math.abs(it.timestamp.toEpochMilli() - at) }
                ?.takeIf { kotlin.math.abs(it.timestamp.toEpochMilli() - at) <= Duration.ofMinutes(15).toMillis() }?.glucoseMgDl

        private fun foodAround(dose: EventEntity, events: List<EventEntity>, before: Duration, until: Long) = events.any {
            it.logType == LogEventType.CARB && (it.value ?: 0.0) >= 10 && !it.summary &&
                it.timestampMillis in (dose.timestampMillis - before.toMillis())..until
        }

        private fun isRapid(dose: EventEntity) = dose.logType == LogEventType.INSULIN && (dose.value ?: 0.0) > 0 && dose.mealId == null

        /** A dose taken when not high, with no food from an hour before it until [now]: a meal dose with no meal. */
        internal fun noFood(dose: EventEntity, events: List<EventEntity>, readings: List<GlucoseReading>, now: Instant): Boolean {
            if (!isRapid(dose)) return false
            val before = glucoseAt(readings, dose.timestampMillis) ?: return false
            if (before > TargetRange.high) return false // a correction, not a meal dose
            return !foodAround(dose, events, Duration.ofHours(1), now.toEpochMilli())
        }

        /** 2 h after a dose taken when high with no food around it: how it went. Null when it wasn't a correction. */
        internal fun correctionOutcome(dose: EventEntity, events: List<EventEntity>, readings: List<GlucoseReading>, now: Instant): Outcome? {
            if (!isRapid(dose)) return null
            val before = glucoseAt(readings, dose.timestampMillis) ?: return null
            if (before <= TargetRange.high || foodAround(dose, events, Duration.ofHours(1), now.toEpochMilli())) return null
            val latest = readings.lastOrNull()?.takeIf { Duration.between(it.timestamp, now) <= Duration.ofMinutes(15) } ?: return null
            val tenAgo = readings.lastOrNull { Duration.between(it.timestamp, latest.timestamp) >= Duration.ofMinutes(10) }
            val perMinute = tenAgo?.let { (latest.glucoseMgDl - it.glucoseMgDl).toDouble() / Duration.between(it.timestamp, latest.timestamp).toMinutes().coerceAtLeast(1) } ?: 0.0
            return when {
                latest.glucoseMgDl < 120 && perMinute <= -1 -> Outcome.DROPPING // under 120 and still falling 1+ a minute, insulin working
                latest.glucoseMgDl > TargetRange.high && before - latest.glucoseMgDl < 30 -> Outcome.STILL_HIGH
                else -> Outcome.COMING_DOWN
            }
        }

        /** Lines up the check-ins for a rapid dose as it lands (the receiver judges when it fires). */
        fun schedule(context: Context, dose: EventEntity, now: Instant = Instant.now()) {
            if (dose.logType != LogEventType.INSULIN || (dose.value ?: 0.0) <= 0 || dose.mealId != null) return
            val at = Instant.ofEpochMilli(dose.timestampMillis)
            listOf(NO_FOOD to NO_FOOD_AFTER, CORRECTION to CORRECTION_AFTER).forEachIndexed { i, (kind, after) ->
                val fire = at.plus(after)
                if (fire.isBefore(now.minus(Duration.ofMinutes(10)))) return@forEachIndexed // long past
                val wake = PendingIntent.getBroadcast(
                    context, (dose.id % 100_000).toInt() + 60_000 + i * 100_000,
                    Intent(context, DoseCheck::class.java).putExtra(EXTRA_DOSE, dose.id).putExtra(EXTRA_KIND, kind),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val alarms = context.getSystemService(AlarmManager::class.java)
                val time = maxOf(fire, now.plusSeconds(60)).toEpochMilli()
                runCatching {
                    if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, wake)
                    else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, wake)
                }
            }
        }

        private fun notify(context: Context, dose: EventEntity, outcome: Outcome, latest: GlucoseReading?, before: Int?) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.dose_check_channel), NotificationManager.IMPORTANCE_HIGH))
            fun n(x: Number) = String.format(Locale.getDefault(), "%d", x.toInt())
            val units = com.sukoon.app.ui.logbook.formatAmountLocalized(dose.value ?: 0.0)
            val now = latest?.glucoseMgDl?.let(::n) ?: "-"
            val (title, body) = when (outcome) {
                Outcome.NO_FOOD -> context.getString(R.string.dose_check_food_title) to context.getString(R.string.dose_check_food_body, units)
                Outcome.COMING_DOWN -> context.getString(R.string.dose_check_down_title) to context.getString(R.string.dose_check_down_body, now, before?.let(::n) ?: "-")
                Outcome.STILL_HIGH -> context.getString(R.string.dose_check_high_title) to context.getString(R.string.dose_check_high_body, now)
                Outcome.DROPPING -> context.getString(R.string.dose_check_drop_title) to context.getString(R.string.dose_check_drop_body, now)
            }
            val code = (dose.id % 1000).toInt()
            val open = PendingIntent.getActivity(
                context, NOTIFICATION + code,
                Intent(context, MainActivity::class.java).apply { if (outcome == Outcome.NO_FOOD) putExtra(MainActivity.EXTRA_ENTRY, LogEventType.CARB.name) }
                    .setData(android.net.Uri.parse("sukoon://dose-check/${outcome.name}/${dose.id}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_sukoon)
                .setColor(0xFF3E7A63.toInt())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
            if (outcome == Outcome.NO_FOOD) {
                builder.addAction(0, context.getString(R.string.dose_check_log_meal), open)
                builder.addAction(
                    0, context.getString(R.string.dose_check_not_eating),
                    PendingIntent.getBroadcast(
                        context, NOTIFICATION + 500 + code,
                        Intent(context, DoseCheck::class.java).setAction(ACTION_NOT_EATING).putExtra(EXTRA_DOSE, dose.id),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            runCatching { manager.notify(NOTIFICATION + code, builder.build()) }
        }
    }
}
