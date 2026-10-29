package com.sukoon.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sukoon.app.MainActivity
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.di.AppContainer
import com.sukoon.app.domain.metrics.FingerPricks
import com.sukoon.app.insights.MealSlot
import com.sukoon.app.platform.TimeFormat
import com.sukoon.app.ui.home.HomeUiState
import com.sukoon.app.ui.home.HomeUiStateMapper
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Posts what [CheckPlan] says is due: finger-prick checks in finger-prick mode, and "Lunch? Nothing
 * logged yet". One alarm, always set to the next one; [refresh] re-plans whenever the logbook,
 * the sensor or You → Reminders change. Buttons on each notification: Log it / In 15 min / Skip,
 * and for a meal The usual / Something else / Didn't eat.
 */
class Checks : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SukoonApp).container
        val key = intent.getStringExtra(EXTRA_KEY)
        val done = goAsync()
        container.appScope.launch {
            try {
                val manager = context.getSystemService(NotificationManager::class.java)
                when (intent.action) {
                    ACTION_LATER -> if (key != null) {
                        manager.cancel(notificationId(key))
                        store(context).edit { sent.remove(key); snoozed[key] = Instant.now().plus(LATER) }
                    }
                    ACTION_DONE -> if (key != null) {
                        manager.cancel(notificationId(key)) // Skip, Didn't eat, Not food: it stays sent
                        // "Didn't eat": that meal isn't a gap tonight.
                        if (key.startsWith("meal:")) store(context).edit { sent["no:$key"] = Instant.now() }
                    }
                    ACTION_USUAL -> if (key != null) {
                        manager.cancel(notificationId(key))
                        val note = intent.getStringExtra(EXTRA_NOTE)
                        val carbs = intent.getDoubleExtra(EXTRA_CARBS, 0.0)
                        // Logged now: the meal reminder then asks about its insulin, as for any meal.
                        if (note != null && carbs > 0) container.logbookRepository.log(LogEventType.CARB, carbs, note)
                    }
                    else -> fire(context, container)
                }
                refresh(context, container)
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        private const val ACTION_CHECK = "com.sukoon.app.reminders.CHECK"
        private const val ACTION_LATER = "com.sukoon.app.reminders.CHECK_LATER"
        private const val ACTION_DONE = "com.sukoon.app.reminders.CHECK_DONE"
        private const val ACTION_USUAL = "com.sukoon.app.reminders.CHECK_USUAL"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_NOTE = "note"
        private const val EXTRA_CARBS = "carbs"
        private const val CHANNEL = "checks"
        private val LATER: Duration = Duration.ofMinutes(15)
        /** The logbook looked at: the 2-hour checks and learned meal times need this much. */
        private val LOOK_BACK: Duration = Duration.ofDays(21)
        private val lock = Mutex()

        /** Finger-prick mode, judged the way Now judges it. */
        suspend fun fingerPrickMode(container: AppContainer, pricks: List<FingerPricks.Prick>, now: Instant): Boolean =
            HomeUiStateMapper.map(
                container.glucoseRepository.status.value,
                container.glucoseRepository.latestReading.first(),
                emptyList(), now, container.sensorLife(now), pricks,
            ) is HomeUiState.FingerPrick

        private suspend fun plan(context: Context, container: AppContainer, now: Instant): List<Due> {
            val zone = ZoneId.systemDefault()
            val events = container.logbookRepository.eventsSince(now.minus(LOOK_BACK).toEpochMilli()).first()
            val day = events.filter { it.timestampMillis >= now.minus(Duration.ofHours(30)).toEpochMilli() }
            val mode = container.settings.sourceKind.real && fingerPrickMode(container, FingerPricks.of(day), now)
            val state = store(context)
            return CheckPlan.plan(now, zone, day, mode, container.settings.checks, MealTimes.learned(events, now, zone), state.sent, state.snoozed)
        }

        /** A new sensor reading: "Did you eat?" if it's rising and nothing explains it ([RiseCheck]). */
        suspend fun rise(context: Context, container: AppContainer) = lock.withLock {
            if (!container.settings.checks.mealMissing || !container.settings.sourceKind.real) return@withLock
            val now = Instant.now()
            val readings = container.glucoseRepository.readingsSince(now.minus(Duration.ofMinutes(40)).toEpochMilli()).first().map { it.timestamp to it.glucoseMgDl }
            val events = container.logbookRepository.eventsSince(now.minus(Duration.ofDays(60)).toEpochMilli()).first()
            val state = store(context)
            val asked = state.sent.filterKeys { it.startsWith("rise:") }.values.toList()
            val zone = ZoneId.systemDefault()
            if (!RiseCheck.unexplained(readings, events, now, zone, asked)) return@withLock
            val due = Due(CheckKind.MEAL_MISSING, now, "rise:${now.toEpochMilli()}", com.sukoon.app.insights.InsightEngine.slotFor(now.atZone(zone).hour), mgDl = readings.maxBy { it.first }.second)
            state.edit { sent[due.key] = now }
            val start = readings.lastOrNull { !it.first.isAfter(now.minus(RiseCheck.WINDOW)) }?.second ?: readings.first().second
            notify(context, due, null, MealTimes.usual(events, due.slot!!, now, zone), riseBy = (due.mgDl ?: 0) - start)
        }

        /** The next finger-prick check (not a meal question), for Now's "Next check" line. */
        suspend fun nextPrick(context: Context, container: AppContainer): Due? =
            plan(context, container, Instant.now()).firstOrNull { it.kind != CheckKind.MEAL_MISSING }

        /** Re-plans and sets the one alarm to the next reminder (or cancels it when there's none). */
        suspend fun refresh(context: Context, container: AppContainer) = lock.withLock {
            val now = Instant.now()
            val next = plan(context, container, now).firstOrNull()
            val wake = PendingIntent.getBroadcast(context, 60_000, Intent(context, Checks::class.java).setAction(ACTION_CHECK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (next == null) context.getSystemService(AlarmManager::class.java).cancel(wake)
            else BasalReminder.at(context, maxOf(next.at, now.plusSeconds(5)), wake)
        }

        /** Everything due now, posted and marked sent. */
        private suspend fun fire(context: Context, container: AppContainer) = lock.withLock {
            val now = Instant.now()
            val due = plan(context, container, now).filter { !it.at.isAfter(now.plusSeconds(30)) }
            if (due.isEmpty()) return@withLock
            val zone = ZoneId.systemDefault()
            val events = container.logbookRepository.eventsSince(now.minus(Duration.ofDays(60)).toEpochMilli()).first()
            val lastPrick = FingerPricks.of(events).lastOrNull()
            store(context).edit { due.forEach { sent[it.key] = now; snoozed.remove(it.key) } }
            due.forEach { d -> notify(context, d, lastPrick, d.slot?.let { MealTimes.usual(events, it, now, zone) }) }
        }

        private fun notificationId(key: String) = 9_000 + (key.hashCode() and 0x3FF)

        private fun notify(context: Context, due: Due, lastPrick: FingerPricks.Prick?, usual: UsualMeal?, riseBy: Int? = null) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.checks_channel), NotificationManager.IMPORTANCE_HIGH))
            val id = notificationId(due.key)
            val time = TimeFormat.of()
            fun open(type: LogEventType, code: Int) = PendingIntent.getActivity(
                context, id * 4 + code,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ENTRY, type.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            fun action(action: String, code: Int, extras: Intent.() -> Unit = {}) = PendingIntent.getBroadcast(
                context, id * 4 + code,
                Intent(context, Checks::class.java).setAction(action).putExtra(EXTRA_KEY, due.key).apply(extras),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val last = lastPrick?.let { context.getString(R.string.checks_last_prick, it.mgDl, time.format(it.at)) }
            val (title, body) = when (due.kind) {
                CheckKind.BEFORE_MEAL -> context.getString(R.string.checks_before_title, slotName(context, due.slot)) to last
                CheckKind.AFTER_MEAL -> context.getString(R.string.checks_after_title) to context.getString(R.string.checks_after_body, time.format(due.since ?: due.at))
                CheckKind.BEDTIME -> context.getString(R.string.checks_bed_title) to last
                CheckKind.AFTER_LOW -> context.getString(R.string.checks_low_title) to context.getString(R.string.checks_low_body, due.mgDl ?: 0, time.format(due.since ?: due.at))
                CheckKind.MEAL_MISSING -> if (riseBy != null) context.getString(R.string.checks_rise_title) to context.getString(R.string.checks_rise_body, due.mgDl ?: 0, riseBy)
                else context.getString(R.string.checks_meal_title, slotName(context, due.slot)) to context.getString(R.string.checks_meal_body, time.format(due.at.minus(CheckPlan.MEAL_WAIT)))
                CheckKind.DAY_GAPS -> context.getString(R.string.checks_gaps_title) to
                    context.getString(R.string.checks_gaps_body, due.gaps.joinToString(context.getString(R.string.checks_list_sep)) { slotName(context, it) })
            }
            val b = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_sukoon)
                .setColor(if (due.kind == CheckKind.AFTER_LOW) 0xFFC9564B.toInt() else 0xFF3E7A63.toInt())
                .setContentTitle(title)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            body?.let { b.setContentText(it).setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
            if (due.kind == CheckKind.DAY_GAPS) {
                val fill = open(LogEventType.CARB, 0)
                b.setContentIntent(fill).addAction(0, context.getString(R.string.checks_fill_in), fill).addAction(0, context.getString(R.string.checks_not_today), action(ACTION_DONE, 2))
            } else if (due.kind == CheckKind.MEAL_MISSING) {
                val other = open(LogEventType.CARB, 0)
                b.setContentIntent(other)
                usual?.let { u ->
                    val grams = String.format(Locale.getDefault(), "%d", Math.round(u.carbs))
                    b.addAction(0, context.getString(R.string.checks_usual, u.note, grams), action(ACTION_USUAL, 1) { putExtra(EXTRA_NOTE, u.note); putExtra(EXTRA_CARBS, u.carbs) })
                }
                b.addAction(0, context.getString(R.string.checks_something_else), other)
                b.addAction(0, context.getString(if (riseBy != null) R.string.checks_not_food else R.string.checks_didnt_eat), action(ACTION_DONE, 2))
            } else {
                val log = open(LogEventType.FINGERSTICK, 0)
                b.setContentIntent(log).addAction(0, context.getString(R.string.checks_log_it), log)
                if (due.kind == CheckKind.AFTER_LOW) {
                    b.addAction(0, context.getString(R.string.checks_ate_more), PendingIntent.getActivity(context, id * 4 + 3, MainActivity.logTreatment(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                } else {
                    b.addAction(0, context.getString(R.string.checks_later), action(ACTION_LATER, 1))
                    b.addAction(0, context.getString(R.string.checks_skip), action(ACTION_DONE, 2))
                }
            }
            runCatching { manager.notify(id, b.build()) }
        }

        private fun slotName(context: Context, slot: MealSlot?) = context.getString(
            when (slot) {
                MealSlot.BREAKFAST -> R.string.checks_breakfast
                MealSlot.LUNCH -> R.string.checks_lunch
                else -> R.string.checks_dinner
            },
        )

        /** A prick was logged: it answers any finger-prick check still in the shade. */
        fun clearPrickChecks(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            store(context).sent.keys.filter { it.startsWith("pre:") || it.startsWith("post:") || it.startsWith("bed:") || it.startsWith("low:") }
                .forEach { manager.cancel(notificationId(it)) }
        }

        /** Sent keys and snoozes, kept two days. */
        private class Store(val prefs: android.content.SharedPreferences) {
            val sent: MutableMap<String, Instant> = read("sent")
            val snoozed: MutableMap<String, Instant> = read("snoozed")

            private fun read(name: String): MutableMap<String, Instant> {
                val json = runCatching { JSONObject(prefs.getString(name, "{}") ?: "{}") }.getOrElse { JSONObject() }
                val cutoff = Instant.now().minus(Duration.ofDays(2))
                return json.keys().asSequence().associateWith { Instant.ofEpochMilli(json.getLong(it)) }.filterValues { it.isAfter(cutoff) }.toMutableMap()
            }

            fun edit(block: Store.() -> Unit) {
                block()
                fun json(map: Map<String, Instant>) = JSONObject().apply { map.forEach { (k, v) -> put(k, v.toEpochMilli()) } }.toString()
                prefs.edit().putString("sent", json(sent)).putString("snoozed", json(snoozed)).apply()
            }
        }

        private fun store(context: Context) = Store(context.getSharedPreferences("sukoon_checks", Context.MODE_PRIVATE))
    }
}
