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
