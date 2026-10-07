package com.sukoon.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukoon.app.SukoonApp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sukoon.app.R
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.ui.insights.InsightsViewModel
import com.sukoon.app.ui.home.HomeUiState
import com.sukoon.app.ui.settings.SetupBanner
import com.sukoon.app.ui.settings.rememberMissingSetup
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.ui.ai.AskViewModel
import com.sukoon.app.ui.graph.GraphViewModel
import com.sukoon.app.ui.home.HomeScreen
import com.sukoon.app.ui.home.HomeViewModel
import com.sukoon.app.ui.logbook.LogbookViewModel
import com.sukoon.app.ui.settings.SettingsScreen
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.settings.EmergencyActionsDialog
import com.sukoon.app.ui.components.toast
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sukoon.app.data.source.libre.SensorLife
import com.sukoon.app.ui.home.SensorEndingBanner
import java.time.Duration
import java.time.Instant
import com.sukoon.app.ui.reports.ReportViewModel
import com.sukoon.app.ui.help.GettingStartedCard
import com.sukoon.app.ui.help.HelpDialog
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.sukoon.app.ui.theme.Motion
import com.sukoon.app.data.prefs.UserRole
import com.sukoon.app.platform.SetupItem
import com.sukoon.app.ui.home.FollowingHome
import com.sukoon.app.ui.home.FollowingStrip
import com.sukoon.app.ui.home.FollowingTrends
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sukoon.app.ui.home.HomeStat
import com.sukoon.app.ui.home.StatsPicker
import kotlinx.coroutines.flow.first
import com.sukoon.app.reminders.BasalReminder
import com.sukoon.app.ui.settings.minuteLabel
import java.time.ZoneId
import com.sukoon.app.ui.settings.UpdateBanner
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.insulin.RatioLearner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Top-level navigation, per the shipped design's 3-tab bottom bar (Now / Trends / You) — not the
 * 5 tabs the earlier plan assumed. The design consolidates: Logbook + Insights live under Trends;
 * Sharing + Settings under You. Those sub-screens (A3/A4/A9/A10) become nested destinations of
 * these tabs as they land.
 *
 * State is preserved per-tab via the standard popUpTo(saveState)/restoreState pattern.
 */
enum class SukoonTab(val route: String, @StringRes val labelRes: Int) {
    NOW("now", R.string.home_nav_now),
    TRENDS("trends", R.string.home_nav_trends),
    YOU("you", R.string.home_nav_you),
}

