package com.sukoon.app.ui.widget

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

/** Per-widget settings, chosen in WidgetConfigActivity and stored in that widget's Glance state. */
data class WidgetOptions(
    /** 0 hides the graph. */
    val graphHours: Int = 3,
    val showDetails: Boolean = true,
    val background: WidgetBackground = WidgetBackground.AUTO,
) {
    companion object {
        val GRAPH_CHOICES = listOf(0, 1, 3, 6, 12, 24)
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
                bracket = GlucoseMetrics.bracketFor(latest.glucoseMgDl),
                stale = age > HomeUiStateMapper.STALE_AFTER,
                minutesAgo = age.toMinutes().coerceAtLeast(0),
                delta = fiveMinutesBefore?.let { latest.glucoseMgDl - it.glucoseMgDl },
                tirTodayPercent = if (today.isEmpty()) null else GlucoseMetrics.timeInRange(today).getValue(RangeBracket.IN_RANGE).roundToInt(),
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
