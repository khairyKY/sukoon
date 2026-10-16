package com.sukoon.app.ui.settings

import com.sukoon.app.platform.TimeFormat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.sukoon.app.domain.metrics.TargetRange
import com.sukoon.app.insulin.Profile
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.sukoon.app.ui.theme.Motion
import com.sukoon.app.ui.theme.Motion.staggerIn
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
import com.sukoon.app.ui.components.SukoonTextField
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
import com.sukoon.app.alarms.SoundPack
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
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.reminders.BasalReminderSettings
import com.sukoon.app.ui.components.ExpandedPanel
import com.sukoon.app.ui.sharing.LibreLinkUpSection
import com.sukoon.app.sharing.LibreLinkUp
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.sukoon.app.ui.sharing.DexcomSection
import com.sukoon.app.platform.Updates
import androidx.compose.ui.res.pluralStringResource
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.insights.Insight
import com.sukoon.app.insulin.DoseSettings
import com.sukoon.app.insulin.RatioLearner

/** The You tab's sections (design "You, divided"): each opens its own page from the hub. */
enum class YouPage(@StringRes val title: Int, @DrawableRes val icon: Int) {
    SENSOR(R.string.you_sensor, R.drawable.ic_sensor),
    ALARMS(R.string.you_alarms, R.drawable.ic_bell),
    PEOPLE(R.string.you_people, R.drawable.ic_people),
    INSULIN(R.string.you_insulin, R.drawable.ic_insulin),
    SUPPLIES(R.string.you_supplies, R.drawable.ic_box),
    CLINIC(R.string.you_clinic, R.drawable.ic_clinic),
    FITNESS(R.string.you_fitness, R.drawable.ic_walk),
    APPS(R.string.you_apps, R.drawable.ic_link),
    REPORTS(R.string.you_reports, R.drawable.ic_doc),
    APPEARANCE(R.string.you_appearance, R.drawable.ic_contrast),
    HELP(R.string.you_help, R.drawable.ic_help),
    /** Opens the Guide itself (no page of its own); Help & about links it too. */
    GUIDE(R.string.help_title, R.drawable.ic_book),
    /** The profile card's page: your name, About you and the parent lock (not a tile on the hub). */
    PROFILE(R.string.you_profile, R.drawable.ic_people),
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
    onPreviewPack: (SoundPack) -> Unit = {},
    /** Opens the Guide on one topic. */
    onOpenGuideTopic: (String) -> Unit = {},
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
    /** The last 24 hours of readings, for the sensor page's connection note. */
    dayOfReadings: suspend () -> List<GlucoseReading> = { emptyList() },
    basalReminder: BasalReminderSettings = BasalReminderSettings(),
    onBasalReminder: (BasalReminderSettings) -> Unit = {},
    usualBasalMinute: suspend () -> Int? = { null },
    updates: Updates? = null,
    /** The last month's doses logged with an injection site. */
    injectionSites: suspend () -> List<EventEntity> = { emptyList() },
    doseSettings: DoseSettings = DoseSettings(),
    onDoseSettings: (DoseSettings) -> Unit = {},
    /** Textbook starting points from the logbook's daily totals, when there are enough days. */
    doseStartingPoints: suspend () -> Insight.Formulas? = { null },
    /** Every meal's verdict and the ratios learned from the clean ones, given the correction factor (null: estimate it). */
    learningReport: suspend (Double?) -> RatioLearner.Report = { RatioLearner.Report(emptyList(), null, false, emptyList()) },
    profile: Profile = Profile(),
    onProfile: (Profile) -> Unit = {},
    /** The top of your target range (70 to this). */
    targetHigh: Int = TargetRange.DEFAULT_HIGH,
    onTargetHigh: (Int) -> Unit = {},
    parentPin: String? = null,
    onParentPin: (String?) -> Unit = {},
    /** Each new count opens You on that page (null: the hub): the You tab, Troubleshoot → Sensor. */
    openPage: Pair<Int, YouPage?> = 0 to null,
    /** Home's learning notice: open You → Insulin with the Learning screen up. */
    openLearning: Boolean = false,
    onOpenedLearning: () -> Unit = {},
    /** A guide-style link to somewhere outside You ("trends_report"): MainScaffold.openLink. */
    onLink: (String) -> Unit = {},
) {
    // The parent lock: while it's on (and not unlocked on this visit), changes ask for the PIN first.
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var askingPin by remember { mutableStateOf(false) }
    val locked = parentPin != null && !unlocked && com.sukoon.app.platform.ParentLock.appliesTo(profile.ageYears)
    fun <T> guarded(change: (T) -> Unit): (T) -> Unit = { if (locked) askingPin = true else change(it) }
    if (askingPin) PinDialog(setting = false, stored = parentPin, onDone = { unlocked = true; askingPin = false }, onDismiss = { askingPin = false })
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf<YouPage?>(null) }
    LaunchedEffect(openPage.first) { if (openPage.first > 0) page = openPage.second }
    LaunchedEffect(openLearning) { if (openLearning) page = YouPage.INSULIN }
    BackHandler(enabled = page != null) { page = null }
    // Someone who only follows sees what's theirs: who they follow, those alarms, the look, help. Not a sensor's settings.
    val role by (context.applicationContext as com.sukoon.app.SukoonApp).container.role.collectAsStateWithLifecycle()
    val wears = role.wears
    val missing = rememberMissingSetup().let { all -> if (wears) all else all.filter { it in FOLLOWER_SETUP_ITEMS } }

    val reduced = Motion.reduced()
    // Push / pop (motion spec): a page slides 30 dp in from the trailing edge (mirrored in Arabic) as it fades in over the hub; back reverses it.
    val travel = with(LocalDensity.current) { 30.dp.roundToPx() } * if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val hubScroll = rememberScrollState()
    val entering = Motion.firstShow() // the hub's cards stagger in once, not on every pop back to it
    AnimatedContent(
        page,
        modifier.fillMaxSize(),
        transitionSpec = {
            val push = targetState != null
            when {
                reduced -> fadeIn(tween(Motion.REDUCED)) togetherWith fadeOut(tween(Motion.REDUCED))
                push -> slideInHorizontally(tween(Motion.CALM, easing = Motion.Out)) { travel } + fadeIn(tween(Motion.CALM, easing = Motion.Out)) togetherWith
                    ExitTransition.KeepUntilTransitionsFinished
                else -> EnterTransition.None togetherWith
                    slideOutHorizontally(tween(Motion.CALM, easing = Motion.In)) { travel } + fadeOut(tween(Motion.CALM, easing = Motion.In))
            }.apply { targetContentZIndex = if (push) 1f else 0f }
        },
        label = "youPage",
    ) { shown ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(if (shown == null) hubScroll else rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            when (val open = shown) {
                null -> {
                    Text(stringResource(R.string.home_nav_you), fontFamily = HeadlineSerifFontFamily, fontSize = 30.sp, lineHeight = 36.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(14.dp))
                    ProfileCard(emergency.yourName, wears = pairing != null) { page = YouPage.PROFILE }
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
                        YouPage.FITNESS to fitnessSummary(),
                        YouPage.SUPPLIES to suppliesSummary(),
                        YouPage.CLINIC to clinicSummary(),
                        YouPage.APPS to stringResource(R.string.you_apps_summary),
                        YouPage.REPORTS to stringResource(R.string.you_reports_summary),
                        YouPage.APPEARANCE to "${themeLabel(themeMode)} · ${stringResource(colourName(com.sukoon.app.ui.theme.Themes.current))} · ${languageLabel()}",
                        YouPage.HELP to stringResource(R.string.you_help_summary),
                        YouPage.GUIDE to stringResource(R.string.you_guide_summary),
                    )
                    (if (wears) YouPage.entries - YouPage.PROFILE else FOLLOWER_PAGES).chunked(2).forEachIndexed { r, row ->
                        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEachIndexed { c, p -> SectionCard(p, summaries.getValue(p), Modifier.weight(1f).then(if (entering) Modifier.staggerIn(r * 2 + c) else Modifier)) { if (p == YouPage.GUIDE) onOpenGuide() else page = p } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (!wears) WearToo { page = YouPage.SENSOR }
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
                            if (pairing != null && sourceKind == SourceKind.LIBRE_BLE) {
                                Gap()
                                SectionLabel(stringResource(R.string.sensor_connection_title))
                                ConnectionHealth(dayOfReadings, saveIntervalMinutes)
                            }
                            Gap()
                            SectionLabel(stringResource(R.string.settings_source_title))
                            if (pairing != null) {
                                SourceOption(SourceKind.LIBRE_BLE, sourceKind, R.string.settings_source_sensor, R.string.settings_source_sensor_body) {
                                    onSelectSource(SourceKind.LIBRE_BLE)
                                    context.toast(context.getString(R.string.toast_source_sensor))
                                }
                            }
                            // LibreLinkUp connections as this phone's glucose (a Libre 3 on Abbott's app, say).
                            val llu = sharing.libreLinkUp
                            val lluAccount by llu.account.collectAsStateWithLifecycle()
                            val connections by produceState<List<LibreLinkUp.Connection>>(emptyList(), lluAccount) {
                                value = if (lluAccount == null) emptyList() else runCatching { llu.connections() }.getOrDefault(emptyList())
                            }
                            connections.forEach { c ->
                                val chosen = sourceKind == SourceKind.LIBRE_LINK_UP && llu.ownPatientId == c.patientId
                                SourceRow(stringResource(R.string.settings_source_llu, c.name.ifBlank { "…" }), stringResource(R.string.settings_source_llu_body), chosen) {
                                    llu.ownPatientId = c.patientId
                                    onSelectSource(SourceKind.LIBRE_LINK_UP)
                                    context.toast(context.getString(R.string.toast_source_llu, c.name))
                                }
                            }
                            val dexcomAccount by sharing.dexcom.account.collectAsStateWithLifecycle()
                            dexcomAccount?.let { d ->
                                SourceRow(stringResource(R.string.settings_source_dexcom, d.name.ifBlank { d.username }), stringResource(R.string.settings_source_dexcom_body), sourceKind == SourceKind.DEXCOM_SHARE) {
                                    onSelectSource(SourceKind.DEXCOM_SHARE)
                                    context.toast(context.getString(R.string.toast_source_dexcom))
                                }
                            }
                            SourceOption(SourceKind.SIMULATED, sourceKind, R.string.settings_source_demo, R.string.settings_source_demo_body) {
                                onSelectSource(SourceKind.SIMULATED)
                                context.toast(context.getString(R.string.toast_source_demo))
                            }
                            SourceOption(SourceKind.NONE, sourceKind, R.string.settings_source_none, R.string.settings_source_none_body) {
                                onSelectSource(SourceKind.NONE)
                            }
                            // Their sign-ins live under People; from here, one tap there.
                            if (lluAccount == null || dexcomAccount == null) {
                                Spacer(Modifier.height(8.dp))
                                LinkRow(stringResource(R.string.settings_source_cloud), stringResource(R.string.settings_source_cloud_body)) { page = YouPage.PEOPLE }
                            }
                            Gap()
                            SectionLabel(stringResource(R.string.readings_title))
                            ReadingsSection(saveIntervalMinutes, onSaveInterval)
                            Gap()
                            SectionLabel(stringResource(R.string.calibration_title))
                            CalibrationSection(calibration)
                        }
                        YouPage.ALARMS -> if (!wears) {
                            // Following only: their alarms reach you through these; nothing here is about a sensor of yours.
                            SectionLabel(stringResource(R.string.alarms_reach_title))
                            AlarmReach(sensorIsSource = true)
                            Gap()
                            SectionLabel(stringResource(R.string.alarms_history_title))
                            AlarmHistory(alarmLog)
                        } else {
                            // Whether alarms can reach you goes first while something stops them, else after the alarms (design "You · Alarms").
                            val blocked = !sourceKind.real || missing.any { it in ALARM_SETUP }
                            if (blocked) {
                                SectionLabel(stringResource(R.string.alarms_reach_title))
                                AlarmReach(sensorIsSource = sourceKind.real)
                                Gap()
                            }
                            if (locked) {
                                LockedNotice { askingPin = true }
                                Gap()
                            }
                            RangeCard(targetHigh, guarded(onTargetHigh))
                            Gap()
                            AlarmSettingsSection(alarmSettings, guarded(onAlarmSettings), onTestAlarm, onPreviewAlarm, onPreviewPack)
                            Gap()
                            SectionLabel(stringResource(R.string.workout_check_section))
                            GentleChecks()
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
                            if (wears) {
                                SectionLabel(stringResource(R.string.emergency_title))
                                EmergencySection(emergency, emergencyAlerts, onEmergency)
                                Gap()
                            }
                            SectionLabel(stringResource(R.string.sharing_title))
                            SharingSection(sharing, followerWatch, locked = locked, onUnlock = { askingPin = true })
                            Gap()
                            SectionLabel(stringResource(R.string.llu_title))
                            LibreLinkUpSection(sharing.libreLinkUp, sharing, followerWatch)
                            Gap()
                            SectionLabel(stringResource(R.string.dexcom_title))
                            DexcomSection(sharing.dexcom, sharing, followerWatch)
                        }
                        YouPage.INSULIN -> {
                            SectionLabel(stringResource(R.string.reminder_basal_section))
                            BasalReminderCard(basalReminder, onBasalReminder, usualBasalMinute)
                            Gap()
                            SectionLabel(stringResource(R.string.sites_title))
                            InjectionSitesCard(injectionSites)
                            Gap()
                            InsulinSection(insulinAction, onInsulinAction, doseSettings.step) { onDoseSettings(doseSettings.copy(step = it)) }
                            Gap()
                            DoseCard(doseSettings, guarded(onDoseSettings), doseStartingPoints, learningReport, insulinAction.durationMinutes / 60, profile, locked, { askingPin = true }, parentPin, onParentPin, openLearning, onOpenedLearning)
                            Spacer(Modifier.height(10.dp))
                            // About you (weight for the starting ratios, age for the lock) and the parent lock moved to the profile page.
                            LinkRow(stringResource(R.string.you_profile_link), stringResource(R.string.you_profile_link_body)) { page = YouPage.PROFILE }
                        }
                        YouPage.PROFILE -> {
                            var name by rememberSaveable { mutableStateOf(emergency.yourName) }
                            SukoonTextField(
                                value = name,
                                onValueChange = { name = it; onEmergency(emergency.copy(yourName = it)) },
                                label = stringResource(R.string.emergency_your_name),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Gap()
                            SectionLabel(stringResource(R.string.profile_title))
                            // Age turns the lock off from 18, so under the lock it's guarded like the rest.
                            if (locked) LockedNotice { askingPin = true } else ProfileFields(profile, onProfile)
                            if (wears && !locked && com.sukoon.app.platform.ParentLock.appliesTo(profile.ageYears)) {
                                Gap()
                                ParentLockRow(parentPin, onParentPin)
                            }
                        }
                        YouPage.SUPPLIES -> SuppliesPage()
                        YouPage.CLINIC -> ClinicPage(onOpenReports = { onLink("trends_report") })
                        YouPage.FITNESS -> FitnessPage(
                            (context.applicationContext as com.sukoon.app.SukoonApp).container.modes,
                            profile,
                            onEditProfile = { page = YouPage.PROFILE },
                            onWhy = { onOpenGuideTopic("modes") },
                        )
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
                                    stringResource(if (service == "ns") R.string.apps_close else if (nsOn) R.string.apps_change else R.string.apps_set_up),
                                    prominent = !nsOn && service != "ns",
                                ) { service = if (service == "ns") null else "ns" }
                                // Opens right under its row, in the same panel as the alarms' settings.
                                ExpandedPanel(service == "ns", inset = 0.dp) { NightscoutSection(nightscout, nightscoutStatus, onNightscout, onUploadNow, plain = true) }
                                HorizontalDivider(color = outline().copy(alpha = 0.08f))
                                ServiceRow(
                                    stringResource(R.string.apps_ai),
                                    if (geminiKey.isNotBlank()) stringResource(R.string.apps_ai_key_ending, geminiKey.takeLast(4)) else stringResource(R.string.apps_not_set_up),
                                    stringResource(if (service == "ai") R.string.apps_close else if (geminiKey.isNotBlank()) R.string.apps_change else R.string.apps_set_up),
                                    prominent = geminiKey.isBlank() && service != "ai",
                                ) { service = if (service == "ai") null else "ai" }
                                ExpandedPanel(service == "ai", inset = 0.dp) { AiKey(geminiKey, onSaveGeminiKey) }
                            }
                        }
                        YouPage.REPORTS -> {
                            Text(stringResource(R.string.you_reports_body), fontSize = 13.sp, color = CaptionMuted)
                            Gap()
                            SectionLabel(stringResource(R.string.export_title))
                            ExportSection(buildCsv)
                            Gap()
                            SectionLabel(stringResource(R.string.backup_title))
                            BackupSection()
                        }
                        YouPage.APPEARANCE -> {
                            SectionLabel(stringResource(R.string.you_theme))
                            ThemeCards(themeMode, onThemeMode)
                            Gap()
                            SectionLabel(stringResource(R.string.you_colour))
                            ColourChoice()
                            Gap()
                            SectionLabel(stringResource(R.string.you_language))
                            LanguageChoice()
                            Gap()
                            SectionLabel(stringResource(R.string.you_clock))
                            ClockChoice()
                            Gap()
                            SectionLabel(stringResource(R.string.widgets_title))
                            WidgetsCard()
                            Gap()
                            SectionLabel(stringResource(R.string.now_layout_section))
                            NowLayoutRow()
                        }
                        YouPage.GUIDE -> Unit // opens the Guide, never a page
                        YouPage.HELP -> {
                            HelpLinks(onGuide = onOpenGuide) {
                                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.sukoon.app.PRIVACY_URL))) }
                            }
                            Gap()
                            SectionLabel(stringResource(R.string.setup_title))
                            SetupChecklist(only = if (wears) null else FOLLOWER_SETUP_ITEMS)
                            Gap()
                            updates?.takeIf { com.sukoon.app.BuildConfig.SELF_UPDATE }?.let {
                                SectionLabel(stringResource(R.string.updates_title))
                                UpdatesSection(it, onBackUp = { page = YouPage.REPORTS })
                                Gap()
                            }
                            SectionLabel(stringResource(R.string.settings_about_title))
                            Text(stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME), fontSize = 12.5.sp, color = CaptionMuted)
                        }
                    }
                }
            }
        }
    }
}

