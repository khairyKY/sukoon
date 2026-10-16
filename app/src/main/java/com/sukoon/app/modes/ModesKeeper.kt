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
    fun now(): ActiveModes = Instant.now().let { t ->
        Modes.active(t, zone, workoutUntil, workoutStart, events.value, fasting, sickSince).copy(mealWatchUntil = mealWatch(t))
    }

    // --- the watch after a smaller meal ---------------------------------------------------------

    private var watchUntil: Instant?
        get() = prefs.getLong(K_WATCH_UNTIL, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(v) = prefs.edit().putLong(K_WATCH_UNTIL, v?.toEpochMilli() ?: 0).apply()
    private var watchStart: Instant?
        get() = prefs.getLong(K_WATCH_START, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(v) = prefs.edit().putLong(K_WATCH_START, v?.toEpochMilli() ?: 0).apply()

    /** On until [watchUntil], unless 10 g or more of carbs were logged after it started (that covers it). */
    private fun mealWatch(now: Instant): Instant? {
        val until = watchUntil?.takeIf { it > now } ?: return null
        val start = watchStart ?: return until
        val ate = events.value.any { it.logType == com.sukoon.app.data.db.LogEventType.CARB && (it.value ?: 0.0) >= 10 && Instant.ofEpochMilli(it.timestampMillis) > start }
        return if (ate) null else until
    }

    /** Watch for a low until [until] (when a smaller meal's extra insulin is spent): low alarm at 90, two check-ins. */
    fun startMealWatch(until: Instant) {
        val now = Instant.now()
        watchStart = now
        watchUntil = until.coerceAtMost(now.plus(Duration.ofHours(5)))
        MealWatchCheck.schedule(context, now)
        refresh()
    }

    fun endMealWatch() {
        watchUntil = null
        refresh()
    }

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
        get() {
            val typed = FastingSettings(
                on = prefs.getBoolean(K_FAST_ON, false),
                suhoorEnds = LocalTime.ofSecondOfDay(prefs.getInt(K_SUHOOR, FastingSettings().suhoorEnds.toSecondOfDay()).toLong()),
                iftar = LocalTime.ofSecondOfDay(prefs.getInt(K_IFTAR, FastingSettings().iftar.toSecondOfDay()).toLong()),
            )
            // From your place: today's Fajr and Maghrib, worked out fresh every day.
            val zone = java.time.ZoneId.systemDefault()
            val day = prayerPlace?.let { PrayerTimes.of(java.time.LocalDate.now(zone), it.lat, it.lng, it.method) } ?: return typed
            return typed.copy(suhoorEnds = day.fajr.atZone(zone).toLocalTime().withSecond(0).withNano(0), iftar = day.maghrib.atZone(zone).toLocalTime().withSecond(0).withNano(0))
        }
        set(v) {
            prefs.edit().putBoolean(K_FAST_ON, v.on).putInt(K_SUHOOR, v.suhoorEnds.toSecondOfDay()).putInt(K_IFTAR, v.iftar.toSecondOfDay()).apply()
            refresh()
        }

    /** Where Fajr and Maghrib are worked out for (null: the times typed in). [lat]/[lng] rounded to ~1 km. */
    data class PrayerPlace(val lat: Double, val lng: Double, val method: PrayerTimes.Method = PrayerTimes.Method.EGYPT)

    var prayerPlace: PrayerPlace?
        get() = prefs.getString(K_PLACE, null)?.split(",")?.takeIf { it.size == 3 }?.let {
            runCatching { PrayerPlace(it[0].toDouble(), it[1].toDouble(), PrayerTimes.Method.valueOf(it[2])) }.getOrNull()
        }
        set(v) {
            prefs.edit().apply { if (v == null) remove(K_PLACE) else putString(K_PLACE, "${v.lat},${v.lng},${v.method.name}") }.apply()
            _place.value = v
            refresh()
        }
    private val _place = MutableStateFlow(prayerPlace)
    val place: StateFlow<PrayerPlace?> = _place.asStateFlow()
    private var freshFixAt = 0L

    /**
     * The place follows the phone (Cairo, Alexandria and Tanta are minutes apart): its rough last-known
     * location, and on Android 11+ a fresh one at most hourly; only a move of over 10 km moves the place.
     * [start] turns it on from nothing. False when no location is known yet. Nothing leaves the phone.
     */
    fun followPlace(start: Boolean = false): Boolean {
        if (prayerPlace == null && !start) return false
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        val lm = context.getSystemService(android.location.LocationManager::class.java) ?: return false
        val fused = if (android.os.Build.VERSION.SDK_INT >= 31) android.location.LocationManager.FUSED_PROVIDER else android.location.LocationManager.NETWORK_PROVIDER
        val last = runCatching {
            listOf(fused, android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.PASSIVE_PROVIDER, android.location.LocationManager.GPS_PROVIDER)
                .distinct().mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull()
        last?.let(::moveTo)
        val now = System.currentTimeMillis()
        if (android.os.Build.VERSION.SDK_INT >= 30 && (start || now - freshFixAt > Duration.ofHours(1).toMillis())) {
            freshFixAt = now
            val provider = if (android.os.Build.VERSION.SDK_INT >= 31 && lm.hasProvider(fused)) fused else android.location.LocationManager.NETWORK_PROVIDER
            runCatching { lm.getCurrentLocation(provider, null, context.mainExecutor) { loc -> if (loc != null && (start || prayerPlace != null)) moveTo(loc) } }
        }
        return last != null
    }

    private fun moveTo(loc: android.location.Location) {
        val cur = prayerPlace
        if (cur != null && PrayerTimes.km(cur.lat, cur.lng, loc.latitude, loc.longitude) <= 10) return
        // Rounded to ~1 km: plenty for prayer times, and no more precise than it needs to be.
        prayerPlace = PrayerPlace(Math.round(loc.latitude * 100) / 100.0, Math.round(loc.longitude * 100) / 100.0, cur?.method ?: PrayerTimes.Method.EGYPT)
    }

    /** Your goal (You → Fitness & food): maintain, lean bulk or cut, with its targets (Goals). */
    var goal: GoalSettings
        get() = GoalSettings(
            // Before goals there was only "bulking on": it carries over.
            kind = GoalKind.entries.firstOrNull { it.name == prefs.getString(K_GOAL, null) } ?: if (prefs.getBoolean(K_BULK_ON, false)) GoalKind.BULK else GoalKind.NONE,
            proteinPerKg = prefs.getFloat(K_GOAL_PROTEIN, 0f).takeIf { it > 0 }?.toDouble(),
            change = if (prefs.contains(K_GOAL_CHANGE)) prefs.getInt(K_GOAL_CHANGE, 0) else null,
            maintenance = prefs.getInt(K_GOAL_MAINTENANCE, 0).takeIf { it > 0 },
            gymDays = prefs.getStringSet(K_GOAL_GYM, emptySet()).orEmpty().mapNotNull { n -> java.time.DayOfWeek.entries.firstOrNull { it.name == n } }.toSet(),
        )
        set(v) {
            prefs.edit().putString(K_GOAL, v.kind.name).putFloat(K_GOAL_PROTEIN, v.proteinPerKg?.toFloat() ?: 0f)
                .apply { if (v.change == null) remove(K_GOAL_CHANGE) else putInt(K_GOAL_CHANGE, v.change) }
                .putInt(K_GOAL_MAINTENANCE, v.maintenance ?: 0).putStringSet(K_GOAL_GYM, v.gymDays.map { it.name }.toSet()).remove(K_BULK_ON).apply()
            _goal.value = v
        }
    private val _goal = MutableStateFlow(goal)
    val goalFlow: StateFlow<GoalSettings> = _goal.asStateFlow()

    /** A gym day: one of your gym days, or a workout today (logged, or started from Today). */
    fun gymToday(now: Instant = Instant.now(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Boolean {
        val today = now.atZone(zone).toLocalDate()
        val m = active.value
        return today.dayOfWeek in goal.gymDays || m.workoutUntil != null || m.lastWorkoutEnd?.atZone(zone)?.toLocalDate() == today
    }

    /** For Ask: the goal and its targets, or "" without one. */
    fun goalLine(profile: com.sukoon.app.insulin.Profile): String {
        val g = goal
        val t = Goals.targets(profile, g) ?: return ""
        val what = when (g.kind) {
            GoalKind.BULK -> "a lean bulk with resistance training (+100–300 kcal a day)"
            GoalKind.CUT -> "a cut (fat loss) keeping muscle, with resistance training"
            else -> "maintaining weight, with resistance training"
        }
        return listOfNotNull(
            "GOAL: $what.",
            t.kcal?.let { "About $it kcal a day (maintenance ${t.maintenance}${if (t.estimated) " estimated" else ""}${if (t.change != 0) " ${if (t.change > 0) "+" else ""}${t.change}" else ""})." },
            t.proteinGrams?.let { "Protein $it g a day (${t.proteinPerKg} g/kg), ${t.proteinPerMeal?.first}–${t.proteinPerMeal?.last} g a meal." },
            "Basal is glargine U-300 (can't be cut for one session); fat-heavy meals (tahina, eggs, oil) rise 3–5 h later.",
            if (g.kind == GoalKind.CUT) "Eating less usually means less insulin: watch for lows and review ratios." else null,
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
        const val K_WATCH_UNTIL = "meal_watch_until"
        const val K_WATCH_START = "meal_watch_start"
        const val K_WORKOUT_START = "workout_start"
        const val K_AFTER_CUT = "after_workout_cut"
        const val K_FAST_ON = "fasting_on"
        const val K_SUHOOR = "fasting_suhoor_ends"
        const val K_IFTAR = "fasting_iftar"
        const val K_SICK = "sick_since"
        const val K_BULK_ON = "bulk_on" // before goals
        const val K_GOAL = "goal"
        const val K_PLACE = "prayer_place"
        const val K_GOAL_PROTEIN = "goal_protein_per_kg"
        const val K_GOAL_CHANGE = "goal_change"
        const val K_GOAL_MAINTENANCE = "goal_maintenance"
        const val K_GOAL_GYM = "goal_gym_days"
        const val K_KETONE_REMINDED = "ketone_reminded"
        const val K_SUHOOR_REMINDED = "suhoor_reminded_day"
    }
}

