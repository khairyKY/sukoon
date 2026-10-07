package com.sukoon.app.ui.home

import com.sukoon.app.domain.metrics.TargetRange
import com.sukoon.app.alarms.AlarmEngine
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.data.source.libre.SensorLife
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.insulin.InsulinAction
import com.sukoon.app.insulin.InsulinOnBoard
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The one thing to do now, shown under Home's message; a brief with no step means there is nothing
 * to do. Never a dose: insulin only ever appears as "still working". Lows follow the 15-15 rule
 * (ADA Standards of Care); ketones are checked when high for hours (ADA: over 240–250).
 */
enum class Step {
    FAST_CARBS, RECHECK, CARBS_READY, KETONES, WATER, WATER_WALK, DONT_STACK, LET_IT_SETTLE, LOG_MEAL,
    SNACK_IF_MEAL_FAR, CARBS_BY_BED, CARBS_WITH_YOU, BEDTIME_SNACK, FINGERPRICK_FIRST,
}

/** What Home says under the number: the observation that matters most right now. The UI words it (EN/AR). */
sealed interface Brief {
    val step: Step?

    // Below 70.
    /** [again]: treated 15 to 60 minutes ago and still under 70 (the 15-15 rule's second round). */
    data class Low(val insulin: Double, val again: Boolean = false) : Brief { override val step = Step.FAST_CARBS }
    data class Treated(val at: Instant) : Brief { override val step = Step.RECHECK }

    // Worth acting on.
    data class HeadingLow(val minutes: Int, val insulin: Double) : Brief { override val step = Step.CARBS_READY }
    data class VeryHighFor(val minutes: Long) : Brief { override val step = Step.KETONES }
    data class Rebound(val lowAt: Instant) : Brief { override val step = Step.LET_IT_SETTLE }
    data class InsulinWorking(val units: Double) : Brief { override val step = Step.DONT_STACK }
    data class HighFor(val minutes: Long, val over250: Boolean) : Brief {
        override val step = when {
            over250 -> Step.WATER
            minutes >= 60 -> Step.WATER_WALK
            else -> null
        }
    }
    data class BackFromLow(val lowAt: Instant) : Brief { override val step = Step.SNACK_IF_MEAL_FAR }
    data object RisingNoMeal : Brief { override val step = Step.LOG_MEAL }
    data class Bedtime(val mgDl: Int, val insulin: Boolean) : Brief { override val step = Step.BEDTIME_SNACK }
    data class ActiveToday(val night: Boolean) : Brief { override val step = if (night) Step.CARBS_BY_BED else Step.CARBS_WITH_YOU }
    data object NewSensor : Brief { override val step = Step.FINGERPRICK_FIRST }

    // Nothing to do.
    data class AfterMeal(val grams: Int?, val at: Instant) : Brief { override val step: Step? = null }
    data object DawnRise : Brief { override val step: Step? = null }
    data object QuietNight : Brief { override val step: Step? = null }
    data object GoodNight : Brief { override val step: Step? = null }
    data class InRangeFor(val minutes: Long) : Brief { override val step: Step? = null }
    data class GoodDay(val percent: Int) : Brief { override val step: Step? = null }
    data class Steady(val variant: Int) : Brief { override val step: Step? = null }
}

/**
 * Picks Home's brief from everything Sukoon knows: the trend and where it's heading, insulin still
 * working, meals, activity, recent lows, the time of day, today so far and the sensor's age. Rules
 * run most urgent first and the first that fits wins. Pure and clock/zone-injected, so each is tested.
 */
object HomeBriefs {

    const val STEADY_VARIANTS = 4
    private const val MIN_UNITS = 0.5 // less rapid insulin than this isn't worth mentioning
    private val GAP = Duration.ofMinutes(20)
    private val TREATMENT_WAIT = Duration.ofMinutes(15)

