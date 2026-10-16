package com.sukoon.app.modes

import com.sukoon.app.alarms.AlarmSettings
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Fasting (Ramadan): your fast runs from [suhoorEnds] (Fajr) to [iftar] (Maghrib) every day it's on.
 * Times are yours to set: they move about a minute a day through the month.
 */
data class FastingSettings(val on: Boolean = false, val suhoorEnds: LocalTime = LocalTime.of(4, 20), val iftar: LocalTime = LocalTime.of(17, 40))

/** What's on right now (docs/research/modes-sources.md for every number). */
data class ActiveModes(
    /** In a workout: started from Modes, or a logged workout still going. */
    val workoutUntil: Instant? = null,
    /** The last workout's end, within the last [Modes.LATE_LOW] (lows can follow for a day). */
    val lastWorkoutEnd: Instant? = null,
    /** Inside the late-low watch's night hours: the low alarm sits at [Modes.NIGHT_LOW]. */
    val nightWatch: Boolean = false,
    val fasting: FastingSettings = FastingSettings(),
    /** Inside today's fast. */
    val fastingNow: Boolean = false,
    val sickSince: Instant? = null,
    /** Watching after a meal smaller than its insulin: the low alarm sits at [Modes.MEAL_WATCH_LOW] until then. */
    val mealWatchUntil: Instant? = null,
) {
    val workingOut: Boolean get() = workoutUntil != null
    val lateLowWatch: Boolean get() = lastWorkoutEnd != null && workoutUntil == null
    val sick: Boolean get() = sickSince != null
    val any: Boolean get() = workingOut || lateLowWatch || fasting.on || sick || mealWatchUntil != null
}

/** Why a dose suggestion was changed, and by how much (shown with the suggestion). */
data class DoseChange(val reason: Reason, val mealFactor: Double = 1.0, val correctionFactor: Double = 1.0) {
    enum class Reason { AFTER_WORKOUT, IN_WORKOUT, SUHOOR }
}

object Modes {
    /** Low alarm during a workout (Moser et al., Diabetologia 2020: 100 mg/dL). */
    const val WORKOUT_LOW = 100
    /** Low alarm overnight after a workout (Moser 2020: 80 mg/dL). */
    const val NIGHT_LOW = 80

    /** After eating less than the insulin was for: the low alarm sits here until the extra is spent. */
    const val MEAL_WATCH_LOW = 90
    /** How long lows can follow exercise (Riddell et al., Lancet D&E 2017: at least 24 h). */
    val LATE_LOW: Duration = Duration.ofHours(24)
    /** The meal "after" a workout: within this of its end. */
    val AFTER_WORKOUT: Duration = Duration.ofHours(2)
    /** Corrections during or just after lifting: half (Moser 2020, resistance-training rises). */
    const val WORKOUT_CORRECTION_FACTOR = 0.5
    /** IDF-DAR 2021: break the fast under 70 or over 300; re-check within an hour at 70–90. */
    const val FAST_BREAK_LOW = 70
    const val FAST_BREAK_HIGH = 300
    const val FAST_RECHECK = 90
    /** IDF-DAR 2021 (type 1 chapter): suhoor rapid insulin −30–50%; the cautious end of the start. */
    const val SUHOOR_CUT_PERCENT = 30
    /** The suhoor meal: eaten in the last hours before the fast starts. */
    val SUHOOR_WINDOW: Duration = Duration.ofHours(3)
    /** ISPAD 2022: ketones at least every 1–2 hours when sick. */
    val KETONES_EVERY: Duration = Duration.ofHours(2)

    /** Workouts in the logbook as (start, end): an activity entry's time plus its minutes. */
    fun workouts(events: List<EventEntity>): List<Pair<Instant, Instant>> = events
        .filter { it.logType == LogEventType.ACTIVITY }
        .map { e -> Instant.ofEpochMilli(e.timestampMillis).let { it to it.plus(Duration.ofSeconds(((e.value ?: 0.0) * 60).toLong())) } }

