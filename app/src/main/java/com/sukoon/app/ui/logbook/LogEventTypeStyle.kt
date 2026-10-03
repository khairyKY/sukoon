package com.sukoon.app.ui.logbook

import androidx.compose.ui.graphics.Color
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.ui.theme.CanvasDark
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.TextMuted
import com.sukoon.app.ui.theme.PillLowText

/** Dot/pin color per event type (design 8k) — shared between the Logbook timeline and the Graph's event pins. */
fun colorForLogEventType(type: LogEventType): Color = when (type) {
    LogEventType.CARB -> Sage
    LogEventType.INSULIN -> CanvasDark
    LogEventType.BASAL -> SageDeep
    LogEventType.FINGERSTICK -> PillLowText
    LogEventType.ACTIVITY -> StateHigh
    LogEventType.NOTE -> TextMuted
}
