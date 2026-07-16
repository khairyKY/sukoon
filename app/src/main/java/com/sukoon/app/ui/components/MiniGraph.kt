package com.sukoon.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow

/**
 * The small "last N hours" bar strip shown on Home (screens 5a/8e/8h). Bar height is the
 * glucose value normalized against [maxMgDl]; bar color follows the same TIR brackets the
 * metrics module uses, so this reads the graph the same way the Insights tab will later.
 */
@Composable
fun MiniGraph(
    readingsMgDl: List<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    maxMgDl: Int = 260,
    dimmed: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (dimmed) 0.4f else 1f),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        readingsMgDl.forEach { value ->
            val fraction = (value.toFloat() / maxMgDl).coerceIn(0.08f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(height * fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colorFor(value)),
            )
        }
    }
}

private fun colorFor(mgDl: Int): Color = when {
    mgDl < 70 -> StateLow
    mgDl <= 180 -> Sage
    else -> StateHigh
}
