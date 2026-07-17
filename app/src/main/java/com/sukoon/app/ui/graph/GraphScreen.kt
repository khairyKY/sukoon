package com.sukoon.app.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import com.sukoon.app.ui.theme.SukoonTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// Fixed y-axis span for the chart, mg/dL. In-range band is 70–180 (matches GlucoseMetrics).
private const val Y_MIN = 40f
private const val Y_MAX = 400f
private const val LOW = 70f
private const val HIGH = 180f

private val hmFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/**
 * The full graph screen (design 8j) — an interactive, time-ranged glucose chart drawn directly
 * with Canvas (no chart library; one chart doesn't justify the dependency). Reads from the Room
 * store via [GraphViewModel]. Currently the Trends tab's content until Insights/Logbook land.
 */
@Composable
fun GraphScreen(
    state: GraphUiState,
    onSelectRange: (GraphRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.graph_title),
            fontFamily = HeadlineSerifFontFamily,
            fontSize = 26.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.readings.isEmpty()) {
                Text(
                    text = stringResource(R.string.graph_empty),
                    color = CaptionMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                GlucoseChart(state.readings, state.range)
            }
        }

        Spacer(Modifier.height(14.dp))
        RangeSelector(selected = state.range, onSelect = onSelectRange)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GlucoseChart(readings: List<GlucoseReading>, range: GraphRange) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val bandColor = Sage.copy(alpha = 0.12f)
    val gridColor = onBg.copy(alpha = 0.15f)
    val lineColor = Sage

    val tEnd = readings.last().timestamp.toEpochMilli()
    val tStart = tEnd - range.millis
    val span = (tEnd - tStart).coerceAtLeast(1L)

    // Reset the inspected point only when the range changes — NOT on every new reading, or a
    // live append (~every 2s) would clear the user's selection. Indices stay valid because
    // readings only append within a fixed window.
    var selected by remember(range) { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Readout above the chart; shows the tapped point (design's tap-to-inspect).
        val sel = selected?.let(readings::getOrNull)
        val mgdlUnit = stringResource(R.string.home_unit_mgdl)
        val tapHint = stringResource(R.string.graph_tap_hint)
        Text(
            text = if (sel != null) {
                "${sel.glucoseMgDl} $mgdlUnit · ${hmFormatter.format(sel.timestamp)}"
            } else {
                tapHint
            },
            color = if (sel != null) MaterialTheme.colorScheme.onBackground else CaptionMuted,
            fontWeight = if (sel != null) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(readings, range) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        val t = tStart + (frac * span).toLong()
                        selected = readings.indices.minByOrNull {
                            abs(readings[it].timestamp.toEpochMilli() - t)
                        }
                    }
                },
        ) {
            fun xFor(millis: Long) = ((millis - tStart).toFloat() / span) * size.width
            fun yFor(mgDl: Int) =
                size.height * (1f - (mgDl.coerceIn(Y_MIN.toInt(), Y_MAX.toInt()) - Y_MIN) / (Y_MAX - Y_MIN))

            // In-range target band.
            drawRect(
                color = bandColor,
                topLeft = Offset(0f, yFor(HIGH.toInt())),
                size = Size(size.width, yFor(LOW.toInt()) - yFor(HIGH.toInt())),
            )
            // Dashed low/high threshold lines.
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            listOf(LOW, HIGH).forEach { level ->
                drawLine(
                    color = gridColor,
                    start = Offset(0f, yFor(level.toInt())),
                    end = Offset(size.width, yFor(level.toInt())),
                    strokeWidth = 1f,
                    pathEffect = dash,
                )
            }

            // Glucose polyline.
            if (readings.size >= 2) {
                val path = Path().apply {
                    readings.forEachIndexed { i, r ->
                        val x = xFor(r.timestamp.toEpochMilli())
                        val y = yFor(r.glucoseMgDl)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(path, color = lineColor, style = Stroke(width = 3f))
            }

            // Selected-point highlight.
            selected?.let(readings::getOrNull)?.let { r ->
                val x = xFor(r.timestamp.toEpochMilli())
                val y = yFor(r.glucoseMgDl)
                drawLine(onBg.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                drawCircle(colorForRange(r.glucoseMgDl), radius = 6f, center = Offset(x, y))
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(hmFormatter.format(Instant.ofEpochMilli(tStart)), color = CaptionMuted, fontSize = 10.sp)
            Text(hmFormatter.format(Instant.ofEpochMilli(tEnd)), color = CaptionMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun RangeSelector(selected: GraphRange, onSelect: (GraphRange) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                    text = stringResource(R.string.graph_range_hours, range.hours),
                    color = if (isSelected) Color.White else CaptionMuted,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun colorForRange(mgDl: Int): Color = when {
    mgDl < LOW -> StateLow
    mgDl <= HIGH -> Sage
    else -> StateHigh
}

@Preview(showBackground = true)
@Composable
private fun GraphScreenPreview() {
    val now = System.currentTimeMillis()
    val readings = (0 until 36).map { i ->
        GlucoseReading(
            timestamp = Instant.ofEpochMilli(now - (36 - i) * 5 * 60_000L),
            glucoseMgDl = 110 + (Math.sin(i / 4.0) * 60).toInt(),
            trend = TrendDirection.STEADY,
            source = SourceKind.SIMULATED,
        )
    }
    SukoonTheme {
        GraphScreen(state = GraphUiState(GraphRange.H3, readings), onSelectRange = {})
    }
}
