package com.sukoon.app.ui.graph

import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.domain.metrics.TargetRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.insights.RangeSummary
import com.sukoon.app.ui.home.HomeUiStateMapper
import com.sukoon.app.ui.logbook.colorForLogEventType
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import com.sukoon.app.ui.theme.SukoonTheme
import com.sukoon.app.ui.widget.arrow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import com.sukoon.app.ui.theme.Motion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.pluralStringResource

// Fixed bottom of the y-axis; the top adapts to the data (see yMaxFor). The band is your range (70 to TargetRange.high).
private const val Y_MIN = 40
private const val LOW = 70
private val HIGH get() = TargetRange.high
private val CHART_HEIGHT = 256.dp
private val READINGS_HEIGHT = 420.dp // the readings box; it scrolls inside, the page around it
private const val BUCKET_MS = 15 * 60_000L // 7/14-day charts draw 15-minute means
private const val GAP_MS = 15 * 60_000L // a longer silence breaks the line instead of bridging it

private val hm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/**
 * Trends → Graph (design 8j, overhauled 2026-10-03): the current value and trend, a chart colored by
 * range with only the numbers that matter (70/180 and a few clock times), time in range + average +
 * GMI for the chosen range, then every reading, newest first, in its own scrolling box.
 * Canvas-drawn: one chart doesn't justify a chart library.
 */
@Composable
fun GraphScreen(
    state: GraphUiState,
    onSelectRange: (GraphRange) -> Unit,
    modifier: Modifier = Modifier,
    /** Someone else's name when following them; "Graph" for your own. */
    title: String? = null,
) {
    val zone = remember { ZoneId.systemDefault() }
    // A day can be 1,440 readings: their text is built off the main thread, so scrolling only draws.
    val days by produceState(emptyList<ReadingDay>(), state.readings) {
        value = withContext(Dispatchers.Default) { readingDays(state.readings, zone) }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        Header(state.readings.lastOrNull(), title)
        Spacer(Modifier.height(16.dp))
        if (state.readings.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.graph_empty), color = CaptionMuted, fontSize = 14.sp)
            }
        } else {
            GlucoseChart(state.readings, state.range, state.events, zone)
        }
        Spacer(Modifier.height(12.dp))
        RangeSelector(state.range, onSelectRange)
        state.summary?.let { summary ->
            Spacer(Modifier.height(16.dp))
            RangeStats(summary, stringResource(R.string.graph_tir_title, rangeLabel(state.range)), showGmi = state.range.hours >= 24)
        }
        if (days.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Eyebrow(stringResource(R.string.graph_readings))
            Spacer(Modifier.height(8.dp))
            ReadingsList(days)
        }
    }
}

/**
 * Every reading in the range, newest first, in its own scrolling box; each day's name stays pinned
 * at the top while you scroll through it. Reaching either end hands the scroll back to the page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadingsList(days: List<ReadingDay>) {
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .heightIn(max = READINGS_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        days.forEach { day ->
            stickyHeader(key = "day-${day.date}", contentType = "day") { DayHeader(day.date) }
            items(day.rows, key = { it.key }, contentType = { "reading" }) { ReadingRow(it) }
        }
    }
}

/** The current value and where it's heading — the graph's answer to "where am I now". */
@Composable
private fun Header(latest: GlucoseReading?, title: String?) {
    Column {
        Text(title ?: stringResource(R.string.graph_title), fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        if (latest == null) return@Column
        val minutes = Duration.between(latest.timestamp, Instant.now()).toMinutes().coerceAtLeast(0)
        val stale = minutes > HomeUiStateMapper.STALE_AFTER.toMinutes()
        val color = if (stale) CaptionMuted else valueColor(latest.glucoseMgDl)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format(Locale.getDefault(), "%d", latest.glucoseMgDl),
                fontFamily = HeadlineSerifFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 56.sp,
                lineHeight = 56.sp,
                color = color,
            )
            Spacer(Modifier.width(8.dp))
            Text(latest.trend.arrow, fontSize = 32.sp, color = color, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            listOf(
                stringResource(R.string.home_unit_mgdl),
                trendLabel(latest.trend),
                if (minutes < 1) stringResource(R.string.graph_just_now) else pluralStringResource(R.plurals.graph_min_ago, minutes.toInt(), minutes.toInt()),
            ).joinToString(" · "),
            fontSize = 12.sp,
            color = CaptionMuted,
        )
    }
}

