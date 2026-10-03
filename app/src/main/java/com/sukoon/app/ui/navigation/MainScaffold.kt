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
    // Home's food/insulin shortcuts: which new entry the Logbook should open on arrival.
    var pendingEntry by rememberSaveable { mutableStateOf<LogEventType?>(null) }
    Scaffold(
        bottomBar = { SukoonBottomBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SukoonTab.NOW.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(SukoonTab.NOW.route) {
                val repository = (LocalContext.current.applicationContext as SukoonApp).container.glucoseRepository
                val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
                val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
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
                if (askingForHelp) EmergencyActionsDialog(emergencyAlerts, latest, onDismiss = { askingForHelp = false })
                Column {
                    if (missingSetup.isNotEmpty() && !setupLater) {
                        SetupBanner(missingSetup, onFix = toYou, onLater = { setupLater = true })
                    }
                    HomeScreen(
                        state = homeState,
                        modifier = Modifier.weight(1f),
                        onPairSensor = toYou,
                        onEnterCodeManually = toYou,
                        onTroubleshoot = toYou,
                        // "I've treated it": urgent re-checks in 5 min; a plain low after its usual snooze.
                        onTreated = {
                            scope.launch {
                                if (homeState is HomeUiState.Urgent) alarms.acknowledge(AlarmType.URGENT_LOW, 5)
                                else alarms.acknowledge(AlarmType.LOW, settings.alarmSettings.lowSnoozeMinutes)
                            }
                        },
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
                    )
                }
            }
            composable(SukoonTab.TRENDS.route) {
                // Hub over Graph (A3) + Logbook (A4); Insights (A9) slots in as a third sub-tab later.
                val container = (LocalContext.current.applicationContext as SukoonApp).container
                val graphViewModel: GraphViewModel = viewModel(
                    factory = GraphViewModel.factory(container.glucoseRepository, container.logbookRepository),
                )
                val graphState by graphViewModel.uiState.collectAsStateWithLifecycle()
                val logbookViewModel: LogbookViewModel = viewModel(factory = LogbookViewModel.factory(container.logbookRepository, container.glucoseRepository, container.gemini))
                val logbookState by logbookViewModel.uiState.collectAsStateWithLifecycle()
                val askViewModel: AskViewModel = viewModel(
                    factory = AskViewModel.factory(container.glucoseRepository, container.logbookRepository, container.gemini),
                )
                val askState by askViewModel.uiState.collectAsStateWithLifecycle()
                val insightsViewModel: InsightsViewModel = viewModel(
                    factory = InsightsViewModel.factory(container.glucoseRepository, container.logbookRepository, container.settings),
                )
                val insightsState by insightsViewModel.uiState.collectAsStateWithLifecycle()
                TrendsHub(
                    graphState = graphState,
                    onSelectRange = graphViewModel::selectRange,
                    logbookState = logbookState,
                    onQuickLog = logbookViewModel::log,
                    onUpdateEvent = logbookViewModel::updateEvent,
                    onDeleteEvent = logbookViewModel::deleteEvent,
                    onEstimateCarbs = logbookViewModel::estimateCarbs,
                    onLogMeal = logbookViewModel::logMeal,
                    askState = askState,
                    hasAiKey = askViewModel.hasKey,
                    onAsk = askViewModel::ask,
                    onClearAsk = askViewModel::clear,
                    onOpenSettings = { navController.navigateToTab(SukoonTab.YOU) },
                    insightsState = insightsState,
                    onAcknowledgeInsights = insightsViewModel::acknowledge,
                    pendingEntry = pendingEntry,
                    onPendingEntryHandled = { pendingEntry = null },
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
                    onPreviewAlarm = container.alarms::preview,
                    emergency = emergency,
                    emergencyAlerts = container.emergency,
                    sharing = container.sharing,
                    followerWatch = container.followerWatch,
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
