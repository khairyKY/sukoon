package com.sukoon.app.ui.settings

import com.sukoon.app.platform.TimeFormat
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.res.painterResource
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import com.sukoon.app.ui.theme.TextMuted
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.sukoon.app.ui.components.ExpandedPanel
import com.sukoon.app.alarms.SoundPack
import androidx.compose.ui.res.pluralStringResource

private val LOW_LEVELS = listOf(60, 65, 70, 75, 80, 90, 100)
private val HIGH_LEVELS = listOf(140, 160, 180, 200, 250, 300)
private val SIGNAL_MINUTES = listOf(15, 20, 30, 60)
private val LOW_SNOOZES = listOf(10, 15, 30)
private val HIGH_SNOOZES = listOf(30, 60, 120)

/** You → Alarms. Urgent low is shown but not editable: it's always on at 55. Every alarm can have its own sound. */
@Composable
fun AlarmSettingsSection(settings: AlarmSettings, onChange: (AlarmSettings) -> Unit, onTest: (AlarmType) -> Unit, onPreview: (AlarmType) -> Unit, onPreviewPack: (SoundPack) -> Unit = {}) {
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
        name = settings.sounds[type]?.name ?: stringResource(settings.soundPack.nameRes),
        custom = settings.sounds[type] != null,
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

    var open by rememberSaveable { mutableStateOf<AlarmType?>(null) }
    var previewing by rememberSaveable { mutableStateOf(false) }
    fun toggleOpen(type: AlarmType) {
        open = if (open == type) null else type
    }
    @Composable
    fun soundName(type: AlarmType) = settings.sounds[type]?.name ?: stringResource(settings.soundPack.nameRes)

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, Sage, RoundedCornerShape(14.dp))
            .clickable { previewing = true },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = SageDeep, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.alarms_see_hear), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
    }
    Spacer(Modifier.height(12.dp))

    ListCard {
        AlarmRow(
            AlarmType.URGENT_LOW,
            stringResource(R.string.alarms_sum_urgent, AlarmSettings.URGENT_LOW_MG_DL, soundName(AlarmType.URGENT_LOW)),
            on = null,
            open = open == AlarmType.URGENT_LOW,
            onOpen = { toggleOpen(AlarmType.URGENT_LOW) },
        ) {
            Text(stringResource(R.string.alarms_urgent_body, AlarmSettings.URGENT_LOW_MG_DL), fontSize = 12.5.sp, color = CaptionMuted)
            Sound(AlarmType.URGENT_LOW)
        }
        AlarmRow(
            AlarmType.LOW,
            stringResource(R.string.alarms_sum_low, settings.lowMgDl, soundName(AlarmType.LOW), snoozeLabel(settings.lowSnoozeMinutes)),
            on = settings.lowEnabled,
            open = open == AlarmType.LOW,
            onOpen = { toggleOpen(AlarmType.LOW) },
            onToggle = { onChange(settings.copy(lowEnabled = it)) },
        ) {
            Label(R.string.alarms_line_low)
            NumberChips(LOW_LEVELS, settings.lowMgDl, 60..110, { stringResource(R.string.alarms_below, it) }, enabled = settings.lowEnabled) {
                onChange(settings.copy(lowMgDl = it))
            }
            Label(R.string.alarms_low_repeat)
            NumberChips(LOW_SNOOZES, settings.lowSnoozeMinutes, 5..60, { pluralStringResource(R.plurals.alarms_minutes, it.toInt(), it) }, enabled = settings.lowEnabled) {
                onChange(settings.copy(lowSnoozeMinutes = it))
            }
            Sound(AlarmType.LOW, settings.lowEnabled)
        }
        AlarmRow(
            AlarmType.GOING_LOW,
            stringResource(R.string.alarms_sum_going_low, soundName(AlarmType.GOING_LOW)),
            on = settings.goingLowEnabled,
            open = open == AlarmType.GOING_LOW,
            onOpen = { toggleOpen(AlarmType.GOING_LOW) },
            onToggle = { onChange(settings.copy(goingLowEnabled = it)) },
        ) {
            Text(stringResource(R.string.alarms_going_low_body), fontSize = 12.5.sp, color = CaptionMuted)
            Sound(AlarmType.GOING_LOW, settings.goingLowEnabled)
        }
        AlarmRow(
            AlarmType.HIGH,
            stringResource(R.string.alarms_sum_high, settings.highMgDl, soundName(AlarmType.HIGH), snoozeLabel(settings.highSnoozeMinutes)),
            on = settings.highEnabled,
            open = open == AlarmType.HIGH,
            onOpen = { toggleOpen(AlarmType.HIGH) },
            onToggle = { onChange(settings.copy(highEnabled = it)) },
        ) {
            Label(R.string.alarms_line_high)
            NumberChips(HIGH_LEVELS, settings.highMgDl, 120..400, { stringResource(R.string.alarms_above, it) }, enabled = settings.highEnabled) {
                onChange(settings.copy(highMgDl = it))
            }
            Label(R.string.alarms_high_repeat)
            NumberChips(HIGH_SNOOZES, settings.highSnoozeMinutes, 15..240, { pluralStringResource(R.plurals.alarms_minutes, it.toInt(), it) }, enabled = settings.highEnabled) {
                onChange(settings.copy(highSnoozeMinutes = it))
            }
            Sound(AlarmType.HIGH, settings.highEnabled)
        }
        AlarmRow(
            AlarmType.SIGNAL_LOSS,
            stringResource(R.string.alarms_sum_signal, settings.signalLossMinutes, soundName(AlarmType.SIGNAL_LOSS)),
            on = settings.signalLossEnabled,
            open = open == AlarmType.SIGNAL_LOSS,
            onOpen = { toggleOpen(AlarmType.SIGNAL_LOSS) },
            onToggle = { onChange(settings.copy(signalLossEnabled = it)) },
            last = true,
        ) {
            Label(R.string.alarms_signal_after)
            NumberChips(SIGNAL_MINUTES, settings.signalLossMinutes, 10..120, { pluralStringResource(R.plurals.alarms_after_minutes, it.toInt(), it) }, enabled = settings.signalLossEnabled) {
                onChange(settings.copy(signalLossMinutes = it))
            }
            Sound(AlarmType.SIGNAL_LOSS, settings.signalLossEnabled)
        }
    }
    Spacer(Modifier.height(12.dp))
    PackCard(settings.soundPack, onPick = { onChange(settings.copy(soundPack = it)) }, onPlay = onPreviewPack)
    Spacer(Modifier.height(12.dp))

    // Quiet highs at night: one row, the hours open under it while it's on.
    val quiet = settings.quietHighsFrom >= 0
    ListCard {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_moon), contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.alarms_quiet), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    if (quiet) stringResource(R.string.alarms_sum_quiet, hourLabel(settings.quietHighsFrom), hourLabel(settings.quietHighsTo)) else stringResource(R.string.alarms_quiet_body),
                    fontSize = 12.5.sp,
                    color = CaptionMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = quiet,
                onCheckedChange = { on -> onChange(if (on) settings.copy(quietHighsFrom = 23, quietHighsTo = 7) else settings.copy(quietHighsFrom = -1, quietHighsTo = -1)) },
                colors = SwitchDefaults.colors(checkedTrackColor = Sage),
            )
        }
        ExpandedPanel(quiet) {
            Label(R.string.alarms_quiet_from)
            NumberChips(listOf(21, 22, 23, 0), settings.quietHighsFrom, 0..23, { hourLabel(it) }) { onChange(settings.copy(quietHighsFrom = it)) }
            Label(R.string.alarms_quiet_to)
            NumberChips(listOf(6, 7, 8, 9), settings.quietHighsTo, 0..23, { hourLabel(it) }) { onChange(settings.copy(quietHighsTo = it)) }
        }
    }

    if (previewing) SeeAndHear(onDismiss = { previewing = false }, onHear = onPreview, onSee = { previewing = false; onTest(it) })
}