private val FOLLOWER_PAGES = listOf(YouPage.PEOPLE, YouPage.ALARMS, YouPage.APPEARANCE, YouPage.HELP)

/** Following only: "I wear a sensor too" brings back everything for a sensor of your own (and opens it). */
@Composable
private fun WearToo(onDone: () -> Unit) {
    val container = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, outline(), RoundedCornerShape(18.dp))
            .clickable {
                container.settings.role = com.sukoon.app.data.prefs.UserRole.BOTH
                container.role.value = com.sukoon.app.data.prefs.UserRole.BOTH
                onDone()
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.you_follower_note), fontSize = 13.sp, color = CaptionMuted)
        Text(stringResource(R.string.you_wear_too), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
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
            Text(shown.take(1).uppercase(), fontFamily = HeadlineSerifFontFamily, fontSize = 22.sp, lineHeight = 26.sp, color = SageDeep)
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
            if (count == 1) stringResource(R.string.you_needs_one, first.lowercase(Locale.getDefault())) else pluralStringResource(R.plurals.you_needs_many, count.toInt(), count),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PillHighText,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.you_fix), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PillHighText)
    }
}

/** You hub: "Visit 28 Oct · HbA1c due", or what the page is for. */
@Composable
private fun clinicSummary(): String {
    val app = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container
    val visit by app.clinic.visit.collectAsStateWithLifecycle()
    val labs by app.clinic.labs.collectAsStateWithLifecycle()
    val today = java.time.LocalDate.now()
    val parts = listOfNotNull(
        visit?.takeIf { !it.isBefore(today) }?.let { stringResource(R.string.clinic_summary_visit, it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM").withDecimalStyle(java.time.format.DecimalStyle.ofDefaultLocale()))) },
        if (com.sukoon.app.clinic.Clinic.due(com.sukoon.app.clinic.LabKind.HBA1C, labs, today)) stringResource(R.string.clinic_summary_hba1c) else null,
    )
    return parts.joinToString(" · ").ifEmpty { stringResource(R.string.clinic_summary_none) }
}

/** You hub: "Long-acting 4 days · pickup Sunday", or that nothing's set up. */
@Composable
private fun suppliesSummary(): String {
    val app = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container
    val stock by app.supplies.stock.collectAsStateWithLifecycle()
    val pickup by app.supplies.pickup.collectAsStateWithLifecycle()
    val since = remember { java.time.Instant.now().minus(java.time.Duration.ofDays(120)).toEpochMilli() }
    val events by remember { app.logbookRepository.eventsSince(since) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val now = java.time.Instant.now()
    val parts = stock.mapNotNull { (k, s) ->
        s ?: return@mapNotNull null
        com.sukoon.app.supplies.Supply.daysLeft(com.sukoon.app.supplies.Supply.remaining(s, events, k), com.sukoon.app.supplies.Supply.perDay(events, k, now, java.time.ZoneId.systemDefault()))
            ?.let { stringResource(R.string.supply_summary_days, stringResource(kindName(k)), it) }
    } + listOfNotNull(pickup.next?.let { stringResource(R.string.supply_summary_pickup, it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM").withDecimalStyle(java.time.format.DecimalStyle.ofDefaultLocale()))) })
    return parts.joinToString(" · ").ifEmpty { stringResource(R.string.supply_summary_none) }
}

/** You hub: "Lean bulk · 2,980 kcal", or that there's no goal. */
@Composable
private fun fitnessSummary(): String {
    val container = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container
    val goal by container.modes.goalFlow.collectAsStateWithLifecycle()
    val kcal = com.sukoon.app.modes.Goals.targets(container.settings.profile, goal)?.kcal
    return when {
        goal.kind == com.sukoon.app.modes.GoalKind.NONE -> stringResource(R.string.you_fitness_none)
        kcal != null -> stringResource(R.string.you_fitness_summary, stringResource(goalName(goal.kind)), java.text.NumberFormat.getIntegerInstance().format(kcal))
        else -> stringResource(goalName(goal.kind))
    }
}

/** A fixed colour per section (not the theme's accent): white text on each passes 4.5:1. */
private fun sectionHue(page: YouPage): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(
    when (page) {
        YouPage.SENSOR -> 0xFF3E7A63
        YouPage.ALARMS -> 0xFFB04A42
        YouPage.PEOPLE -> 0xFF3A6E93
        YouPage.INSULIN -> 0xFF675CA6
        YouPage.SUPPLIES -> 0xFF8A6A2E
        YouPage.CLINIC -> 0xFFA44C78
        YouPage.FITNESS -> 0xFF4A7D3A
        YouPage.APPS -> 0xFF237A85
        YouPage.REPORTS -> 0xFF6F5F4E
        YouPage.APPEARANCE -> 0xFF56687A
        YouPage.HELP -> 0xFF5E6B64
        YouPage.GUIDE -> 0xFFA65A2A
        YouPage.PROFILE -> 0xFF3A6E93
    },
)

@Composable
private fun SectionCard(page: YouPage, summary: String, modifier: Modifier, onClick: () -> Unit) {
    // Each section its own colour: a tinted card and a solid tile, so they read apart at a glance.
    val hue = sectionHue(page)
    val dark = com.sukoon.app.ui.theme.LocalDarkTheme.current
    val tileBg = hue
    val tint = androidx.compose.ui.graphics.Color.White
    Column(
        modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .background(hue.copy(alpha = if (dark) 0.16f else 0.09f))
            .border(1.dp, hue.copy(alpha = if (dark) 0.35f else 0.22f), RoundedCornerShape(18.dp))
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

/** Help & about's Guide and privacy policy: rows like the rest of You, not bare links. */
@Composable
private fun HelpLinks(onGuide: () -> Unit, onPrivacy: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp)),
    ) {
        HelpLink(R.drawable.ic_book, stringResource(R.string.help_title), stringResource(R.string.you_guide_summary), onGuide)
        Box(Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(outline()))
        HelpLink(R.drawable.ic_lock, stringResource(R.string.privacy_link), stringResource(R.string.help_privacy_body), onPrivacy)
    }
}

@Composable
private fun HelpLink(@DrawableRes icon: Int, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(SageMist), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = SageDeep, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(body, fontSize = 12.5.sp, color = CaptionMuted)
        }
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = CaptionMuted, modifier = Modifier.size(18.dp))
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
        Text(title, fontFamily = HeadlineSerifFontFamily, fontSize = 28.sp, lineHeight = 34.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** "Day 7 of 15 · live", "Warming up", "Ended", "Demo data", "Not connected". */
@Composable
private fun sensorSummary(sourceKind: SourceKind, status: SourceStatus, pairing: SensorPairing?): String {
    if (sourceKind == SourceKind.SIMULATED) return stringResource(R.string.you_sensor_demo)
    if (sourceKind == SourceKind.NONE) return stringResource(R.string.you_sensor_none)
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
    if (Build.VERSION.SDK_INT < 33) return stringResource(R.string.you_language_auto)
    val chosen = LocalContext.current.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
    return when (chosen) {
        "en" -> "English"
        "ar" -> "العربية الفصحى"
        "ar-EG" -> "العربية المصرية"
        else -> stringResource(R.string.you_language_auto)
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
    SukoonTextField(
        value = keyInput,
        onValueChange = { keyInput = it },
        label = stringResource(R.string.settings_ai_key),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    val consent = com.sukoon.app.ui.components.rememberConsent("ai", R.string.consent_ai_title, R.string.consent_ai_body)
    PrimaryButton(stringResource(if (geminiKey.isBlank()) R.string.settings_save else R.string.settings_update)) {
        val save = {
            onSave(keyInput)
            context.toast(context.getString(if (keyInput.isBlank()) R.string.toast_ai_key_removed else R.string.settings_ai_saved))
        }
        if (keyInput.isBlank()) save() else consent(save)
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

/** The theme's name, for the picker and the You hub. */
private fun colourName(t: com.sukoon.app.ui.theme.ColorTheme): Int = when (t) {
    com.sukoon.app.ui.theme.ColorTheme.SAGE -> R.string.colour_sage
    com.sukoon.app.ui.theme.ColorTheme.ROSE -> R.string.colour_rose
    com.sukoon.app.ui.theme.ColorTheme.TIDE -> R.string.colour_tide
    com.sukoon.app.ui.theme.ColorTheme.DUSK -> R.string.colour_dusk
    com.sukoon.app.ui.theme.ColorTheme.LAGOON -> R.string.colour_lagoon
    com.sukoon.app.ui.theme.ColorTheme.SLATE -> R.string.colour_slate
}

/** Six colour themes, three to a row: each tile shows its accent and light tone. */
@Composable
private fun ColourChoice() {
    val settings = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.settings
    val current = com.sukoon.app.ui.theme.Themes.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.sukoon.app.ui.theme.ColorTheme.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    val on = t == current
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(if (on) 2.dp else 1.dp, if (on) Sage else outline(), RoundedCornerShape(16.dp))
                            .clickable { settings.colorTheme = t }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)) {
                            Box(Modifier.weight(1f).fillMaxSize().background(t.accent))
                            Box(Modifier.weight(1f).fillMaxSize().background(t.light))
                        }
                        Text(stringResource(colourName(t)), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
        Text(stringResource(R.string.colour_note), fontSize = 12.5.sp, color = CaptionMuted)
    }
}

/** 12- or 24-hour times everywhere in Sukoon, or the phone's. */
@Composable
private fun ClockChoice() {
    val settings = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.settings
    val sample = java.time.LocalTime.of(13, 50)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp)),
    ) {
        listOf(
            TimeFormat.Mode.PHONE to stringResource(R.string.theme_auto),
            TimeFormat.Mode.H12 to stringResource(R.string.you_clock_12),
            TimeFormat.Mode.H24 to stringResource(R.string.you_clock_24),
        ).forEach { (mode, label) ->
            val on = mode == TimeFormat.mode
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(enabled = !on) {
                        settings.timeFormat = mode
                        TimeFormat.mode = mode
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        when (mode) {
                            TimeFormat.Mode.PHONE -> stringResource(R.string.you_clock_phone)
                            TimeFormat.Mode.H12 -> java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(sample)
                            TimeFormat.Mode.H24 -> java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(sample)
                        },
                        fontSize = 12.5.sp,
                        color = CaptionMuted,
                    )
                }
                Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) Sage else CaptionMuted.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.size(9.dp).clip(CircleShape).background(Sage))
                }
            }
        }
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
    val current = manager.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
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
            "ar" to "العربية الفصحى",
            "ar-EG" to "العربية المصرية",
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

/** A row that opens another page: what's there, a line on why, and a chevron. */
@Composable
private fun LinkRow(title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(body, fontSize = 12.5.sp, color = CaptionMuted)
        }
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = CaptionMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SourceOption(kind: SourceKind, selected: SourceKind, titleRes: Int, bodyRes: Int, onSelect: () -> Unit) =
    SourceRow(stringResource(titleRes), stringResource(bodyRes), kind == selected, onSelect)

/** One choice of where readings come from: a radio, its name and a line on what it means. */
@Composable
private fun SourceRow(title: String, body: String, isSelected: Boolean, onSelect: () -> Unit) {
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
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(body, fontSize = 12.sp, color = CaptionMuted)
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

/** Appearance › Now: opens "Make Now yours". */
@Composable
private fun NowLayoutRow() {
    val settings = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.settings
    var open by remember { mutableStateOf(false) }
    if (open) com.sukoon.app.ui.home.NowLayoutSheet(settings.nowLayout, onChange = { settings.nowLayout = it }, onDismiss = { open = false })
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp))
            .clickable { open = true }
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(stringResource(R.string.now_layout_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.now_layout_row_body), fontSize = 12.5.sp, color = com.sukoon.app.ui.theme.CaptionMuted)
        }
        Text("›", fontSize = 20.sp, color = com.sukoon.app.ui.theme.CaptionMuted)
    }
}