    fun active(
        now: Instant,
        zone: ZoneId,
        /** "Start a workout" in Modes: on until then. */
        manualWorkoutUntil: Instant?,
        /** When that manual workout started (it ends a workout too, once it's over). */
        manualWorkoutStart: Instant?,
        events: List<EventEntity>,
        fasting: FastingSettings,
        sickSince: Instant?,
    ): ActiveModes {
        val sessions = workouts(events) + listOfNotNull(manualWorkoutStart?.let { s -> manualWorkoutUntil?.let { s to it } })
        val workoutUntil = sessions.filter { (start, end) -> !now.isBefore(start) && now.isBefore(end) }.maxOfOrNull { it.second }
        val lastEnd = sessions.map { it.second }.filter { !it.isAfter(now) && Duration.between(it, now) < LATE_LOW }.maxOrNull()
        val hour = now.atZone(zone).hour
        val time = now.atZone(zone).toLocalTime()
        return ActiveModes(
            workoutUntil = workoutUntil,
            lastWorkoutEnd = lastEnd,
            nightWatch = workoutUntil == null && lastEnd != null && (hour >= 22 || hour < 7),
            fasting = fasting,
            fastingNow = fasting.on && !time.isBefore(fasting.suhoorEnds) && time.isBefore(fasting.iftar),
            sickSince = sickSince,
        )
    }

    /** Your alarm settings with today's modes applied: what the alarms judge by. */
    fun alarmSettings(base: AlarmSettings, m: ActiveModes): AlarmSettings {
        var s = base
        val lowLine = when {
            m.workingOut -> WORKOUT_LOW
            m.mealWatchUntil != null -> MEAL_WATCH_LOW
            m.nightWatch -> NIGHT_LOW
            else -> null
        }
        if (lowLine != null) s = s.copy(lowEnabled = true, lowMgDl = maxOf(s.lowMgDl, lowLine), goingLowEnabled = true)
        if (m.sick) s = s.copy(highSnoozeMinutes = minOf(s.highSnoozeMinutes, 60)) // ISPAD: look again within 1–2 h
        return s
    }

    /**
     * How today's modes change a dose suggestion for a meal at [mealAt] (null: a correction only),
     * or null when they don't. [afterWorkoutCutPercent]: the meal after a workout (Riddell 2017: about
     * 50%); before lifting nothing is cut (Riddell: not advised for brief intense exercise).
     */
    fun doseChange(m: ActiveModes, now: Instant, mealAt: Instant?, zone: ZoneId, afterWorkoutCutPercent: Int): DoseChange? {
        val correction = if (m.workingOut || (m.lastWorkoutEnd != null && Duration.between(m.lastWorkoutEnd, now) <= AFTER_WORKOUT)) WORKOUT_CORRECTION_FACTOR else 1.0
        val afterWorkout = mealAt != null && m.lastWorkoutEnd != null && !mealAt.isBefore(m.lastWorkoutEnd) &&
            Duration.between(m.lastWorkoutEnd, mealAt) <= AFTER_WORKOUT && afterWorkoutCutPercent > 0
        val suhoor = mealAt != null && m.fasting.on && mealAt.atZone(zone).toLocalTime().let { t ->
            val start = m.fasting.suhoorEnds.minus(SUHOOR_WINDOW)
            if (start.isBefore(m.fasting.suhoorEnds)) !t.isBefore(start) && t.isBefore(m.fasting.suhoorEnds)
            else !t.isBefore(start) || t.isBefore(m.fasting.suhoorEnds) // the window wraps midnight
        }
        return when {
            afterWorkout -> DoseChange(DoseChange.Reason.AFTER_WORKOUT, mealFactor = 1 - afterWorkoutCutPercent / 100.0, correctionFactor = correction)
            suhoor -> DoseChange(DoseChange.Reason.SUHOOR, mealFactor = 1 - SUHOOR_CUT_PERCENT / 100.0)
            correction < 1.0 -> DoseChange(DoseChange.Reason.IN_WORKOUT, correctionFactor = correction)
            else -> null
        }
    }

    /** What to do before lifting at [mgDl] (Riddell 2017 / Moser 2020); null when nothing is needed. */
    fun beforeWorkout(mgDl: Int?): BeforeWorkout? = when {
        mgDl == null -> null
        mgDl < 90 -> BeforeWorkout.CARBS_AND_WAIT // 10–20 g, and wait
        mgDl < 126 -> BeforeWorkout.SMALL_CARBS // about 10 g
        mgDl > 270 -> BeforeWorkout.KETONES_FIRST
        else -> null
    }

    enum class BeforeWorkout { CARBS_AND_WAIT, SMALL_CARBS, KETONES_FIRST }

    /** ISPAD 2022 blood-ketone bands, mmol/L. */
    enum class KetoneBand { NORMAL, RAISED, MODERATE, HIGH, DANGER }

    fun ketoneBand(mmol: Double): KetoneBand = when {
        mmol < 0.6 -> KetoneBand.NORMAL
        mmol < 1.0 -> KetoneBand.RAISED
        mmol < 1.5 -> KetoneBand.MODERATE
        mmol < 3.0 -> KetoneBand.HIGH
        else -> KetoneBand.DANGER
    }
}
