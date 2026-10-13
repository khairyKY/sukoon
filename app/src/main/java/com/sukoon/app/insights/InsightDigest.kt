package com.sukoon.app.insights

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import java.time.Instant
import java.time.ZoneId

/**
 * What the app can do about a finding (docs/insights-rework.md). Each finding lists them in order;
 * the first one that would change something becomes its button, else [LOOK_AT].
 */
enum class InsightAction {
    TURN_ON_GOING_LOW,
    TURN_ON_WORKOUT_CHECK,
    OPEN_DOSE_SETUP,
    OPEN_LEARNING,
    LOG_INSULIN,
    OPEN_CALIBRATION,
    OPEN_SITES,
    LOG_MEAL,
    REPORT,
    LOOK_AT,
    ;

    /** Whether this would change anything given what's already on. */
    fun applies(on: InEffect): Boolean = when (this) {
        TURN_ON_GOING_LOW -> !on.goingLow
        TURN_ON_WORKOUT_CHECK -> !on.workoutCheck
        OPEN_DOSE_SETUP -> !on.doseSuggestions
        OPEN_LEARNING -> on.doseSuggestions // the ratios only matter while suggestions use them
        OPEN_CALIBRATION -> !on.calibration
        else -> true
    }
}

/** What's on already, so an action is never offered for something in effect. */
data class InEffect(
    val goingLow: Boolean = false,
    val workoutCheck: Boolean = false,
    val doseSuggestions: Boolean = false,
    val calibration: Boolean = false,
)

/** The lows told once: when they cluster, how often, and the likeliest cause. */
enum class LowCause { WORKOUTS, STACKING }

/** One finding, ready to show: [insight] carries the numbers the UI words; [actions] in order. */
data class Finding(val insight: Insight, val priority: Int, val actions: List<InsightAction>) {
    fun action(on: InEffect): InsightAction = actions.firstOrNull { it.applies(on) } ?: InsightAction.LOOK_AT
}

/** Lows merged from five rules: [recurring] (when), [nights] with lows, and the likeliest [cause]. */
data class LowsStory(
    val recurring: Insight.RecurringLows?,
    val nightsWithLows: Int,
    val nights: Int,
    val cause: LowCause?,
    val totalLows: Int,
) : Insight {
    override val level = Level.URGENT
}

/**
 * The screen's shape: the 14-day line, the one thing, up to three more, the rest folded, or what to
 * log to get there. Built from [InsightEngine.analyze] so every rule keeps its tests and sources.
 */
data class InsightDigest(
    val days: Int,
    val inRange: Int?,
    /** Time in range against the week before (WeekOverWeek), when there's a week before. */
    val inRangeChange: Int?,
    val targets: Insight.Targets?,
    val oneThing: Finding?,
    val alsoWorth: List<Finding>,
    val more: List<Finding>,
    val notEnough: NotEnough?,
)

/** What to log to unlock more: readings (days of wear) or meals (days with a meal logged). */
sealed interface NotEnough {
    data class Readings(val days: Int, val needed: Int = 3) : NotEnough
    data class Meals(val days: Int, val needed: Int = MEAL_DAYS) : NotEnough
}

const val MEAL_DAYS = 5

object InsightDigestBuilder {

    fun build(insights: List<Insight>, events: List<EventEntity>, now: Instant, zone: ZoneId): InsightDigest {
        (insights.firstOrNull { it is Insight.NotEnoughData } as? Insight.NotEnoughData)?.let {
            return InsightDigest(it.daysWithData, null, null, null, null, emptyList(), emptyList(), NotEnough.Readings(it.daysWithData))
        }
        val targets = insights.filterIsInstance<Insight.Targets>().firstOrNull()
        val week = insights.filterIsInstance<Insight.WeekOverWeek>().firstOrNull()
        val findings = findings(insights).sortedByDescending { it.priority }
        val worth = findings.filter { it.insight.level != Level.GOOD }
        val oneThing = worth.firstOrNull { it.insight.level >= Level.ATTENTION }
        val also = worth.filter { it !== oneThing }.take(3)
        val more = findings.filter { f -> f !== oneThing && also.none { it === f } }
        val mealDays = events.filter { it.logType == LogEventType.CARB && Instant.ofEpochMilli(it.timestampMillis).isAfter(now.minusSeconds(InsightEngine.WINDOW_DAYS * 86_400)) }
            .map { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }.distinct().size
        val notEnough = if (oneThing == null && insights.none { it is Insight.MealOutcomes } && mealDays < MEAL_DAYS) NotEnough.Meals(mealDays) else null
        return InsightDigest(
            days = targets?.days ?: 0,
            inRange = targets?.inRange,
            inRangeChange = week?.let { it.inRange - it.inRangeBefore },
            targets = targets,
            oneThing = oneThing,
            alsoWorth = also,
            more = more,
            notEnough = notEnough,
        )
    }

