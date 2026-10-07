package com.sukoon.app.ui.widget

import com.sukoon.app.domain.metrics.TargetRange
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.domain.metrics.GlucoseMetrics
import com.sukoon.app.domain.metrics.GlucoseMetrics.RangeBracket
import com.sukoon.app.domain.metrics.GlucoseSample
import com.sukoon.app.ui.home.HomeUiStateMapper
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.insulin.InsulinOnBoard
import com.sukoon.app.insulin.InsulinAction
import com.sukoon.app.reminders.BasalReminder

/** Where a widget can sit: its launcher cells (columns × rows). Each has its own widget entry, so "Add to home screen" drops it at that size. */
enum class WidgetSize(val cols: Int, val rows: Int) { SMALL(1, 1), WIDE(2, 1), SQUARE(2, 2), STRIP(4, 1), LARGE(4, 2) }

/** What a widget can show besides the glucose number. Each style offers the ones that fit it ([WidgetStyle.infos]). */
enum class WidgetInfo { ARROW, CHANGE, AGO, GRAPH, TIR, CARBS, KCAL, IOB, LONG }

/** The widget designs (design canvas "Round 3 · Widgets"), the sizes each reads well at, and what each can show. */
enum class WidgetStyle(val sizes: List<WidgetSize>, val infos: List<WidgetInfo>, val defaults: Set<WidgetInfo>) {
    NUMBER(listOf(WidgetSize.SMALL, WidgetSize.WIDE), listOf(WidgetInfo.ARROW, WidgetInfo.CHANGE, WidgetInfo.AGO), setOf(WidgetInfo.ARROW, WidgetInfo.AGO)),
    CARD(
        listOf(WidgetSize.SQUARE, WidgetSize.LARGE, WidgetSize.WIDE),
        listOf(WidgetInfo.ARROW, WidgetInfo.CHANGE, WidgetInfo.AGO, WidgetInfo.GRAPH, WidgetInfo.TIR),
        setOf(WidgetInfo.ARROW, WidgetInfo.CHANGE, WidgetInfo.AGO, WidgetInfo.GRAPH, WidgetInfo.TIR),
    ),
    GRAPH(listOf(WidgetSize.LARGE, WidgetSize.STRIP), listOf(WidgetInfo.ARROW, WidgetInfo.AGO, WidgetInfo.TIR), setOf(WidgetInfo.ARROW, WidgetInfo.AGO)),
    RING(listOf(WidgetSize.SQUARE), listOf(WidgetInfo.ARROW, WidgetInfo.AGO), setOf(WidgetInfo.ARROW)),
    TODAY(
        listOf(WidgetSize.STRIP, WidgetSize.SQUARE, WidgetSize.LARGE),
        listOf(WidgetInfo.CARBS, WidgetInfo.KCAL, WidgetInfo.IOB, WidgetInfo.TIR, WidgetInfo.LONG),
        setOf(WidgetInfo.CARBS, WidgetInfo.KCAL, WidgetInfo.IOB, WidgetInfo.TIR),
    ),
    INSULIN(listOf(WidgetSize.SQUARE, WidgetSize.STRIP), listOf(WidgetInfo.IOB, WidgetInfo.LONG), setOf(WidgetInfo.IOB, WidgetInfo.LONG)),
    FOLLOWING(listOf(WidgetSize.SMALL, WidgetSize.WIDE, WidgetSize.SQUARE), listOf(WidgetInfo.ARROW, WidgetInfo.AGO), setOf(WidgetInfo.ARROW, WidgetInfo.AGO)),
}

/** Per-widget settings, chosen in the widget maker and stored in that widget's Glance state. */
data class WidgetOptions(
    val style: WidgetStyle = WidgetStyle.CARD,
    /** How far back the graph goes, when it shows one. */
    val graphHours: Int = 3,
    val info: Set<WidgetInfo> = style.defaults,
    val background: WidgetBackground = WidgetBackground.AUTO,
    /** [WidgetStyle.FOLLOWING]: whose glucose (their user id); null = the first person followed. */
    val person: String? = null,
) {
    fun shows(item: WidgetInfo) = item in info && item in style.infos
    val graphShown: Boolean get() = style == WidgetStyle.GRAPH || shows(WidgetInfo.GRAPH)
    val showDetails: Boolean get() = shows(WidgetInfo.AGO) || shows(WidgetInfo.CHANGE)

    companion object {
        val GRAPH_CHOICES = listOf(1, 3, 6, 12, 24)
    }
}

enum class WidgetBackground { AUTO, LIGHT, DARK, CLEAR }

/** Which arrangement fits the space the user gave the widget (dp from Glance's exact size). */
enum class WidgetLayout { TINY, STRIP, CARD }

fun layoutFor(widthDp: Float, heightDp: Float): WidgetLayout = when {
    widthDp < ONE_CELL_MAX || heightDp < ONE_CELL_MAX && widthDp < STRIP_MIN_WIDTH -> WidgetLayout.TINY // 1×n and 2×1
    heightDp < ONE_CELL_MAX -> WidgetLayout.STRIP // wide and one row tall
    else -> WidgetLayout.CARD // 2×2 up to half the screen
}

