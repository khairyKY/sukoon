package com.sukoon.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukoon.app.ui.theme.CanvasDark
import com.sukoon.app.ui.theme.MarkHighlight
import com.sukoon.app.ui.theme.SageLight

/**
 * The Sukoon brand mark: a crescent cut from an overlapping circle pair, plus a small ripple
 * ring — the "still water, one ripple" motif. Matches Sukoon Brand Directions.dc.html (8a/8b),
 * where it appears on a fixed dark badge regardless of app theme, like a logo.
 */
@Composable
fun SukoonMark(modifier: Modifier = Modifier, markSize: Dp = 36.dp) {
    Box(
        modifier = modifier
            .size(markSize)
            .clip(RoundedCornerShape(markSize / 3.6f))
            .background(CanvasDark),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(markSize * (23f / 36f))) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val circleRadius = size.width * (20f / 23f) / 2f
            val strokeWidthPx = markSize.toPx() * (1.2f / 36f)

            drawCircle(color = MarkHighlight, radius = circleRadius, center = center)
            drawCircle(
                color = CanvasDark,
                radius = circleRadius,
                center = center + Offset(size.width * (5f / 23f), -size.height * (4f / 23f)),
            )
            drawCircle(
                color = SageLight,
                radius = size.width * (5f / 23f) / 2f,
                center = center + Offset(size.width * (3.5f / 23f), size.height * (1f / 23f)),
                style = Stroke(width = strokeWidthPx),
            )
        }
    }
}