/** Also Home's last-3-hours chart: shorter there, and without tap-to-read ([interactive] = false). */
@Composable
internal fun GlucoseChart(
    readings: List<GlucoseReading>,
    range: GraphRange,
    events: List<EventEntity>,
    zone: ZoneId,
    height: Dp = CHART_HEIGHT,
    interactive: Boolean = true,
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val background = MaterialTheme.colorScheme.background
    // The chart always ends at "now", so a signal gap at the end shows as a gap.
    val (tStart, tEnd, points) = remember(readings, range) {
        val end = maxOf(System.currentTimeMillis(), readings.last().timestamp.toEpochMilli())
        val start = end - range.millis
        val shown = readings.filter { it.timestamp.toEpochMilli() >= start }
        Triple(start, end, if (range.hours > 24) downsample(shown, BUCKET_MS) else shown)
    }
    val yMax = remember(points) { yMaxFor(points) }
    val ticks = remember(tStart, range) { timeTicks(tStart, tEnd, range.tickHours, zone) }
    val tickFormat = remember(range) {
        DateTimeFormatter.ofPattern(
            when {
                range.hours <= 24 -> "HH:mm"
                range == GraphRange.D7 -> "EEE"
                else -> "d MMM"
            },
        ).withZone(zone)
    }
    val measurer = rememberTextMeasurer()
    val caption = TextStyle(fontSize = 12.sp, color = CaptionMuted)
    val pinColors = LogEventType.entries.associateWith { colorForLogEventType(it) }
    val span = (tEnd - tStart).toFloat()
    var selected by remember(range) { mutableStateOf<GlucoseReading?>(null) }
    // Graph draw-on (motion spec): 900 ms left to right on each range; "remove animations" shows it whole.
    val reduced = Motion.reduced()
    val reveal = remember(range) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(range) { reveal.animateTo(1f, tween(Motion.DRAW, easing = Motion.Out)) }

    Column {
        val sel = selected
        if (interactive) Text(
            text = if (sel != null) {
                "${hm.format(sel.timestamp)} · ${String.format(Locale.getDefault(), "%d", sel.glucoseMgDl)} ${stringResource(R.string.home_unit_mgdl)} ${sel.trend.arrow}"
            } else {
                stringResource(R.string.graph_tap_hint)
            },
            color = if (sel != null) onBg else CaptionMuted,
            fontWeight = if (sel != null) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = if (sel != null) 14.sp else 12.sp,
        )
        if (interactive) Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clipToBounds()
                .graphicsLayer() // its own layer: scrolling the readings list moves it instead of redrawing every point each frame
                .pointerInput(points, range, interactive) {
                    if (!interactive) return@pointerInput
                    detectTapGestures { offset ->
                        val t = tStart + (offset.x / size.width * span).toLong()
                        selected = points.minByOrNull { abs(it.timestamp.toEpochMilli() - t) }
                    }
                },
        ) {
            val plotHeight = size.height - 20.dp.toPx() // bottom strip for the clock labels
            fun x(millis: Long) = (millis - tStart) / span * size.width
            fun y(mgDl: Int) = plotHeight * (1f - (mgDl.coerceIn(Y_MIN, yMax) - Y_MIN).toFloat() / (yMax - Y_MIN))

            // Target band; its two edges are the only y-axis numbers.
            drawRect(Sage.copy(alpha = 0.10f), topLeft = Offset(0f, y(HIGH)), size = Size(size.width, y(LOW) - y(HIGH)))
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            listOf(LOW, HIGH).forEach { level ->
                drawLine(onBg.copy(alpha = 0.15f), Offset(0f, y(level)), Offset(size.width, y(level)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                val label = measurer.measure(String.format(Locale.getDefault(), "%d", level), caption)
                drawText(label, topLeft = Offset(size.width - label.size.width - 2.dp.toPx(), y(level) - label.size.height - 2.dp.toPx()))
            }

            // A few round clock times along the bottom.
            ticks.forEach { t ->
                val label = measurer.measure(tickFormat.format(Instant.ofEpochMilli(t)), caption)
                val left = (x(t) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(left, plotHeight + 4.dp.toPx()))
            }

            // The line, broken across signal gaps and colored by where it sits: red under 70, amber over 180.
            val gap = if (range.hours > 24) 2 * BUCKET_MS else GAP_MS
            fun joined(i: Int) = i > 0 && points[i].timestamp.toEpochMilli() - points[i - 1].timestamp.toEpochMilli() <= gap
            val path = Path()
            points.forEachIndexed { i, r ->
                if (joined(i)) path.lineTo(x(r.timestamp.toEpochMilli()), y(r.glucoseMgDl)) else path.moveTo(x(r.timestamp.toEpochMilli()), y(r.glucoseMgDl))
            }
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val drawn = size.width * reveal.value
            clipRect(right = drawn, bottom = y(HIGH)) { drawPath(path, StateHigh, style = stroke) }
            clipRect(right = drawn, top = y(HIGH), bottom = y(LOW)) { drawPath(path, Sage, style = stroke) }
            clipRect(right = drawn, top = y(LOW)) { drawPath(path, StateLow, style = stroke) }
            // A reading with no neighbour on either side would be an invisible path segment: dot it.
            points.indices.filter { !joined(it) && !(it + 1 < points.size && joined(it + 1)) }.forEach { i ->
                drawCircle(valueColor(points[i].glucoseMgDl, Sage), radius = 2.dp.toPx(), center = Offset(x(points[i].timestamp.toEpochMilli()), y(points[i].glucoseMgDl)))
            }

            // Logged events: dots along the top; finger-pricks as rings at their own value.
            events.forEach { event ->
                if (event.timestampMillis in tStart..tEnd) {
                    val ex = x(event.timestampMillis)
                    val color = pinColors.getValue(event.logType)
                    val meter = event.value?.toInt()?.takeIf { event.logType == LogEventType.FINGERSTICK }
                    if (meter != null) {
                        drawCircle(Color.White, radius = 7f, center = Offset(ex, y(meter)))
                        drawCircle(color, radius = 7f, center = Offset(ex, y(meter)), style = Stroke(width = 3.5f))
                    } else {
                        drawCircle(color, radius = 4.dp.toPx(), center = Offset(ex, 6.dp.toPx()))
                    }
                }
            }

            // Where the line ends now (once the draw-on gets there).
            points.lastOrNull()?.takeIf { reveal.value >= 1f }?.let { r ->
                val c = Offset(x(r.timestamp.toEpochMilli()), y(r.glucoseMgDl))
                drawCircle(background, radius = 6.dp.toPx(), center = c)
                drawCircle(valueColor(r.glucoseMgDl, Sage), radius = 4.dp.toPx(), center = c)
            }

            selected?.let { r ->
                val sx = x(r.timestamp.toEpochMilli())
                drawLine(onBg.copy(alpha = 0.3f), Offset(sx, 0f), Offset(sx, plotHeight), strokeWidth = 1.dp.toPx())
                drawCircle(valueColor(r.glucoseMgDl, Sage), radius = 5.dp.toPx(), center = Offset(sx, y(r.glucoseMgDl)))
            }
        }
    }
}

@Composable
private fun RangeSelector(selected: GraphRange, onSelect: (GraphRange) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GraphRange.entries.forEach { range ->
            val isSelected = range == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (isSelected) Modifier.background(Sage) else Modifier.border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), RoundedCornerShape(8.dp)))
                    .clickable { onSelect(range) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rangeLabel(range),
                    color = if (isSelected) Color.White else CaptionMuted,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** Time in range for the chosen window: one bar of the five consensus bands, average, and GMI for day ranges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RangeStats(summary: RangeSummary, title: String, showGmi: Boolean) {
    val bands = listOf(
        Triple(String.format(Locale.getDefault(), "<%d", 54), summary.veryLow, PillLowText),
        Triple(String.format(Locale.getDefault(), "%d–%d", 54, 69), summary.low, StateLow),
        Triple(String.format(Locale.getDefault(), "%d–%d", 70, 180), summary.inRange, Sage),
        Triple(String.format(Locale.getDefault(), "%d–%d", 181, 250), summary.high, StateHigh),
        Triple(String.format(Locale.getDefault(), ">%d", 250), summary.veryHigh, PillHighText),
    )
    val yours = TargetRange.high.takeIf { it < TargetRange.DEFAULT_HIGH }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Eyebrow(title)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(percent(summary.inRange), fontFamily = HeadlineSerifFontFamily, fontSize = 32.sp, lineHeight = 32.sp, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.graph_in_range), fontSize = 14.sp, color = CaptionMuted, modifier = Modifier.padding(bottom = 4.dp))
        }
        // A tighter range of your own sits beside the international 70–180, which the bands keep.
        yours?.let { Text(stringResource(R.string.graph_in_your_range, it, percent(summary.inYourRange)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep) }
        Row(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
        ) {
            bands.forEach { (_, share, color) ->
                if (share > 0) Box(Modifier.weight(share.toFloat()).fillMaxHeight().background(color))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            bands.forEach { (label, share, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(4.dp))
                    Text("$label  ${percent(share)}", fontSize = 12.sp, color = CaptionMuted)
                }
            }
        }
        val average = stringResource(R.string.graph_average, summary.meanMgDl)
        if (showGmi) {
            Text("$average · ${stringResource(R.string.graph_gmi, String.format(Locale.getDefault(), "%.1f", summary.gmiPercent))}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.graph_gmi_note), fontSize = 12.sp, lineHeight = 16.sp, color = CaptionMuted)
        } else {
            Text(average, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> stringResource(R.string.graph_today)
        today.minusDays(1) -> stringResource(R.string.graph_yesterday)
        else -> DateTimeFormatter.ofPattern("EEE d MMM").format(day)
    }
    Text(
        label.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.SemiBold,
        color = CaptionMuted,
        // Opaque, so rows scrolling under the pinned header don't show through.
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** One saved reading: time, value, arrow, and the change since the reading before it. */
@Composable
private fun ReadingRow(row: ReadingRowText) {
    val color = valueColor(row.mgDl)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(row.time, fontSize = 14.sp, color = CaptionMuted, modifier = Modifier.width(56.dp))
        Text(row.value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = color)
        Spacer(Modifier.width(8.dp))
        Text(row.arrow, fontSize = 16.sp, color = color)
        Spacer(Modifier.weight(1f))
        if (row.delta != null) Text(row.delta, fontSize = 12.sp, color = CaptionMuted)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
}

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
}

@Composable
private fun rangeLabel(range: GraphRange): String =
    if (range.hours <= 24) stringResource(R.string.graph_range_hours, range.hours) else stringResource(R.string.graph_range_days, range.hours / 24)

@Composable
private fun trendLabel(trend: TrendDirection): String = when (trend) {
    TrendDirection.FALLING_FAST -> stringResource(R.string.home_trend_falling_fast)
    TrendDirection.FALLING -> stringResource(R.string.home_trend_falling)
    TrendDirection.STEADY -> stringResource(R.string.home_trend_steady)
    TrendDirection.RISING, TrendDirection.RISING_FAST -> stringResource(R.string.home_trend_rising)
}

/** Low red, high amber, in range [inRange] (plain text by default). */
@Composable
private fun valueColor(mgDl: Int): Color = valueColor(mgDl, MaterialTheme.colorScheme.onBackground)

private fun valueColor(mgDl: Int, inRange: Color): Color = when {
    mgDl < LOW -> StateLow
    mgDl > HIGH -> StateHigh
    else -> inRange
}

/** "<1%" for a sliver that would round to 0 — time under 54 matters even when small. */
private fun percent(share: Double): String =
    if (share > 0 && share < 0.5) String.format(Locale.getDefault(), "<%d%%", 1) else String.format(Locale.getDefault(), "%d%%", share.roundToInt())

@Preview(showBackground = true)
@Composable
private fun GraphScreenPreview() {
    val now = System.currentTimeMillis()
    val readings = (0 until 36).map { i ->
        GlucoseReading(
            timestamp = Instant.ofEpochMilli(now - (36 - i) * 5 * 60_000L),
            glucoseMgDl = 110 + (Math.sin(i / 4.0) * 80).toInt(),
            trend = TrendDirection.STEADY,
            source = SourceKind.SIMULATED,
        )
    }
    SukoonTheme {
        GraphScreen(state = GraphUiState(GraphRange.H3, readings, summary = InsightEngine.summary(readings, Instant.ofEpochMilli(now))), onSelectRange = {})
    }
}