// Launcher cells are ~60–100 dp, so one cell never reaches 120 dp but two always do.
private const val ONE_CELL_MAX = 120f
private const val STRIP_MIN_WIDTH = 180f

/** Everything any widget layout shows, derived from the last 24 h of readings. Pure + clock-injected. */
data class WidgetModel(
    val mgDl: Int?,
    val trend: TrendDirection?,
    val bracket: RangeBracket?,
    /** PLAN §5: an old reading is greyed out and never shown as current. */
    val stale: Boolean,
    val minutesAgo: Long?,
    /** Change over the last ~5 minutes, mg/dL. */
    val delta: Int?,
    /** % of today's readings in 70–180. */
    val tirTodayPercent: Int?,
    val graph: List<GlucoseSample>,
) {
    companion object {
        fun build(readings: List<GlucoseReading>, now: Instant, zone: ZoneId, graphHours: Int): WidgetModel {
            val latest = readings.maxByOrNull { it.timestamp }
                ?: return WidgetModel(null, null, null, stale = true, minutesAgo = null, delta = null, tirTodayPercent = null, graph = emptyList())
            val age = Duration.between(latest.timestamp, now)
            val fiveMinutesBefore = readings
                .filter { Duration.between(it.timestamp, latest.timestamp).toMinutes() in 3..7 }
                .minByOrNull { abs(Duration.between(it.timestamp, latest.timestamp).seconds - 300) }
            val midnight = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
            val today = readings.filter { it.timestamp >= midnight }.map { GlucoseSample(it.timestamp, it.glucoseMgDl) }
            return WidgetModel(
                mgDl = latest.glucoseMgDl,
                trend = latest.trend,
                bracket = GlucoseMetrics.bracketFor(latest.glucoseMgDl, TargetRange.high),
                stale = age > HomeUiStateMapper.STALE_AFTER,
                minutesAgo = age.toMinutes().coerceAtLeast(0),
                delta = fiveMinutesBefore?.let { latest.glucoseMgDl - it.glucoseMgDl },
                tirTodayPercent = if (today.isEmpty()) null else GlucoseMetrics.timeInRange(today, TargetRange.high).getValue(RangeBracket.IN_RANGE).roundToInt(),
                graph = readings
                    .filter { graphHours > 0 && it.timestamp >= now.minus(Duration.ofHours(graphHours.toLong())) }
                    .map { GlucoseSample(it.timestamp, it.glucoseMgDl) },
            )
        }
    }
}

/** CGM-convention arrows, one per trend bucket (shared by Home and the widgets). */
val TrendDirection.arrow: String
    get() = when (this) {
        TrendDirection.FALLING_FAST -> "↓"
        TrendDirection.FALLING -> "↘"
        TrendDirection.STEADY -> "→"
        TrendDirection.RISING -> "↗"
        TrendDirection.RISING_FAST -> "↑"
    }

/** The followed person a [WidgetStyle.FOLLOWING] widget shows. */
data class PersonReading(val name: String, val mgDl: Int?, val trend: TrendDirection?, val minutesAgo: Long?, val stale: Boolean)

/** Today's numbers and insulin for the Today and Insulin widgets. Pure + clock-injected. */
data class WidgetExtras(
    val carbs: Double,
    val kcal: Double?,
    val iob: Double,
    val lastRapidUnits: Double?,
    val lastRapidAt: Instant?,
    /** Long-acting logged in the last 12 hours (the reminder's rule): when. */
    val longTakenAt: Instant?,
    /** The last long-acting amount: what the widget's "Took it" logs. */
    val lastLongDose: Double?,
    val person: PersonReading? = null,
) {
    companion object {
        val NONE = WidgetExtras(0.0, null, 0.0, null, null, null, null)

        fun build(events: List<EventEntity>, now: Instant, zone: ZoneId, action: InsulinAction, person: PersonReading? = null): WidgetExtras {
            val midnight = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val today = events.filter { it.timestampMillis in midnight..now.toEpochMilli() }
            val meals = today.filter { it.logType == LogEventType.CARB }
            val rapid = events.filter { it.logType == LogEventType.INSULIN && it.timestampMillis <= now.toEpochMilli() }.maxByOrNull { it.timestampMillis }
            val long = events.filter { it.logType == LogEventType.BASAL && it.timestampMillis <= now.toEpochMilli() }.maxByOrNull { it.timestampMillis }
            return WidgetExtras(
                carbs = meals.sumOf { it.value ?: 0.0 },
                kcal = meals.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.sum(),
                iob = InsulinOnBoard.total(events, now, action),
                lastRapidUnits = rapid?.value,
                lastRapidAt = rapid?.let { Instant.ofEpochMilli(it.timestampMillis) },
                longTakenAt = long?.let { Instant.ofEpochMilli(it.timestampMillis) }?.takeIf { BasalReminder.taken(listOfNotNull(long), now) },
                lastLongDose = BasalReminder.lastDose(events),
                person = person,
            )
        }

        fun person(name: String, latest: GlucoseReading?, now: Instant): PersonReading {
            val age = latest?.let { Duration.between(it.timestamp, now) }
            return PersonReading(name, latest?.glucoseMgDl, latest?.trend, age?.toMinutes()?.coerceAtLeast(0), age == null || age > HomeUiStateMapper.STALE_AFTER)
        }
    }
}
