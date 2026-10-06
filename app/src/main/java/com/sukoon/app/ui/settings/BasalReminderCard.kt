package com.sukoon.app.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.reminders.BasalReminderSettings
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import java.util.Locale

/** "22:30" for a minute of the day. */
fun minuteLabel(minuteOfDay: Int): String = String.format(Locale.getDefault(), "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

/**
 * You → Insulin: the long-acting reminder. Turning it on asks for the time, offering 30 minutes
 * after when you usually log it (from the logbook), or 22:00.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasalReminderCard(settings: BasalReminderSettings, onChange: (BasalReminderSettings) -> Unit, usualMinute: suspend () -> Int?) {
    var picking by remember { mutableStateOf(false) }
    var usual by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { usual = runCatching { usualMinute() }.getOrNull() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reminder_basal_setting), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    if (settings.enabled) stringResource(R.string.reminder_basal_on, minuteLabel(settings.minuteOfDay)) else stringResource(R.string.reminder_basal_off),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = CaptionMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { on -> if (on) picking = true else onChange(settings.copy(enabled = false)) },
                colors = SwitchDefaults.colors(checkedTrackColor = Sage),
            )
        }
        if (settings.enabled) {
            Text(
                stringResource(R.string.reminder_basal_change, minuteLabel(settings.minuteOfDay)),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = SageDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picking = true }.heightIn(min = 40.dp).padding(vertical = 10.dp),
            )
        }
        usual?.let { Text(stringResource(R.string.reminder_basal_usual, minuteLabel(it)), fontSize = 12.sp, color = CaptionMuted) }
    }

    if (picking) {
        val start = if (settings.enabled) settings.minuteOfDay else usual?.let { (it + 30) % 1440 } ?: (22 * 60)
        val state = rememberTimePickerState(start / 60, start % 60, is24Hour = DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.reminder_basal_pick)) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    onChange(BasalReminderSettings(enabled = true, minuteOfDay = state.hour * 60 + state.minute))
                }) { Text(stringResource(R.string.reminder_basal_set)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.sensor_cancel)) } },
        )
    }
}