@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    var showGuide by rememberSaveable { mutableStateOf(false) }
    if (showGuide) HelpDialog(onClose = { showGuide = false })
    // Health Connect only lets most phones read while Sukoon is on screen: sync on every return.
    val appContainer = (LocalContext.current.applicationContext as SukoonApp).container
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { runCatching { appContainer.healthConnect.sync() } }
    }
    // Home's food/insulin shortcuts: which new entry the Logbook should open on arrival.
    var pendingEntry by rememberSaveable { mutableStateOf<LogEventType?>(null) }
    // Pull down on Home or the Logbook: MyFitnessPal's latest through Health Connect, and what came in.
    val syncContext = LocalContext.current
    val syncMfp: suspend () -> String = {
        val health = appContainer.healthConnect
        runCatching {
            if (!health.canReadMeals()) {
                syncContext.getString(R.string.sync_mfp_connect)
            } else {
                val r = health.sync()
                if (r.mealsAdded + r.mealsUpdated == 0) syncContext.getString(R.string.sync_mfp_none)
                else syncContext.getString(R.string.sync_mfp_new, r.mealsAdded, r.mealsUpdated)
            }
        }.getOrElse { syncContext.getString(R.string.sync_failed, it.message ?: it.javaClass.simpleName) }
    }
    val requested by appContainer.requestedEntry.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        val type = requested ?: return@LaunchedEffect
        pendingEntry = type
        navController.navigateToTab(SukoonTab.TRENDS)
        appContainer.requestedEntry.value = null
    }
    Scaffold(
        bottomBar = { SukoonBottomBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SukoonTab.NOW.route,
            modifier = Modifier.padding(innerPadding),
            // Stillness first (motion spec): switching tabs is a short fade, not travel.
            enterTransition = { fadeIn(tween(Motion.BASE, easing = Motion.Out)) },
            exitTransition = { fadeOut(tween(Motion.QUICK, easing = Motion.In)) },
        ) {
            composable(SukoonTab.NOW.route) {
                val home = (LocalContext.current.applicationContext as SukoonApp).container
                val role by home.role.collectAsStateWithLifecycle()
                if (role == UserRole.FOLLOWER) {
                    // Following only: Home is the person followed (design "Follower · Home").
                    Column {
                        val missing = rememberMissingSetup().filter { it in FOLLOWER_SETUP }
                        var later by rememberSaveable { mutableStateOf(false) }
                        if (missing.isNotEmpty() && !later) SetupBanner(missing, onFix = { navController.navigateToTab(SukoonTab.YOU) }, onLater = { later = true })
                        FollowingHome(home.sharing, home.settings, onAddPerson = { navController.navigateToTab(SukoonTab.YOU) }, modifier = Modifier.weight(1f))
                    }
                    return@composable
                }
                val repository = home.glucoseRepository
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(
                        repository,
                        home::sensorLife,
                        home.logbookRepository,
                        home.alarms.treatedAt,
                        actionOf = { home.settings.insulinAction },
                        healthToday = {
                            home.healthConnect.today().let { today ->
                                buildMap {
                                    today.steps?.let { put(HomeStat.STEPS, it.toDouble()) }
                                    today.waterLiters?.let { put(HomeStat.WATER, it) }
                                }
                            }
                        },
                    ),
                )
                val stats by homeViewModel.stats.collectAsStateWithLifecycle()
                var chosenStats by remember { mutableStateOf(home.settings.homeStats) }
                var pickingStats by remember { mutableStateOf(false) }
                if (pickingStats) {
                    StatsPicker(chosenStats, home.healthConnect, onChange = { home.settings.homeStats = it; chosenStats = it }, onDismiss = { pickingStats = false })
                }
                val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                val brief by homeViewModel.brief.collectAsStateWithLifecycle()
                val toYou = { navController.navigateToTab(SukoonTab.YOU) }
                val alarms = (LocalContext.current.applicationContext as SukoonApp).container.alarms
                val settings = (LocalContext.current.applicationContext as SukoonApp).container.settings
                val scope = rememberCoroutineScope()
                val missingSetup = rememberMissingSetup()
                var setupLater by rememberSaveable { mutableStateOf(false) }
                val context = LocalContext.current
                val emergencyAlerts = (context.applicationContext as SukoonApp).container.emergency
                val latest by repository.latestReading.collectAsStateWithLifecycle(initialValue = null)
                var askingForHelp by remember { mutableStateOf(false) }
                val insulinOnBoard by (context.applicationContext as SukoonApp).container.insulinOnBoard.collectAsStateWithLifecycle(initialValue = 0.0)
                if (askingForHelp) EmergencyActionsDialog(emergencyAlerts, latest, onDismiss = { askingForHelp = false })
                var viewing by remember { mutableStateOf<String?>(null) }
                viewing?.let { id ->
                    Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            FollowingHome(home.sharing, home.settings, onAddPerson = { viewing = null; toYou() }, personId = id)
                        }
                    }
                }
                Column {
                    // Wearing and following: the people followed sit above your own Home.
                    if (role == UserRole.BOTH) FollowingStrip(home.sharing) { viewing = it.id }
                    if (missingSetup.isNotEmpty() && !setupLater) {
                        SetupBanner(missingSetup, onFix = toYou, onLater = { setupLater = true })
                    }
                    val app = (context.applicationContext as SukoonApp).container
                    var startDismissed by remember { mutableStateOf(app.settings.gettingStartedDismissed) }
                    val sensorConnected = app.pairingStore.load() != null
                    val hasContact = app.settings.emergency.contacts.isNotEmpty()
                    if (!startDismissed && !(sensorConnected && hasContact)) {
                        GettingStartedCard(
                            sensorConnected = sensorConnected,
                            hasEmergencyContact = hasContact,
                            onSetUp = toYou,
                            onGuide = { showGuide = true },
                            onDismiss = {
                                app.settings.gettingStartedDismissed = true
                                startDismissed = true
                            },
                        )
                    }
                    val life by homeViewModel.sensorLife.collectAsStateWithLifecycle()
                    (life as? SensorLife.Running)
                        ?.takeIf { Duration.between(Instant.now(), it.endsAt) <= Duration.ofHours(24) }
                        ?.let { SensorEndingBanner(it, onClick = toYou) }
                    UpdateBanner(home.updates, onBackUp = toYou)
                    HomeScreen(
                        state = homeState,
                        brief = brief,
                        onSync = syncMfp,
                        modifier = Modifier.weight(1f),
                        onPairSensor = toYou,
                        onEnterCodeManually = toYou,
                        onTroubleshoot = toYou,
                        onTreated = { scope.launch { alarms.treated() } },
                        onSnooze = { scope.launch { alarms.acknowledge(AlarmType.LOW, 15) } },
                        onAlertEmergencyContact = {
                            if (emergencyAlerts.contacts.isEmpty()) {
                                context.toast(context.getString(R.string.toast_emergency_no_contacts), long = true)
                                toYou()
                            } else {
                                askingForHelp = true
                            }
                        },
                        onAddFood = { pendingEntry = LogEventType.CARB; navController.navigateToTab(SukoonTab.TRENDS) },
                        onAddInsulin = { pendingEntry = LogEventType.INSULIN; navController.navigateToTab(SukoonTab.TRENDS) },
                        insulinOnBoard = insulinOnBoard,
                        stats = stats,
                        chosenStats = chosenStats,
                        onChooseStats = { pickingStats = true },
                    )
                }
            }
            composable(SukoonTab.TRENDS.route) {
                // Hub over Graph (A3) + Logbook (A4); Insights (A9) slots in as a third sub-tab later.
                val container = (LocalContext.current.applicationContext as SukoonApp).container
                val role by container.role.collectAsStateWithLifecycle()
                if (role == UserRole.FOLLOWER) {
                    FollowingTrends(container.sharing)
                    return@composable
                }
                val graphViewModel: GraphViewModel = viewModel(
                    factory = GraphViewModel.factory(container.glucoseRepository, container.logbookRepository),
                )
                val graphState by graphViewModel.uiState.collectAsStateWithLifecycle()
                val logbookViewModel: LogbookViewModel = viewModel(factory = LogbookViewModel.factory(container.logbookRepository, container.glucoseRepository, container.gemini, container.entryPhotos) { container.settings.insulinAction })
                val logbookState by logbookViewModel.uiState.collectAsStateWithLifecycle()
                val askViewModel: AskViewModel = viewModel(
                    factory = AskViewModel.factory(container.glucoseRepository, container.logbookRepository, container.gemini, doseSettings = { container.settings.doseSettings }) { container.settings.insulinAction },
                )
                val askState by askViewModel.uiState.collectAsStateWithLifecycle()
                val insightsViewModel: InsightsViewModel = viewModel(
                    factory = InsightsViewModel.factory(container.glucoseRepository, container.logbookRepository, container.settings),
                )
                val insightsState by insightsViewModel.uiState.collectAsStateWithLifecycle()
                val reportViewModel: ReportViewModel = viewModel(factory = ReportViewModel.factory(container.glucoseRepository))
                val reportState by reportViewModel.state.collectAsStateWithLifecycle()
                TrendsHub(
                    graphState = graphState,
                    onSelectRange = graphViewModel::selectRange,
                    logbookState = logbookState,
                    onSaveEntry = logbookViewModel::save,
                    onUndoEntry = logbookViewModel::undo,
                    onUpdateEvent = logbookViewModel::updateEvent,
                    onDeleteEvent = logbookViewModel::deleteEvent,
                    onEstimateCarbs = logbookViewModel::estimateCarbs,
                    onEntryPhoto = logbookViewModel::setPhoto,
                    askState = askState,
                    hasAiKey = askViewModel.hasKey,
                    onAsk = askViewModel::ask,
                    onClearAsk = askViewModel::clear,
                    onOpenSettings = { navController.navigateToTab(SukoonTab.YOU) },
                    insightsState = insightsState,
                    onAcknowledgeInsights = insightsViewModel::acknowledge,
                    pendingEntry = pendingEntry,
                    onPendingEntryHandled = { pendingEntry = null },
                    onSync = syncMfp,
                    reportState = reportState,
                    onSelectReportDays = reportViewModel::selectDays,
                    reportName = container.settings.emergency.yourName,
                    doseSettings = { container.settings.doseSettings },
                )
            }
            composable(SukoonTab.YOU.route) {
                val container = (LocalContext.current.applicationContext as SukoonApp).container
                val sourceKind by container.sourceKind.collectAsStateWithLifecycle()
                val status by container.glucoseRepository.status.collectAsStateWithLifecycle()
                var pairing by remember { mutableStateOf(container.pairingStore.load()) }
                var geminiKey by remember { mutableStateOf(container.settings.geminiApiKey) }
                var alarmSettings by remember { mutableStateOf(container.settings.alarmSettings) }
                var emergency by remember { mutableStateOf(container.settings.emergency) }
                var insulinAction by remember { mutableStateOf(container.settings.insulinAction) }
                var basalReminder by remember { mutableStateOf(container.settings.basalReminder) }
                var doseSettings by remember { mutableStateOf(container.settings.doseSettings) }
                val youContext = LocalContext.current
                val themeMode by container.themeMode.collectAsStateWithLifecycle()
                var saveInterval by remember { mutableIntStateOf(container.settings.saveIntervalMinutes) }
                var nightscout by remember { mutableStateOf(container.nightscout.config) }
                val nightscoutStatus by container.nightscout.status.collectAsStateWithLifecycle()
                SettingsScreen(
                    sourceKind = sourceKind,
                    status = status,
                    pairing = pairing,
                    geminiKey = geminiKey,
                    onSelectSource = container::selectSource,
                    onPaired = { read, scannedAt ->
                        container.pairSensor(read, scannedAt)
                        pairing = container.pairingStore.load()
                    },
                    onForgetSensor = {
                        container.pairingStore.clear()
                        pairing = null
                        container.selectSource(SourceKind.SIMULATED)
                    },
                    onSaveGeminiKey = { key ->
                        container.settings.geminiApiKey = key
                        geminiKey = container.settings.geminiApiKey
                    },
                    alarmSettings = alarmSettings,
                    onAlarmSettings = { changed ->
                        container.settings.alarmSettings = changed.sanitized()
                        alarmSettings = container.settings.alarmSettings
                    },
                    onTestAlarm = container.alarms::test,
                    alarmLog = container.alarmLog.entries.collectAsStateWithLifecycle().value,
                    dayOfReadings = { container.glucoseRepository.readingsSince(System.currentTimeMillis() - Duration.ofDays(1).toMillis()).first() },
                    onPreviewAlarm = container.alarms::preview,
                    onPreviewPack = container.alarms::previewPack,
                    emergency = emergency,
                    emergencyAlerts = container.emergency,
                    sharing = container.sharing,
                    followerWatch = container.followerWatch,
                    healthConnect = container.healthConnect,
                    calibration = container.calibration,
                    onOpenGuide = { showGuide = true },
                    updates = container.updates,
                    themeMode = themeMode,
                    onThemeMode = { mode ->
                        container.settings.themeMode = mode
                        container.themeMode.value = mode
                    },
                    insulinAction = insulinAction,
                    onInsulinAction = { changed ->
                        container.settings.insulinAction = changed
                        insulinAction = container.settings.insulinAction
                    },
                    basalReminder = basalReminder,
                    onBasalReminder = { changed ->
                        container.settings.basalReminder = changed
                        basalReminder = changed
                        BasalReminder.schedule(youContext, changed)
                        youContext.toast(if (changed.enabled) youContext.getString(R.string.toast_reminder_set, minuteLabel(changed.minuteOfDay)) else youContext.getString(R.string.toast_reminder_off))
                    },
                    usualBasalMinute = {
                        BasalReminder.usualMinute(container.logbookRepository.eventsSince(System.currentTimeMillis() - Duration.ofDays(30).toMillis()).first(), ZoneId.systemDefault())
                    },
                    doseSettings = doseSettings,
                    onDoseSettings = { changed ->
                        container.settings.doseSettings = changed
                        doseSettings = changed
                    },
                    doseStartingPoints = {
                        InsightEngine.formulas(container.logbookRepository.eventsSince(System.currentTimeMillis() - Duration.ofDays(14).toMillis()).first(), ZoneId.systemDefault())
                    },
                    learningReport = { factor ->
                        withContext(Dispatchers.Default) {
                            val since = System.currentTimeMillis() - Duration.ofDays(30).toMillis()
                            RatioLearner.report(
                                container.glucoseRepository.readingsSince(since).first(),
                                container.logbookRepository.eventsSince(since).first(),
                                factor,
                                ZoneId.systemDefault(),
                                container.settings.insulinAction,
                            )
                        }
                    },
                    injectionSites = {
                        container.logbookRepository.eventsSince(System.currentTimeMillis() - Duration.ofDays(30).toMillis()).first().filter { it.site != null }
                    },
                    onEmergency = { changed ->
                        container.settings.emergency = changed.sanitized()
                        emergency = container.settings.emergency
                    },
                    saveIntervalMinutes = saveInterval,
                    onSaveInterval = { minutes ->
                        container.settings.saveIntervalMinutes = minutes
                        saveInterval = container.settings.saveIntervalMinutes
                    },
                    nightscout = nightscout,
                    nightscoutStatus = nightscoutStatus,
                    onNightscout = { changed ->
                        container.nightscout.config = changed
                        nightscout = container.nightscout.config
                        container.nightscout.connect()
                    },
                    onUploadNow = container.nightscout::uploadNow,
                    buildCsv = container::exportCsv,
                )
            }
        }
    }
}

@Composable
private fun SukoonBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            SukoonTab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                BottomBarItem(
                    label = stringResource(tab.labelRes),
                    selected = selected,
                    onClick = {
                        if (!selected) navController.navigateToTab(tab)
                    },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTab(tab: SukoonTab) {
    navigate(tab.route) {
        // Preserve each tab's state across switches; avoid stacking duplicates.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBarItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (selected) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Sage))
        } else {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f), CircleShape),
            )
        }
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 9.5.sp,
            color = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            },
        )
    }
}

/** What a follower-only phone needs for alerts to reach it (no sensor, no emergency texts). */
private val FOLLOWER_SETUP = setOf(SetupItem.NOTIFICATIONS, SetupItem.FULL_SCREEN, SetupItem.OVERLAY, SetupItem.BATTERY)
