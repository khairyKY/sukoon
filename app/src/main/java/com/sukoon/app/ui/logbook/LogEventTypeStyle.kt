package com.sukoon.app.ui.logbook

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.sukoon.app.R
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
    LogEventType.KETONE -> StateHigh
    LogEventType.WEIGHT -> SageDeep
}

/** The line icon per event type, shared by the timeline chips and the add tiles. */
@DrawableRes
fun iconForLogEventType(type: LogEventType): Int = when (type) {
    LogEventType.CARB -> R.drawable.ic_food
    LogEventType.INSULIN -> R.drawable.ic_insulin
    LogEventType.BASAL -> R.drawable.ic_moon
    LogEventType.FINGERSTICK -> R.drawable.ic_drop
    LogEventType.ACTIVITY -> R.drawable.ic_walk
    LogEventType.NOTE -> R.drawable.ic_note
    LogEventType.KETONE -> R.drawable.ic_drop
    LogEventType.WEIGHT -> R.drawable.ic_scale
}
