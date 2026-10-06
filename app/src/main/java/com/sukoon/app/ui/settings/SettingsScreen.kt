package com.sukoon.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.BuildConfig
import com.sukoon.app.R
import com.sukoon.app.alarms.AlarmSettings
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.calibration.CalibrationManager
import com.sukoon.app.data.export.ConnectionTest
import com.sukoon.app.data.export.NightscoutConfig
import com.sukoon.app.data.export.UploadStatus
import com.sukoon.app.data.prefs.ThemeMode
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.SourceStatus
import com.sukoon.app.data.source.libre.LibreNfc
import com.sukoon.app.data.source.libre.SensorLife
import com.sukoon.app.data.source.libre.SensorLifecycle
import com.sukoon.app.data.source.libre.SensorPairing
import com.sukoon.app.emergency.EmergencyAlerts
import com.sukoon.app.emergency.EmergencySettings
import com.sukoon.app.health.HealthConnectSync
import com.sukoon.app.insulin.InsulinAction
import com.sukoon.app.sharing.FollowerWatch
import com.sukoon.app.sharing.Sharing
import com.sukoon.app.ui.components.ChoiceChips
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.sharing.SharingSection
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowBg
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import com.sukoon.app.ui.widget.WidgetsCard
import java.time.Duration
import java.time.Instant
import java.util.Locale
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import com.sukoon.app.ui.theme.CanvasDark
import com.sukoon.app.ui.theme.CanvasLight
import com.sukoon.app.ui.theme.OnCanvasDark
import com.sukoon.app.ui.theme.OnCanvasLight
import com.sukoon.app.ui.theme.SurfaceDark
import com.sukoon.app.ui.theme.SurfaceLight
import com.sukoon.app.ui.theme.SageLight
import com.sukoon.app.alarms.AlarmLog
import android.net.Uri
import androidx.compose.material3.HorizontalDivider

/** The You tab's sections (design "You, divided"): each opens its own page from the hub. */
private enum class YouPage(@StringRes val title: Int, @DrawableRes val icon: Int) {
    SENSOR(R.string.you_sensor, R.drawable.ic_sensor),
    ALARMS(R.string.you_alarms, R.drawable.ic_bell),
    PEOPLE(R.string.you_people, R.drawable.ic_people),
    INSULIN(R.string.you_insulin, R.drawable.ic_insulin),
    APPS(R.string.you_apps, R.drawable.ic_link),
    REPORTS(R.string.you_reports, R.drawable.ic_doc),
    APPEARANCE(R.string.you_appearance, R.drawable.ic_contrast),
    HELP(R.string.you_help, R.drawable.ic_help),
}

/**
 * You tab: a hub (who you are, anything that still needs you, and eight sections, each with what
 * it's set to now) and a page per section. The sections themselves are the same cards as before.
 */
