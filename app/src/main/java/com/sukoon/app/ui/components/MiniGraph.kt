package com.sukoon.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Home's bar strip (screens 5a/8e/8h): one bar per 15 minutes of the last 3 hours. Each bar is the
 * bucket's lowest reading if any was under 70, its highest if any was over 180, else its average,
 * so a short low or high shows instead of being averaged away. A bucket with no readings is a gap.
 */
@Composable
fun MiniGraph(
    readings: List<GlucoseReading>,
    now: Instant,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    dimmed: Boolean = false,
) {
    val bars = homeBars(readings, now)
    val top = maxOf(260, bars.filterNotNull().maxOrNull() ?: 0)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (dimmed) 0.4f else 1f),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { value ->
            if (value == null) {
                Spacer(Modifier.weight(1f))
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(height * (value.toFloat() / top).coerceIn(0.08f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(colorFor(value)),
                )
            }
        }
    }
}

/** [count] buckets of [span] ending at [now], oldest first; null = no readings in that bucket. */
internal fun homeBars(readings: List<GlucoseReading>, now: Instant, count: Int = 12, span: Duration = Duration.ofHours(3)): List<Int?> {
    val step = span.dividedBy(count.toLong())
    val start = now.minus(span)
    return (0 until count).map { i ->
        val from = start.plus(step.multipliedBy(i.toLong()))
        val to = from.plus(step)
        val values = readings.filter { it.timestamp >= from && (it.timestamp < to || (i == count - 1 && it.timestamp <= now)) }.map { it.glucoseMgDl }
        when {
            values.isEmpty() -> null
            values.min() < 70 -> values.min()
            values.max() > 180 -> values.max()
            else -> values.average().roundToInt()
        }
    }
}

private fun colorFor(mgDl: Int): Color = when {
    mgDl < 70 -> StateLow
    mgDl <= 180 -> Sage
    else -> StateHigh
}
