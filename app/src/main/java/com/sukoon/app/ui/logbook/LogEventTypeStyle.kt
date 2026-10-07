package com.sukoon.app.ui.logbook

import androidx.compose.ui.graphics.Color
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.TextMuted
import com.sukoon.app.ui.theme.PillLowText
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme

/** Dot/pin color per event type (design 8k): the Logbook timeline, the add tiles and the Graph's pins. */
@Composable
fun colorForLogEventType(type: LogEventType): Color = when (type) {
    LogEventType.CARB -> Sage
    LogEventType.INSULIN -> MaterialTheme.colorScheme.onBackground // ink: the canvas colour would vanish in dark mode
    LogEventType.BASAL -> SageDeep
    LogEventType.FINGERSTICK -> PillLowText
    LogEventType.ACTIVITY -> StateHigh
    LogEventType.NOTE -> TextMuted
}