@Composable
fun SettingsScreen(
    sourceKind: SourceKind,
    status: SourceStatus,
    pairing: SensorPairing?,
    geminiKey: String,
    onSelectSource: (SourceKind) -> Unit,
    onPaired: (LibreNfc.SensorRead, Long) -> Unit,
    onForgetSensor: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    alarmSettings: AlarmSettings,
    onAlarmSettings: (AlarmSettings) -> Unit,
    onTestAlarm: (AlarmType) -> Unit,
    onPreviewAlarm: (AlarmType) -> Unit,
    emergency: EmergencySettings,
    emergencyAlerts: EmergencyAlerts,
    onEmergency: (EmergencySettings) -> Unit,
    sharing: Sharing,
    followerWatch: FollowerWatch,
    healthConnect: HealthConnectSync,
    calibration: CalibrationManager,
    onOpenGuide: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    insulinAction: InsulinAction,
    onInsulinAction: (InsulinAction) -> Unit,
    saveIntervalMinutes: Int,
    onSaveInterval: (Int) -> Unit,
    nightscout: NightscoutConfig,
    nightscoutStatus: UploadStatus,
    onNightscout: suspend (NightscoutConfig) -> ConnectionTest?,
    onUploadNow: suspend () -> UploadStatus,
    buildCsv: suspend (Int) -> Pair<String, Int>,
    modifier: Modifier = Modifier,
    alarmLog: List<AlarmLog.Entry> = emptyList(),
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf<YouPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    val missing = rememberMissingSetup()

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        when (val open = page) {
            null -> {
                Text(stringResource(R.string.home_nav_you), fontFamily = HeadlineSerifFontFamily, fontSize = 30.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 4.dp))
                Spacer(Modifier.height(14.dp))
                ProfileCard(emergency.yourName, wears = pairing != null) { page = YouPage.PEOPLE }
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    NeedsYou(missing.size, stringResource(missing.first().titleRes)) { page = YouPage.HELP }
                }
                Spacer(Modifier.height(12.dp))
                val summaries = mapOf(
                    YouPage.SENSOR to sensorSummary(sourceKind, status, pairing),
                    YouPage.ALARMS to alarmsSummary(alarmSettings),
                    YouPage.PEOPLE to emergency.contacts.take(2).joinToString { it.name }.ifBlank { stringResource(R.string.you_people_none) },
                    YouPage.INSULIN to stringResource(R.string.you_insulin_summary, insulinAction.peakMinutes, formatHours(insulinAction.durationMinutes)),
                    YouPage.APPS to stringResource(R.string.you_apps_summary),
                    YouPage.REPORTS to stringResource(R.string.you_reports_summary),
                    YouPage.APPEARANCE to "${themeLabel(themeMode)} · ${languageLabel()}",
                    YouPage.HELP to stringResource(R.string.you_help_summary),
                )
                YouPage.entries.chunked(2).forEach { row ->
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { p -> SectionCard(p, summaries.getValue(p), Modifier.weight(1f)) { page = p } }
                    }
                }
            }
            else -> {
                PageHeader(stringResource(open.title)) { page = null }
                Spacer(Modifier.height(16.dp))
                when (open) {
                    YouPage.SENSOR -> {
                        SensorCard(
                            pairing = pairing,
                            sensorSelected = sourceKind == SourceKind.LIBRE_BLE,
                            status = status,
                            onPaired = onPaired,
                            onForget = {
                                onForgetSensor()
                                context.toast(context.getString(R.string.toast_sensor_forgotten))
                            },
                        )
                        Gap()
                        SectionLabel(stringResource(R.string.settings_source_title))
                        if (pairing != null) {
                            SourceOption(SourceKind.LIBRE_BLE, sourceKind, R.string.settings_source_sensor, R.string.settings_source_sensor_body) {
                                onSelectSource(SourceKind.LIBRE_BLE)
                                context.toast(context.getString(R.string.toast_source_sensor))
                            }
                        }
                        SourceOption(SourceKind.SIMULATED, sourceKind, R.string.settings_source_demo, R.string.settings_source_demo_body) {
                            onSelectSource(SourceKind.SIMULATED)
                            context.toast(context.getString(R.string.toast_source_demo))
                        }
                        Gap()
                        SectionLabel(stringResource(R.string.readings_title))
                        ReadingsSection(saveIntervalMinutes, onSaveInterval)
                        Gap()
                        SectionLabel(stringResource(R.string.calibration_title))
                        CalibrationSection(calibration)
                    }
                    YouPage.ALARMS -> {
                        // Whether alarms can reach you goes first while something stops them, else after the alarms (design "You · Alarms").
                        val blocked = sourceKind != SourceKind.LIBRE_BLE || missing.any { it in ALARM_SETUP }
                        if (blocked) {
                            SectionLabel(stringResource(R.string.alarms_reach_title))
                            AlarmReach(sensorIsSource = sourceKind == SourceKind.LIBRE_BLE)
                            Gap()
                        }
                        AlarmSettingsSection(alarmSettings, onAlarmSettings, onTestAlarm, onPreviewAlarm)
                        Gap()
                        if (!blocked) {
                            SectionLabel(stringResource(R.string.alarms_reach_title))
                            AlarmReach(sensorIsSource = true)
                            Gap()
                        }
                        SectionLabel(stringResource(R.string.alarms_history_title))
                        AlarmHistory(alarmLog)
                    }
                    YouPage.PEOPLE -> {
                        SectionLabel(stringResource(R.string.emergency_title))
                        EmergencySection(emergency, emergencyAlerts, onEmergency)
                        Gap()
                        SectionLabel(stringResource(R.string.sharing_title))
                        SharingSection(sharing, followerWatch)
                    }
                    YouPage.INSULIN -> InsulinSection(insulinAction, onInsulinAction)
                    YouPage.APPS -> {
                        HealthConnectSection(healthConnect)
                        Spacer(Modifier.height(10.dp))
                        var service by rememberSaveable { mutableStateOf<String?>(null) }
                        val nsOn = nightscout.enabled && nightscout.url.isNotBlank()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, outline(), RoundedCornerShape(18.dp))
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            ServiceRow(
                                stringResource(R.string.ns_title),
                                if (nsOn) Uri.parse(nightscout.url).host ?: nightscout.url else stringResource(R.string.apps_not_set_up),
                                stringResource(if (nsOn) R.string.apps_change else R.string.apps_set_up),
                                prominent = !nsOn,
                            ) { service = if (service == "ns") null else "ns" }
                            HorizontalDivider(color = outline().copy(alpha = 0.08f))
                            ServiceRow(
                                stringResource(R.string.apps_ai),
                                if (geminiKey.isNotBlank()) stringResource(R.string.apps_ai_key_ending, geminiKey.takeLast(4)) else stringResource(R.string.apps_not_set_up),
                                stringResource(if (geminiKey.isNotBlank()) R.string.apps_change else R.string.apps_set_up),
                                prominent = geminiKey.isBlank(),
                            ) { service = if (service == "ai") null else "ai" }
                        }
                        when (service) {
                            "ns" -> {
                                Gap()
                                NightscoutSection(nightscout, nightscoutStatus, onNightscout, onUploadNow)
                            }
                            "ai" -> {
                                Gap()
                                AiKey(geminiKey, onSaveGeminiKey)
                            }
                        }
                    }
                    YouPage.REPORTS -> {
                        Text(stringResource(R.string.you_reports_body), fontSize = 13.sp, color = CaptionMuted)
                        Gap()
                        SectionLabel(stringResource(R.string.export_title))
                        ExportSection(buildCsv)
                    }
                    YouPage.APPEARANCE -> {
                        SectionLabel(stringResource(R.string.you_theme))
                        ThemeCards(themeMode, onThemeMode)
                        Gap()
                        SectionLabel(stringResource(R.string.you_language))
                        LanguageChoice()
                        Gap()
                        SectionLabel(stringResource(R.string.widgets_title))
                        WidgetsCard()
                    }
                    YouPage.HELP -> {
                        Text(
                            stringResource(R.string.settings_guide),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenGuide).padding(vertical = 10.dp),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SageDeep,
                        )
                        Gap()
                        SectionLabel(stringResource(R.string.setup_title))
                        SetupChecklist()
                        Gap()
                        SectionLabel(stringResource(R.string.settings_about_title))
                        Text(stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME), fontSize = 12.5.sp, color = CaptionMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(name: String, wears: Boolean, onClick: () -> Unit) {
    val shown = name.trim().ifBlank { stringResource(R.string.home_nav_you) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(SageMist), contentAlignment = Alignment.Center) {
            Text(shown.take(1).uppercase(), fontFamily = HeadlineSerifFontFamily, fontSize = 22.sp, color = SageDeep)
        }
        Column(Modifier.weight(1f)) {
            Text(shown, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            if (wears) {
                Text(
                    stringResource(R.string.you_wears),
                    modifier = Modifier.padding(top = 5.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = CaptionMuted, modifier = Modifier.size(18.dp))
    }
}

/** "1 thing needs you: full-screen alarms" — opens Help, where the setup checklist fixes it. */
@Composable
private fun NeedsYou(count: Int, first: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PillHighBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (count == 1) stringResource(R.string.you_needs_one, first.lowercase(Locale.getDefault())) else stringResource(R.string.you_needs_many, count),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PillHighText,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.you_fix), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PillHighText)
    }
}