    /** Every rule's insight as a finding; the five low rules become one [LowsStory]; formulas and the summary rows aren't findings. */
    internal fun findings(insights: List<Insight>): List<Finding> {
        val recurring = insights.filterIsInstance<Insight.RecurringLows>().firstOrNull()
        val nights = insights.filterIsInstance<Insight.Nights>().firstOrNull()
        val stacking = insights.filterIsInstance<Insight.Stacking>().firstOrNull()
        val workouts = insights.filterIsInstance<Insight.ActivityLows>().firstOrNull()
        val targets = insights.filterIsInstance<Insight.Targets>().firstOrNull()
        val totalLows = recurring?.totalEpisodes ?: stacking?.totalLows ?: 0
        val cause = when {
            workouts != null && workouts.followed >= 2 -> LowCause.WORKOUTS
            stacking != null && stacking.lowsAfterStacking * 3 >= stacking.totalLows -> LowCause.STACKING
            else -> null
        }
        val out = mutableListOf<Finding>()
        val lowsWorthSaying = recurring != null || (nights?.withLows ?: 0) >= 2 || cause != null || (targets != null && !targets.metBelow70)
        if (lowsWorthSaying) {
            val first = when (cause) {
                LowCause.WORKOUTS -> listOf(InsightAction.TURN_ON_WORKOUT_CHECK)
                LowCause.STACKING -> listOf(InsightAction.OPEN_DOSE_SETUP)
                null -> emptyList()
            }
            out += Finding(
                LowsStory(recurring, nights?.withLows ?: 0, nights?.nights ?: 0, cause, totalLows),
                priority = 100,
                actions = first + listOf(InsightAction.TURN_ON_GOING_LOW, InsightAction.LOOK_AT, InsightAction.REPORT),
            )
        }
        // The hardest meal time (most rise) is one finding; the other meal times fold away.
        val meals = insights.filterIsInstance<Insight.MealOutcomes>()
        val hardest = meals.filter { it.level != Level.GOOD }.maxByOrNull { it.averagePeak }
        meals.forEach { m ->
            val lowsAfter = m.lowWithin4h >= 2
            out += Finding(
                m,
                priority = when { lowsAfter -> 90; m === hardest -> 60; else -> 15 },
                actions = listOf(InsightAction.OPEN_LEARNING, InsightAction.LOG_INSULIN, InsightAction.LOOK_AT),
            )
        }
        insights.forEach { i ->
            when (i) {
                is Insight.RecurringHighs -> out += Finding(i, 55, listOf(InsightAction.LOOK_AT))
                is Insight.Rebounds -> out += Finding(i, 58, listOf(InsightAction.LOOK_AT))
                is Insight.LowTreatments -> if (i.level == Level.ATTENTION) out += Finding(i, 57, listOf(InsightAction.LOOK_AT))
                is Insight.MeterAgreement -> out += Finding(i, if (i.level == Level.GOOD) 5 else 52, listOf(InsightAction.OPEN_CALIBRATION, InsightAction.LOOK_AT))
                is Insight.DawnRise -> out += Finding(i, 50, listOf(InsightAction.LOOK_AT, InsightAction.REPORT))
                is Insight.Variability -> out += Finding(i, if (i.level == Level.GOOD) 8 else 45, listOf(InsightAction.LOOK_AT))
                is Insight.Rotation -> out += Finding(i, 40, listOf(InsightAction.OPEN_SITES))
                is Insight.RichMeals -> out += Finding(i, if (i.level == Level.ATTENTION) 35 else 12, listOf(InsightAction.LOOK_AT))
                is Insight.PreBolus -> out += Finding(i, 30, listOf(InsightAction.LOG_INSULIN))
                is Insight.CarbDays -> out += Finding(i, 25, listOf(InsightAction.LOOK_AT))
                is Insight.CarbResponse -> out += Finding(i, 20, listOf(InsightAction.OPEN_LEARNING, InsightAction.LOOK_AT))
                is Insight.Nights -> if (!lowsWorthSaying) out += Finding(i, if (i.level == Level.GOOD) 10 else 40, listOf(InsightAction.TURN_ON_GOING_LOW, InsightAction.LOOK_AT))
                else -> Unit // targets and week-over-week are the 14-day line; formulas live in dose setup; lows are in the story
            }
        }
        return out
    }
}
