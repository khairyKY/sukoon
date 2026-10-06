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
import com.sukoon.app.ui.components.NumberChips
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateUrgent
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.IntentCompat
import com.sukoon.app.alarms.AlarmNotifier
import com.sukoon.app.alarms.AlarmSound
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.ui.components.toast
import com.sukoon.app.alarms.AlarmLog
import com.sukoon.app.alarms.titleRes
import com.sukoon.app.platform.SetupItem
import com.sukoon.app.ui.theme.PillLowBg
import com.sukoon.app.ui.theme.PillLowText
import androidx.compose.foundation.layout.height
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LOW_LEVELS = listOf(60, 65, 70, 75, 80, 90, 100)
private val HIGH_LEVELS = listOf(180, 200, 220, 250, 280, 300, 350)
private val SIGNAL_MINUTES = listOf(15, 20, 30, 60)
private val LOW_SNOOZES = listOf(10, 15, 30)
private val HIGH_SNOOZES = listOf(30, 60, 120)

/** You → Alarms. Urgent low is shown but not editable: it's always on at 55. Every alarm can have its own sound. */
@Composable
fun AlarmSettingsSection(settings: AlarmSettings, onChange: (AlarmSettings) -> Unit, onTest: () -> Unit, onPreview: (AlarmType) -> Unit) {
    val context = LocalContext.current
    var pickingFor by rememberSaveable { mutableStateOf<AlarmType?>(null) }
    fun setSound(type: AlarmType, sound: AlarmSound?) {
        onChange(settings.copy(sounds = if (sound == null) settings.sounds - type else settings.sounds + (type to sound)))
        context.toast(context.getString(R.string.toast_alarm_sound, context.getString(alarmLabel(type)), sound?.name ?: context.getString(R.string.alarm_sound_default)))
    }
    val phoneSounds = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val type = pickingFor ?: return@rememberLauncherForActivityResult
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        val uri = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        // Silent isn't offered; a null pick or the "Default" row both mean the phone's default.
        if (uri == null || RingtoneManager.isDefault(uri)) {
            setSound(type, null)
        } else {
            val name = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: uri.lastPathSegment.orEmpty()
            setSound(type, AlarmSound(uri.toString(), name))
        }
    }
    val ownFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val type = pickingFor ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        // Keep read access across reboots, and make sure it plays before an alarm depends on it.
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (AlarmNotifier.canPlay(context, uri)) {
            setSound(type, AlarmSound(uri.toString(), displayName(context, uri)))
        } else {
            context.toast(context.getString(R.string.toast_alarm_sound_bad), long = true)
        }
    }

    @Composable
    fun Sound(type: AlarmType, enabled: Boolean = true) = SoundRow(
        name = settings.sounds[type]?.name,
        enabled = enabled,
        onPhoneSounds = {
            pickingFor = type
            val kind = if (type.loud) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
            val default = RingtoneManager.getDefaultUri(kind)
            val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, kind)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, default)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, settings.sounds[type]?.uri?.let(Uri::parse) ?: default)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(alarmLabel(type)))
            runCatching { phoneSounds.launch(picker) }.onFailure { context.toast(context.getString(R.string.toast_no_sound_picker), long = true) }
        },
        onOwnFile = {
            pickingFor = type
            ownFile.launch(arrayOf("audio/*"))
        },
        onDefault = { setSound(type, null) },
        onPlay = { onPreview(type) },
    )

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
        Sound(AlarmType.URGENT_LOW)

        Toggle(R.string.alarms_low, R.string.alarms_low_body, settings.lowEnabled) { onChange(settings.copy(lowEnabled = it)) }
        NumberChips(LOW_LEVELS, settings.lowMgDl, 60..110, { stringResource(R.string.alarms_below, it) }, enabled = settings.lowEnabled) {
            onChange(settings.copy(lowMgDl = it))
        }
        Label(R.string.alarms_low_repeat)
        NumberChips(LOW_SNOOZES, settings.lowSnoozeMinutes, 5..60, { stringResource(R.string.alarms_minutes, it) }, enabled = settings.lowEnabled) {
            onChange(settings.copy(lowSnoozeMinutes = it))
        }
        Sound(AlarmType.LOW, settings.lowEnabled)

        Toggle(R.string.alarms_going_low, R.string.alarms_going_low_body, settings.goingLowEnabled) { onChange(settings.copy(goingLowEnabled = it)) }
        Sound(AlarmType.GOING_LOW, settings.goingLowEnabled)

        Toggle(R.string.alarms_high, R.string.alarms_high_body, settings.highEnabled) { onChange(settings.copy(highEnabled = it)) }
        NumberChips(HIGH_LEVELS, settings.highMgDl, 150..400, { stringResource(R.string.alarms_above, it) }, enabled = settings.highEnabled) {
            onChange(settings.copy(highMgDl = it))
        }
        Label(R.string.alarms_high_repeat)
        NumberChips(HIGH_SNOOZES, settings.highSnoozeMinutes, 15..240, { stringResource(R.string.alarms_minutes, it) }, enabled = settings.highEnabled) {
            onChange(settings.copy(highSnoozeMinutes = it))
        }
        Toggle(R.string.alarms_quiet, R.string.alarms_quiet_body, settings.quietHighsFrom >= 0) { on ->
            onChange(if (on) settings.copy(quietHighsFrom = 22, quietHighsTo = 7) else settings.copy(quietHighsFrom = -1, quietHighsTo = -1))
        }
        if (settings.quietHighsFrom >= 0) {
            Label(R.string.alarms_quiet_from)
            NumberChips(listOf(21, 22, 23, 0), settings.quietHighsFrom, 0..23, { hourLabel(it) }) { onChange(settings.copy(quietHighsFrom = it)) }
            Label(R.string.alarms_quiet_to)
            NumberChips(listOf(6, 7, 8, 9), settings.quietHighsTo, 0..23, { hourLabel(it) }) { onChange(settings.copy(quietHighsTo = it)) }
        }
        Sound(AlarmType.HIGH, settings.highEnabled)

        Toggle(R.string.alarms_signal, R.string.alarms_signal_body, settings.signalLossEnabled) { onChange(settings.copy(signalLossEnabled = it)) }
        NumberChips(SIGNAL_MINUTES, settings.signalLossMinutes, 10..120, { stringResource(R.string.alarms_after_minutes, it) }, enabled = settings.signalLossEnabled) {
            onChange(settings.copy(signalLossMinutes = it))
        }
        Sound(AlarmType.SIGNAL_LOSS, settings.signalLossEnabled)
    }
}