/** "15 min", "1 h", "2 h": how long an alarm stays quiet after its snooze. */
@Composable
private fun snoozeLabel(minutes: Int) =
    if (minutes >= 60 && minutes % 60 == 0) stringResource(R.string.alarms_hours, minutes / 60) else pluralStringResource(R.plurals.alarms_minutes, minutes.toInt(), minutes)

@Composable
private fun ListCard(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, outline(), RoundedCornerShape(18.dp)),
    content = content,
)

/** One alarm: its colour, name and settings in a line, its switch (a lock for urgent low); tap to open its settings. */
@Composable
private fun AlarmRow(
    type: AlarmType,
    summary: String,
    on: Boolean?,
    open: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit = {},
    last: Boolean = false,
    details: @Composable ColumnScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlarmDot(type)
        Column(Modifier.weight(1f)) {
            Text(stringResource(alarmLabel(type)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(summary, fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.padding(top = 2.dp))
        }
        // Points down while closed, up while open: this row opens.
        val flip = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
        val turn by animateFloatAsState(if (open) -90f else 90f, label = "chevron")
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = CaptionMuted, modifier = Modifier.size(18.dp).rotate(turn * flip))
        if (on == null) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = stringResource(R.string.alarms_always_on), tint = TextMuted, modifier = Modifier.size(18.dp))
        } else {
            Switch(checked = on, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
        }
    }
    ExpandedPanel(open, content = details)
    if (!last) HorizontalDivider(color = outline().copy(alpha = 0.08f))
}

