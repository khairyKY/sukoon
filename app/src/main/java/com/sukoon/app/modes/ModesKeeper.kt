package com.sukoon.app.modes

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
import com.sukoon.app.data.repository.LogbookRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Workout, fasting and sick-day modes (docs/research/modes-sources.md): what you've switched on, what
 * that means right now ([active], refreshed every minute and on every change), and the reminders the
 * modes bring: ketones every 2 h on a sick day, suhoor before the fast starts.
 */
class ModesKeeper(private val context: Context, logbook: LogbookRepository, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("sukoon_modes", Context.MODE_PRIVATE)
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val events = MutableStateFlow<List<EventEntity>>(emptyList())
    private val _active = MutableStateFlow(ActiveModes())

    /** What's on now. Alarms, dose suggestions and Now read this. */
    val active: StateFlow<ActiveModes> = _active.asStateFlow()

    init {
        // A day and a bit back covers the late-low watch; ketone entries come with it.
        scope.launch { logbook.eventsSince(System.currentTimeMillis() - Duration.ofHours(30).toMillis()).collect { events.value = it; refresh() } }
    }

    fun start() {
        scope.launch {
            while (true) {
                refresh()
                runCatching { remind(Instant.now()) }
                delay(60_000)
            }
        }
    }

    /** What's on at this moment (fresh, for the alarms). */
    fun now(): ActiveModes = Modes.active(Instant.now(), zone, workoutUntil, workoutStart, events.value, fasting, sickSince)

    private fun refresh() {
        _active.value = now()
    }

    // --- what you set -----------------------------------------------------------------------------

    private var workoutUntil: Instant?
        get() = prefs.getLong(K_WORKOUT_UNTIL, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(v) = prefs.edit().putLong(K_WORKOUT_UNTIL, v?.toEpochMilli() ?: 0).apply()
    private var workoutStart: Instant?
        get() = prefs.getLong(K_WORKOUT_START, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(v) = prefs.edit().putLong(K_WORKOUT_START, v?.toEpochMilli() ?: 0).apply()

    /** The meal after a workout: how much less to suggest, 0, 25 or 50% (Riddell 2017: about 50%). */
    var afterWorkoutCut: Int
        get() = prefs.getInt(K_AFTER_CUT, 50)
        set(v) { prefs.edit().putInt(K_AFTER_CUT, v.coerceIn(0, 50)).apply(); refresh() }

    var fasting: FastingSettings
        get() = FastingSettings(
            on = prefs.getBoolean(K_FAST_ON, false),
            suhoorEnds = LocalTime.ofSecondOfDay(prefs.getInt(K_SUHOOR, FastingSettings().suhoorEnds.toSecondOfDay()).toLong()),
            iftar = LocalTime.ofSecondOfDay(prefs.getInt(K_IFTAR, FastingSettings().iftar.toSecondOfDay()).toLong()),
        )
        set(v) {
            prefs.edit().putBoolean(K_FAST_ON, v.on).putInt(K_SUHOOR, v.suhoorEnds.toSecondOfDay()).putInt(K_IFTAR, v.iftar.toSecondOfDay()).apply()
            refresh()
        }

    /** Bulking: the day's calories and protein to aim for (Bulk). */
    var bulk: BulkSettings
        get() = BulkSettings(
            on = prefs.getBoolean(K_BULK_ON, false),
            proteinPerKg = prefs.getFloat(K_BULK_PROTEIN, 2.0f).toDouble(),
            surplus = prefs.getInt(K_BULK_SURPLUS, 250),
            maintenance = prefs.getInt(K_BULK_MAINTENANCE, 0).takeIf { it > 0 },
        )
        set(v) {
            prefs.edit().putBoolean(K_BULK_ON, v.on).putFloat(K_BULK_PROTEIN, v.proteinPerKg.toFloat()).putInt(K_BULK_SURPLUS, v.surplus)
                .putInt(K_BULK_MAINTENANCE, v.maintenance ?: 0).apply()
            _bulk.value = v
        }
    private val _bulk = MutableStateFlow(bulk)
    val bulkFlow: StateFlow<BulkSettings> = _bulk.asStateFlow()

    /** For Ask: the bulk and its targets, or "" when not bulking. */
    fun bulkLine(profile: com.sukoon.app.insulin.Profile): String {
        val b = bulk
        if (!b.on) return ""
        val t = Bulk.targets(profile, b)
        return listOfNotNull(
            "GOAL: a lean bulk with resistance training (Kai's research: +100–300 kcal a day, protein 1.6–2.2 g/kg over ~4 meals).",
            t.kcal?.let { "Target about $it kcal a day (maintenance ${t.maintenance}${if (t.estimated) " estimated" else ""} + ${b.surplus})." },
            t.proteinGrams?.let { "Protein ${it} g a day (${b.proteinPerKg} g/kg), ${t.proteinPerMeal?.first}–${t.proteinPerMeal?.last} g a meal." },
            "Basal is glargine U-300 (can't be cut for one session); fat-heavy bulking meals (tahina, eggs, oil) rise 3–5 h later.",
        ).joinToString(" ")
    }

    var sickSince: Instant?
        get() = prefs.getLong(K_SICK, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        private set(v) = prefs.edit().putLong(K_SICK, v?.toEpochMilli() ?: 0).apply()

    /** A workout from now for [minutes]: low alarm at 100 until it ends, then the late-low watch. */
    fun startWorkout(minutes: Int) {
        val now = Instant.now()
        workoutStart = now
        workoutUntil = now.plus(Duration.ofMinutes(minutes.toLong()))
        refresh()
    }

    /** Ends the workout now; the late-low watch starts from here. */
    fun endWorkout() {
        if (workoutUntil?.isAfter(Instant.now()) == true) workoutUntil = Instant.now()
        refresh()
    }

    fun setSick(on: Boolean) {
        sickSince = if (on) Instant.now() else null
        refresh()
    }

    // --- reminders --------------------------------------------------------------------------------

    private fun remind(now: Instant) {
        val m = _active.value
        if (m.sick) {
            val lastKetones = events.value.filter { it.logType == LogEventType.KETONE }.maxOfOrNull { it.timestampMillis }?.let(Instant::ofEpochMilli)
            val lastReminder = prefs.getLong(K_KETONE_REMINDED, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            val since = listOfNotNull(m.sickSince, lastKetones, lastReminder).max()
            if (Duration.between(since, now) >= Modes.KETONES_EVERY) {
                notify(KETONES_ID, context.getString(R.string.modes_ketones_due_title), context.getString(R.string.modes_ketones_due_body), LogEventType.KETONE)
                prefs.edit().putLong(K_KETONE_REMINDED, now.toEpochMilli()).apply()
            }
        }
        val f = m.fasting
        if (f.on) {
            val today = now.atZone(zone).toLocalDate()
            val time = now.atZone(zone).toLocalTime()
            val remindAt = f.suhoorEnds.minus(SUHOOR_REMINDER)
            val day = if (f.suhoorEnds.isBefore(remindAt) && time.isBefore(f.suhoorEnds)) today.minusDays(1) else today // a reminder before midnight for a Fajr after it
            val due = if (remindAt.isBefore(f.suhoorEnds)) !time.isBefore(remindAt) && time.isBefore(f.suhoorEnds) else !time.isBefore(remindAt) || time.isBefore(f.suhoorEnds)
            if (due && prefs.getString(K_SUHOOR_REMINDED, null) != day.toString()) {
                notify(SUHOOR_ID, context.getString(R.string.modes_suhoor_title), context.getString(R.string.modes_suhoor_body, com.sukoon.app.platform.TimeFormat.of().format(f.suhoorEnds)), null)
                prefs.edit().putString(K_SUHOOR_REMINDED, day.toString()).apply()
            }
        }
    }

    private fun notify(id: Int, title: String, body: String, entry: LogEventType?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.modes_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (entry != null) open.putExtra(MainActivity.EXTRA_ENTRY, entry.name)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sukoon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(PendingIntent.getActivity(context, 300 + id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
        manager.notify(id, builder.build())
    }

    private companion object {
        const val CHANNEL = "modes"
        const val KETONES_ID = 8
        const val SUHOOR_ID = 9
        val SUHOOR_REMINDER: Duration = Duration.ofMinutes(45)
        const val K_WORKOUT_UNTIL = "workout_until"
        const val K_WORKOUT_START = "workout_start"
        const val K_AFTER_CUT = "after_workout_cut"
        const val K_FAST_ON = "fasting_on"
        const val K_SUHOOR = "fasting_suhoor_ends"
        const val K_IFTAR = "fasting_iftar"
        const val K_SICK = "sick_since"
        const val K_BULK_ON = "bulk_on"
        const val K_BULK_PROTEIN = "bulk_protein_per_kg"
        const val K_BULK_SURPLUS = "bulk_surplus"
        const val K_BULK_MAINTENANCE = "bulk_maintenance"
        const val K_KETONE_REMINDED = "ketone_reminded"
        const val K_SUHOOR_REMINDED = "suhoor_reminded_day"
    }
}