/** "Sound · <name>"; tapping opens: phone sounds, your own file, back to default, play it. */
@Composable
private fun SoundRow(name: String?, enabled: Boolean, onPhoneSounds: () -> Unit, onOwnFile: () -> Unit, onDefault: () -> Unit, onPlay: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = enabled) { menu = true }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.alarm_sound), fontSize = 12.5.sp, color = CaptionMuted)
            Spacer(Modifier.width(12.dp))
            Text(
                name ?: stringResource(R.string.alarm_sound_default),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Sage else CaptionMuted,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.alarm_sound_phone)) }, onClick = { menu = false; onPhoneSounds() })
            DropdownMenuItem(text = { Text(stringResource(R.string.alarm_sound_file)) }, onClick = { menu = false; onOwnFile() })
            if (name != null) DropdownMenuItem(text = { Text(stringResource(R.string.alarm_sound_reset)) }, onClick = { menu = false; onDefault() })
            DropdownMenuItem(text = { Text(stringResource(R.string.alarm_sound_play)) }, onClick = { menu = false; onPlay() })
        }
    }
}

private fun hourLabel(hour: Int) = String.format(java.util.Locale.getDefault(), "%02d:00", hour)

private fun alarmLabel(type: AlarmType) = when (type) {
    AlarmType.URGENT_LOW -> R.string.alarms_urgent
    AlarmType.LOW -> R.string.alarms_low
    AlarmType.GOING_LOW -> R.string.alarms_going_low
    AlarmType.HIGH -> R.string.alarms_high
    AlarmType.SIGNAL_LOSS -> R.string.alarms_signal
}