    /** [readings] oldest first (the last day is plenty); null when there's no fresh reading to talk about. */
    fun of(
        readings: List<GlucoseReading>,
        events: List<EventEntity>,
        now: Instant,
        zone: ZoneId,
        action: InsulinAction = InsulinAction(),
        life: SensorLife? = null,
        /** When "I've treated it" was last pressed. */
        treatedAt: Instant? = null,
    ): Brief? {
        val latest = readings.lastOrNull()?.takeIf { Duration.between(it.timestamp, now) <= HomeUiStateMapper.STALE_AFTER } ?: return null
        val v = latest.glucoseMgDl
        val top = TargetRange.high // your range's top: "high" here matches Home's high state
        fun ago(minutes: Long): Instant = now.minus(Duration.ofMinutes(minutes))
        fun EventEntity.at(): Instant = Instant.ofEpochMilli(timestampMillis)
        fun last(type: LogEventType, withinMinutes: Long) = events.lastOrNull { it.logType == type && it.at() in ago(withinMinutes)..now }
        val insulin = InsulinOnBoard.total(events, now, action).takeIf { it >= MIN_UNITS } ?: 0.0

        if (v < 70) {
            val treated = listOfNotNull(treatedAt, last(LogEventType.CARB, TREATMENT_WAIT.toMinutes())?.at()).filter { it > now.minus(TREATMENT_WAIT) }.maxOrNull()
            val earlier = listOfNotNull(treatedAt, last(LogEventType.CARB, 60)?.at()).any { it > ago(60) }
            return if (treated != null) Brief.Treated(treated) else Brief.Low(insulin, again = earlier)
        }

        val hour = now.atZone(zone).hour
        val rising = latest.trend == TrendDirection.RISING || latest.trend == TrendDirection.RISING_FAST
        val falling = latest.trend == TrendDirection.FALLING || latest.trend == TrendDirection.FALLING_FAST
        val projected = AlarmEngine.projected(readings.takeLast(60), latest)
        val lastLow = InsightEngine.lowEpisodes(readings.filter { it.timestamp >= ago(180) }).lastOrNull()
        val meal = events.lastOrNull { it.logType == LogEventType.CARB && (it.value ?: 1.0) > 0 && it.at() in ago(180)..now }

        return when {
            projected < 70 -> Brief.HeadingLow((((v - 70) * 20) / (v - projected)).roundToInt().coerceAtLeast(1), insulin)
            v > 250 && run(readings) { it > 250 } >= 120 -> Brief.VeryHighFor(run(readings) { it > 250 })
            v > top && lastLow != null -> Brief.Rebound(lastLow.start)
            v > top && insulin > 0 -> Brief.InsulinWorking(insulin)
            meal != null && (v > top || rising) -> Brief.AfterMeal(meal.value?.roundToInt()?.takeIf { it > 0 }, meal.at())
            v > top -> Brief.HighFor(run(readings) { it > top }, over250 = v > 250)
            // In range from here on.
            lastLow != null && lastLow.end > ago(60) -> Brief.BackFromLow(lastLow.start)
            rising && hour in 4..8 -> Brief.DawnRise
            rising -> Brief.RisingNoMeal
            hour in 21..23 && v < 110 && (insulin > 0 || falling) -> Brief.Bedtime(v, insulin > 0)
            last(LogEventType.ACTIVITY, 12 * 60) != null && (hour >= 20 || hour < 6) -> Brief.ActiveToday(night = true)
            last(LogEventType.ACTIVITY, 6 * 60) != null && falling && v < 140 -> Brief.ActiveToday(night = false)
            life is SensorLife.Running && now < life.startedAt.plus(Duration.ofHours(25)) -> Brief.NewSensor
            hour in 0..4 -> Brief.QuietNight
            hour in 5..10 && goodNight(readings, now, zone) -> Brief.GoodNight
            run(readings) { it in 70..top } >= 180 -> Brief.InRangeFor(run(readings) { it in 70..top }) // 180 minutes
            // The calm message changes with the hour, not with every reading.
            else -> goodDay(readings, now, zone)?.let { Brief.GoodDay(it) } ?: Brief.Steady((now.epochSecond / 3600 % STEADY_VARIANTS).toInt())
        }
    }

    /** Minutes the newest reading's stretch has stayed [inside], back to a reading outside it or a gap. */
    internal fun run(r: List<GlucoseReading>, inside: (Int) -> Boolean): Long {
        var start = r.last().timestamp
        for (i in r.indices.reversed()) {
            if (!inside(r[i].glucoseMgDl)) break
            if (i < r.lastIndex && Duration.between(r[i].timestamp, r[i + 1].timestamp) > GAP) break
            start = r[i].timestamp
        }
        return Duration.between(start, r.last().timestamp).toMinutes()
    }

    /** Midnight to 6 am today: at least 4 hours of readings, every one in range. */
    private fun goodNight(r: List<GlucoseReading>, now: Instant, zone: ZoneId): Boolean {
        val midnight = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val night = r.filter { it.timestamp >= midnight && it.timestamp < midnight.plus(Duration.ofHours(6)) }
        return night.size >= 2 && Duration.between(night.first().timestamp, night.last().timestamp) >= Duration.ofHours(4) &&
            night.all { it.glucoseMgDl in 70..TargetRange.high }
    }

    /** Time in range since midnight, once there are 6 hours of today to judge and it's 80 % or better. */
    private fun goodDay(r: List<GlucoseReading>, now: Instant, zone: ZoneId): Int? {
        val midnight = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val today = r.filter { it.timestamp >= midnight }
        if (today.size < 2 || Duration.between(today.first().timestamp, today.last().timestamp) < Duration.ofHours(6)) return null
        return InsightEngine.summary(today, now, TargetRange.high)?.inYourRange?.roundToInt()?.takeIf { it >= 80 }
    }
}
