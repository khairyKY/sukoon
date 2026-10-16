package com.sukoon.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukoon.app.data.source.TrendDirection

/**
 * Sukoon's own trend arrow, in the icons' line style, instead of a text glyph the phone may draw as
 * an emoji. Steady points ahead; rising and falling tilt 45°; fast ones point straight up or down
 * with a second head. Not mirrored right to left: time runs the same way on the graphs.
 */
@Composable
fun TrendArrow(trend: TrendDirection, color: Color, size: Dp = 28.dp, modifier: Modifier = Modifier, label: String? = null) {
    val angle = when (trend) {
        TrendDirection.RISING_FAST -> -90f
        TrendDirection.RISING -> -45f
        TrendDirection.STEADY -> 0f
        TrendDirection.FALLING -> 45f
        TrendDirection.FALLING_FAST -> 90f
    }
    val fast = trend == TrendDirection.RISING_FAST || trend == TrendDirection.FALLING_FAST
    Canvas(modifier.size(size).then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        rotate(angle) {
            val y = w / 2
            drawLine(color, Offset(w * 0.16f, y), Offset(w * 0.84f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
            fun head(tipX: Float) {
                drawPath(Path().apply { moveTo(tipX - w * 0.26f, y - w * 0.26f); lineTo(tipX, y); lineTo(tipX - w * 0.26f, y + w * 0.26f) }, color, style = stroke)
            }
            head(w * 0.84f)
            if (fast) head(w * 0.6f)
        }
    }
}

/**
 * "142" and Sukoon's arrow after it. Laid out left to right in every language: the arrow is a trend
 * on a timeline (as on the graph), so in Arabic too it sits to the right of the number.
 */
@Composable
fun NumberWithTrend(
    text: String,
    trend: TrendDirection?,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        androidx.compose.foundation.layout.Row(modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
            androidx.compose.material3.Text(text, color = color, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily)
            if (trend != null) {
                val arrow = with(androidx.compose.ui.platform.LocalDensity.current) { (fontSize.toPx() * 1.05f).toDp() }
                TrendArrow(trend, color, size = arrow)
            }
        }
    }
}
