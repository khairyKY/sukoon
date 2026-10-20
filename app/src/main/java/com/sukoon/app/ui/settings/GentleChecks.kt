package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.reminders.MorningSummary
import com.sukoon.app.reminders.MorningSummarySettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist

/** You → Alarms → Gentle checks (Redesign Canvas §8): the after-workout check's switch, on by default. */
@Composable
internal fun GentleChecks() {
    val settings = (LocalContext.current.applicationContext as SukoonApp).container.settings
    var on by remember { mutableStateOf(settings.afterWorkoutCheck) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(SageMist), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_walk), null, tint = SageDeep, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.workout_check_row), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.workout_check_row_body), fontSize = 12.5.sp, lineHeight = 17.sp, color = CaptionMuted)
            }
            Switch(
                checked = on,
                onCheckedChange = {
                    on = it
                    settings.afterWorkoutCheck = it
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Sage),
            )
        }
        Text(stringResource(R.string.workout_check_mfp), fontSize = 12.sp, color = CaptionMuted, modifier = Modifier.padding(horizontal = 4.dp))
        MorningSummaryRow()
    }
}

/** The morning summary's switch; turning it on asks for the time (08:00 first). */
@Composable
private fun MorningSummaryRow() {
    val context = LocalContext.current
    val settings = (context.applicationContext as SukoonApp).container.settings
    var summary by remember { mutableStateOf(settings.morningSummary) }
    var picking by remember { mutableStateOf(false) }
    fun set(value: MorningSummarySettings) {
        summary = value
        settings.morningSummary = value
        MorningSummary.schedule(context, value)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(SageMist), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_bell), null, tint = SageDeep, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.morning_setting), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                if (summary.enabled) stringResource(R.string.morning_setting_on, minuteLabel(summary.minuteOfDay)) else stringResource(R.string.morning_setting_off),
                fontSize = 12.5.sp, lineHeight = 17.sp, color = CaptionMuted,
            )
            if (summary.enabled) Text(
                stringResource(R.string.reminder_basal_change, minuteLabel(summary.minuteOfDay)),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picking = true }.heightIn(min = 40.dp).padding(vertical = 10.dp),
            )
        }
        Switch(
            checked = summary.enabled,
            onCheckedChange = { on -> if (on) picking = true else set(summary.copy(enabled = false)) },
            colors = SwitchDefaults.colors(checkedTrackColor = Sage),
        )
    }
    if (picking) TimePickDialog(stringResource(R.string.morning_pick), summary.minuteOfDay, onDismiss = { picking = false }) {
        picking = false
        set(MorningSummarySettings(enabled = true, minuteOfDay = it))
    }
}