/** Lows coral (going low as a ring: not there yet), highs amber, no readings grey. */
@Composable
private fun AlarmDot(type: AlarmType) {
    val color = when (type) {
        AlarmType.URGENT_LOW, AlarmType.LOW, AlarmType.GOING_LOW -> StateLow
        AlarmType.HIGH -> StateHigh
        AlarmType.SIGNAL_LOSS -> TextMuted
    }
    val dot = Modifier.size(10.dp).clip(CircleShape)
    Box(if (type == AlarmType.GOING_LOW) dot.border(2.dp, color, CircleShape) else dot.background(color))
}

/** Every alarm in turn: hear its sound for 5 seconds, or see its full alert (marked TEST). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeeAndHear(onDismiss: () -> Unit, onHear: (AlarmType) -> Unit, onSee: (AlarmType) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(stringResource(R.string.alarms_see_hear), fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.alarms_preview_body), fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            ListCard {
                AlarmType.entries.forEachIndexed { i, type ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AlarmDot(type)
                        Text(stringResource(alarmLabel(type)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                        PillButton(stringResource(R.string.alarms_hear), filled = false) { onHear(type) }
                        PillButton(stringResource(R.string.alarms_see), filled = true) { onSee(type) }
                    }
                    if (i < AlarmType.entries.lastIndex) HorizontalDivider(color = outline().copy(alpha = 0.08f))
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(if (filled) Modifier.background(Sage) else Modifier.border(1.dp, outline(), RoundedCornerShape(20.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Color.White else MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * Sound packs: Sukoon's own sounds as sets, one sound for each alarm. Picking one changes every alarm
 * that hasn't been given its own sound; play hears its low alarm at the volume a low plays.
 */
@Composable
private fun PackCard(chosen: SoundPack, onPick: (SoundPack) -> Unit, onPlay: (SoundPack) -> Unit) {
    ListCard {
        Text(stringResource(R.string.pack_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp))
        Text(stringResource(R.string.pack_body), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        SoundPack.entries.forEach { pack ->
            val on = pack == chosen
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable { onPick(pack) }
                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) Sage else CaptionMuted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(Sage)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(pack.nameRes), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(stringResource(pack.blurbRes), fontSize = 12.5.sp, color = CaptionMuted)
                }
                val play = stringResource(R.string.pack_play, stringResource(pack.nameRes))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { onPlay(pack) }.semantics { contentDescription = play },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = SageDeep, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** "Sound · <name>"; tapping opens: phone sounds, your own file, back to the pack, play it. */
@Composable
private fun SoundRow(
    name: String,
    custom: Boolean,
    enabled: Boolean,
    onPhoneSounds: () -> Unit,
    onOwnFile: () -> Unit,
    onDefault: () -> Unit,
    onPlay: () -> Unit,
) {
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
                name,
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
            DropdownMenuItem(text = { Text(stringResource(R.string.alarm_sound_play)) }, onClick = { menu = false; onPlay() })
            DropdownMenuItem(text = { Text(stringResource(R.string.alarm_sound_phone)) }, onClick = { menu = false; onPhoneSounds() })
            DropdownMenuItem(text = { Text(stringResource(R.string.alarm_sound_file)) }, onClick = { menu = false; onOwnFile() })
            if (custom) DropdownMenuItem(text = { Text(stringResource(R.string.alarm_sound_reset)) }, onClick = { menu = false; onDefault() })
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
        NumberChips(SettingsPrefs.SAVE_INTERVALS, intervalMinutes, SettingsPrefs.SAVE_INTERVAL_RANGE, { pluralStringResource(R.plurals.alarms_minutes, it.toInt(), it) }) { onChange(it) }
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

internal val ALARM_SETUP = setOf(SetupItem.NOTIFICATIONS, SetupItem.FULL_SCREEN, SetupItem.OVERLAY, SetupItem.DND, SetupItem.BATTERY)

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
    return TimeFormat.of(if (today) "HH:mm" else "EEE HH:mm").format(at.atZone(zone))
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
private fun Label(textRes: Int) {
    Text(stringResource(textRes).uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
}