/** The picked file's name without its extension, for showing under the alarm. */
private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()?.substringBeforeLast('.') ?: uri.lastPathSegment.orEmpty()

/** You → Readings: how often a reading is saved. Alarms always see every reading. */
@Composable
fun ReadingsSection(intervalMinutes: Int, onChange: (Int) -> Unit) {
    Card {
        Label(R.string.readings_save_every)
        NumberChips(SettingsPrefs.SAVE_INTERVALS, intervalMinutes, SettingsPrefs.SAVE_INTERVAL_RANGE, { stringResource(R.string.alarms_minutes, it) }) { onChange(it) }
        Text(stringResource(R.string.readings_body), fontSize = 12.sp, color = CaptionMuted)
    }
}

/** Whether an alarm would get through right now: the sensor is the source, and the phone lets it sound and show. */
@Composable
fun AlarmReach(sensorIsSource: Boolean) {
    if (!sensorIsSource) {
        Text(
            stringResource(R.string.alarms_off_demo),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = PillLowText,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PillLowBg).padding(14.dp),
        )
        Spacer(Modifier.height(10.dp))
    }
    SetupChecklist(only = ALARM_SETUP)
}

private val ALARM_SETUP = setOf(SetupItem.NOTIFICATIONS, SetupItem.FULL_SCREEN, SetupItem.OVERLAY, SetupItem.DND, SetupItem.BATTERY)

/** The last alarms, newest first: each one that went off (and what got through), each answer, each ending. */
@Composable
fun AlarmHistory(entries: List<AlarmLog.Entry>) = Card {
    if (entries.isEmpty()) {
        Text(stringResource(R.string.alarm_log_empty), fontSize = 13.sp, color = CaptionMuted)
    } else {
        entries.asReversed().take(20).forEach { LogRow(it) }
    }
}

@Composable
private fun LogRow(e: AlarmLog.Entry) {
    val name = listOfNotNull(
        if (e.test) stringResource(R.string.alarm_test_prefix) else null,
        e.who?.let { "$it ·" },
        stringResource(e.type.titleRes),
    ).joinToString(" ")
    val missed = listOfNotNull(
        if (!e.sounded) stringResource(R.string.alarm_log_no_sound) else null,
        if (!e.screen) stringResource(R.string.alarm_log_no_screen) else null,
        if (!e.posted) stringResource(R.string.alarm_log_no_notification) else null,
    )
    val what = when (e.kind) {
        AlarmLog.Kind.FIRED -> name + (e.mgDl?.let { " · $it" } ?: "")
        AlarmLog.Kind.TREATED -> stringResource(R.string.alarm_log_treated, name)
        AlarmLog.Kind.SNOOZED -> stringResource(R.string.alarm_log_snoozed, name, e.minutes)
        AlarmLog.Kind.DISMISSED -> stringResource(R.string.alarm_log_dismissed, name)
        AlarmLog.Kind.RESOLVED -> stringResource(R.string.alarm_log_resolved, name)
    }
    Row(verticalAlignment = Alignment.Top) {
        Text(logTime(e.at), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.width(76.dp))
        Column(Modifier.weight(1f)) {
            Text(
                what,
                fontSize = 13.5.sp,
                fontWeight = if (e.kind == AlarmLog.Kind.FIRED) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (e.kind == AlarmLog.Kind.FIRED) {
                Text(
                    missed.joinToString(" · ").ifEmpty { stringResource(R.string.alarm_log_reached) },
                    fontSize = 12.sp,
                    color = if (missed.isEmpty()) CaptionMuted else PillLowText,
                )
            }
        }
    }
}

private fun logTime(at: Instant): String {
    val zone = ZoneId.systemDefault()
    val today = at.atZone(zone).toLocalDate() == LocalDate.now(zone)
    return DateTimeFormatter.ofPattern(if (today) "HH:mm" else "EEE HH:mm", Locale.getDefault()).format(at.atZone(zone))
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
