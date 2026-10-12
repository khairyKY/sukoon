package com.sukoon.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A Libre 2 seen from above, in line (design "Sensor"): the disc, its bevel and the filament port, and
 * the dotted adhesive ring around it — left off when something else rings it, like the warm-up progress.
 * [disc] is the disc's radius in px.
 */
fun DrawScope.drawSensor(center: Offset, disc: Float, ink: Color, adhesive: Boolean = true) {
    if (adhesive) {
        drawCircle(
            ink.copy(alpha = 0.55f),
            disc / 0.86f,
            center,
            style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2.dp.toPx(), 3.2.dp.toPx()))),
        )
    }
    drawCircle(ink, disc, center, style = Stroke(2.dp.toPx()))
    drawCircle(ink.copy(alpha = 0.4f), disc * 0.907f, center, style = Stroke(1.dp.toPx()))
    drawCircle(ink, disc * 0.128f, center, style = Stroke(1.8.dp.toPx()))
    val arm = disc * 0.07f
    drawLine(ink, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), 1.6.dp.toPx(), StrokeCap.Round)
    drawLine(ink, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), 1.6.dp.toPx(), StrokeCap.Round)
}
