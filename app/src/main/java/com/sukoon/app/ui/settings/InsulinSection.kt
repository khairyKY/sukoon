package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.insulin.InsulinAction
import com.sukoon.app.ui.components.NumberChips
import com.sukoon.app.ui.theme.CaptionMuted

/** You → Insulin: how the rapid insulin works, for "active insulin" on Home and in the Logbook. */
@Composable
fun InsulinSection(action: InsulinAction, onChange: (InsulinAction) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.insulin_body), fontSize = 12.5.sp, color = CaptionMuted)
        Label(stringResource(R.string.insulin_duration))
        NumberChips(listOf(3, 4, 5, 6), action.durationMinutes / 60, 3..8, { stringResource(R.string.life_in_hours, it) }) {
            onChange(action.copy(durationMinutes = it * 60).sanitized())
        }
        Label(stringResource(R.string.insulin_peak))
        NumberChips(listOf(55, 65, 75), action.peakMinutes, InsulinAction.PEAK_RANGE, { stringResource(R.string.life_in_minutes, it) }) {
            onChange(action.copy(peakMinutes = it).sanitized())
        }
        Text(stringResource(R.string.insulin_peak_hint), fontSize = 12.sp, color = CaptionMuted)
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
}