@Composable
private fun SectionCard(page: YouPage, summary: String, modifier: Modifier, onClick: () -> Unit) {
    val (tileBg, tint) = when (page) {
        YouPage.SENSOR -> SageMist to SageDeep
        YouPage.ALARMS -> PillLowBg to PillLowText
        YouPage.PEOPLE -> SageMist to SageDeep
        YouPage.APPS -> PillHighBg to PillHighText
        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f) to MaterialTheme.colorScheme.onBackground
    }
    Column(
        modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tileBg), contentAlignment = Alignment.Center) {
            Icon(painterResource(page.icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(top = 10.dp)) {
            Text(stringResource(page.title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            Text(summary, fontSize = 12.5.sp, color = CaptionMuted, maxLines = 1)
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.you_back), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
        }
        Text(title, fontFamily = HeadlineSerifFontFamily, fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** "Day 7 of 15 · live", "Warming up", "Ended", "Demo data", "Not connected". */
@Composable
private fun sensorSummary(sourceKind: SourceKind, status: SourceStatus, pairing: SensorPairing?): String {
    if (sourceKind == SourceKind.SIMULATED) return stringResource(R.string.you_sensor_demo)
    if (pairing == null) return stringResource(R.string.you_sensor_none)
    val now = Instant.now()
    return when (SensorLifecycle.of(pairing.startMillis, pairing.lifetimeMinutes, now)) {
        is SensorLife.WarmingUp -> stringResource(R.string.you_sensor_warming)
        is SensorLife.Ended -> stringResource(R.string.you_sensor_ended)
        is SensorLife.Running -> stringResource(
            if (status == SourceStatus.Connected) R.string.you_sensor_day_live else R.string.you_sensor_day,
            (Duration.between(Instant.ofEpochMilli(pairing.startMillis), now).toDays() + 1).toInt(),
            pairing.lifetimeMinutes / 1440,
        )
    }
}

/** "5 on · quiet nights". */
@Composable
private fun alarmsSummary(s: AlarmSettings): String {
    val on = 1 + listOf(s.lowEnabled, s.goingLowEnabled, s.highEnabled, s.signalLossEnabled).count { it } // urgent low is always on
    val quiet = s.quietHighsFrom in 0..23 && s.quietHighsTo in 0..23 && s.quietHighsFrom != s.quietHighsTo
    return stringResource(if (quiet) R.string.you_alarms_on_quiet else R.string.you_alarms_on, on)
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.AUTO -> R.string.theme_auto
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

/** The app's language as chosen in Appearance ("English", "العربية", or "Like the phone"). */
@Composable
private fun languageLabel(): String {
    if (Build.VERSION.SDK_INT < 33) return stringResource(R.string.theme_auto)
    val chosen = LocalContext.current.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language
    return when (chosen) {
        "en" -> "English"
        "ar" -> "العربية"
        else -> stringResource(R.string.theme_auto)
    }
}

private fun formatHours(minutes: Int): String =
    if (minutes % 60 == 0) String.format(Locale.getDefault(), "%d", minutes / 60) else String.format(Locale.getDefault(), "%.1f", minutes / 60.0)

/** A service in one line: its name, how it's set now, and the button that opens its settings under the list. */
@Composable
private fun ServiceRow(title: String, summary: String, action: String, prominent: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(summary, fontSize = 12.5.sp, color = CaptionMuted, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(if (prominent) 1.5.dp else 1.dp, if (prominent) Sage else outline(), RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(action, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (prominent) com.sukoon.app.ui.theme.SageDeep else MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun AiKey(geminiKey: String, onSave: (String) -> Unit) {
    val context = LocalContext.current
    var keyInput by rememberSaveable(geminiKey) { mutableStateOf(geminiKey) }
    Text(stringResource(R.string.settings_ai_body), fontSize = 12.5.sp, color = CaptionMuted)
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it },
        label = { Text(stringResource(R.string.settings_ai_key)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    PrimaryButton(stringResource(if (geminiKey.isBlank()) R.string.settings_save else R.string.settings_update)) {
        onSave(keyInput)
        context.toast(context.getString(if (keyInput.isBlank()) R.string.toast_ai_key_removed else R.string.settings_ai_saved))
    }
    if (geminiKey.isNotBlank()) Text(stringResource(R.string.settings_ai_saved), fontSize = 11.5.sp, color = Sage, modifier = Modifier.padding(top = 6.dp))
}

/** Like the phone / light / dark, each drawn as a tiny screen (design "You · Appearance"). */
@Composable
private fun ThemeCards(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeMode.entries.forEach { mode ->
            val on = mode == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(if (on) 2.dp else 1.dp, if (on) Sage else outline(), RoundedCornerShape(16.dp))
                    .clickable { onSelect(mode) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(10.dp))) {
                    if (mode != ThemeMode.DARK) MiniScreen(dark = false, Modifier.weight(1f))
                    if (mode != ThemeMode.LIGHT) MiniScreen(dark = true, Modifier.weight(1f))
                }
                Text(themeLabel(mode), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun MiniScreen(dark: Boolean, modifier: Modifier) {
    Column(modifier.fillMaxSize().background(if (dark) CanvasDark else CanvasLight).padding(horizontal = 6.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (dark) OnCanvasDark else OnCanvasLight))
        Box(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(6.dp)).background(if (dark) SurfaceDark else SurfaceLight))
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(5.dp)).background(if (dark) SageLight else Sage))
    }
}

/**
 * The app's language, whatever the phone's (Android 13+ per-app language; it re-draws at once).
 * Older phones follow the phone's language.
 */
@Composable
private fun LanguageChoice() {
    if (Build.VERSION.SDK_INT < 33) {
        Text(stringResource(R.string.you_language_old), fontSize = 13.sp, color = CaptionMuted)
        return
    }
    val context = LocalContext.current
    val manager = context.getSystemService(LocaleManager::class.java)
    val current = manager.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp)),
    ) {
        listOf(
            null to stringResource(R.string.theme_auto),
            "en" to "English",
            "ar" to "العربية المصرية",
        ).forEach { (tag, label) ->
            val on = tag == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(enabled = !on) { manager.applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    if (tag == null) Text(stringResource(R.string.you_language_phone), fontSize = 12.5.sp, color = CaptionMuted)
                }
                Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) Sage else CaptionMuted.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.size(9.dp).clip(CircleShape).background(Sage))
                }
            }
        }
    }
    Text(stringResource(R.string.you_language_note), fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp))
}

@Composable
private fun Gap() = Spacer(Modifier.height(28.dp))

@Composable
private fun outline(): Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SourceOption(kind: SourceKind, selected: SourceKind, titleRes: Int, bodyRes: Int, onSelect: () -> Unit) {
    val isSelected = kind == selected
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isSelected, onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(16.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (isSelected) Sage else CaptionMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) Box(Modifier.size(8.dp).clip(CircleShape).background(Sage))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(titleRes), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(bodyRes), fontSize = 12.sp, color = CaptionMuted)
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Sage)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
