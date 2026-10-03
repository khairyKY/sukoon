package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.alarms.AlarmSettings
import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.ui.components.ChoiceChips
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateUrgent

private val LOW_LEVELS = listOf(60, 65, 70, 75, 80, 90, 100)
private val HIGH_LEVELS = listOf(180, 200, 220, 250, 280, 300, 350)
private val SIGNAL_MINUTES = listOf(15, 20, 30, 60)
private val LOW_SNOOZES = listOf(10, 15, 30)
private val HIGH_SNOOZES = listOf(30, 60, 120)

/** You → Alarms. Urgent low is shown but not editable: it's always on at 55. */
@Composable
fun AlarmSettingsSection(settings: AlarmSettings, onChange: (AlarmSettings) -> Unit, onTest: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.alarms_urgent), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = StateUrgent)
                Text(stringResource(R.string.alarms_urgent_body, AlarmSettings.URGENT_LOW_MG_DL), fontSize = 12.sp, color = CaptionMuted)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(StateUrgent)
                    .clickable(onClick = onTest)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.alarms_test), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
            }
        }

        Toggle(R.string.alarms_low, R.string.alarms_low_body, settings.lowEnabled) { onChange(settings.copy(lowEnabled = it)) }
        ChoiceChips(LOW_LEVELS, settings.lowMgDl, { stringResource(R.string.alarms_below, it) }, enabled = settings.lowEnabled) {
            onChange(settings.copy(lowMgDl = it))
        }
        Label(R.string.alarms_low_repeat)
        ChoiceChips(LOW_SNOOZES, settings.lowSnoozeMinutes, { stringResource(R.string.alarms_minutes, it) }, enabled = settings.lowEnabled) {
            onChange(settings.copy(lowSnoozeMinutes = it))
        }

        Toggle(R.string.alarms_going_low, R.string.alarms_going_low_body, settings.goingLowEnabled) { onChange(settings.copy(goingLowEnabled = it)) }

        Toggle(R.string.alarms_high, R.string.alarms_high_body, settings.highEnabled) { onChange(settings.copy(highEnabled = it)) }
        ChoiceChips(HIGH_LEVELS, settings.highMgDl, { stringResource(R.string.alarms_above, it) }, enabled = settings.highEnabled) {
            onChange(settings.copy(highMgDl = it))
        }
        Label(R.string.alarms_high_repeat)
        ChoiceChips(HIGH_SNOOZES, settings.highSnoozeMinutes, { stringResource(R.string.alarms_minutes, it) }, enabled = settings.highEnabled) {
            onChange(settings.copy(highSnoozeMinutes = it))
        }

        Toggle(R.string.alarms_signal, R.string.alarms_signal_body, settings.signalLossEnabled) { onChange(settings.copy(signalLossEnabled = it)) }
        ChoiceChips(SIGNAL_MINUTES, settings.signalLossMinutes, { stringResource(R.string.alarms_after_minutes, it) }, enabled = settings.signalLossEnabled) {
            onChange(settings.copy(signalLossMinutes = it))
        }
    }
}

/** You → Readings: how often a reading is saved. Alarms always see every reading. */
@Composable
fun ReadingsSection(intervalMinutes: Int, onChange: (Int) -> Unit) {
    Card {
        Label(R.string.readings_save_every)
        ChoiceChips(SettingsPrefs.SAVE_INTERVALS, intervalMinutes, { stringResource(R.string.alarms_minutes, it) }) { onChange(it) }
        Text(stringResource(R.string.readings_body), fontSize = 12.sp, color = CaptionMuted)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun Toggle(titleRes: Int, bodyRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(bodyRes), fontSize = 12.sp, color = CaptionMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
    }
}

@Composable
private fun Label(textRes: Int) {
    Text(stringResource(textRes).uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
}
