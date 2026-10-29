package com.sukoon.app.ui.navigation

import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.annotation.DrawableRes
import androidx.compose.runtime.produceState
import com.sukoon.app.ui.logbook.formatAmountLocalized
import com.sukoon.app.ui.insights.slot
import com.sukoon.app.data.db.logType
import com.sukoon.app.domain.metrics.TargetRange
import com.sukoon.app.insulin.Dose
import com.sukoon.app.domain.metrics.FingerPricks
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import com.sukoon.app.ui.help.startDone
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
import com.sukoon.app.ui.home.BluetoothOffBanner
import com.sukoon.app.ui.components.rememberBluetoothOn
import com.sukoon.app.ui.components.turnOnBluetooth
import com.sukoon.app.ui.settings.YouPage
import java.time.Duration
import java.time.Instant
import com.sukoon.app.ui.reports.ReportViewModel
import com.sukoon.app.ui.help.GettingStartedCard
import com.sukoon.app.ui.help.HelpDialog
import com.sukoon.app.insights.InsightAction
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
enum class SukoonTab(val route: String, @StringRes val labelRes: Int, @DrawableRes val icon: Int, @DrawableRes val iconOn: Int) {
    NOW("now", R.string.home_nav_now, R.drawable.ic_nav_now, R.drawable.ic_nav_now_on),
    TRENDS("trends", R.string.home_nav_trends, R.drawable.ic_nav_trends, R.drawable.ic_nav_trends_on),
    YOU("you", R.string.home_nav_you, R.drawable.ic_nav_you, R.drawable.ic_nav_you_on),
}

@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    var showGuide by rememberSaveable { mutableStateOf(false) }
    /** The Guide opens on this topic (null: its hub). */
    var guideTopic by rememberSaveable { mutableStateOf<String?>(null) }
    // Health Connect only lets most phones read while Sukoon is on screen: sync on every return.
    val appContainer = (LocalContext.current.applicationContext as SukoonApp).container
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { runCatching { appContainer.healthConnect.sync() } }
    }
    // Home's food/insulin shortcuts: which new entry the Logbook should open on arrival.
    var pendingEntry by rememberSaveable { mutableStateOf<LogEventType?>(null) }
    // Home's "meal without insulin": open insulin for this meal in the Logbook.
    var pendingMealInsulin by rememberSaveable { mutableStateOf<Long?>(null) }
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
    val requestedMeal by appContainer.requestedMealInsulin.collectAsStateWithLifecycle()
    LaunchedEffect(requestedMeal) {
        val meal = requestedMeal ?: return@LaunchedEffect
        pendingMealInsulin = meal
        navController.navigateToTab(SukoonTab.TRENDS)
        appContainer.requestedMealInsulin.value = null
    }
    val requested by appContainer.requestedEntry.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        val type = requested ?: return@LaunchedEffect
        pendingEntry = type
        navController.navigateToTab(SukoonTab.TRENDS)
        appContainer.requestedEntry.value = null
    }
    // You opens on its hub, or on the page asked for (Troubleshoot → Sensor); the counter makes each ask count.
    var youOpen by remember { mutableStateOf(0 to (null as YouPage?)) }
    fun openYou(page: YouPage? = null) {
        youOpen = youOpen.first + 1 to page
        navController.navigateToTab(SukoonTab.YOU)
    }
    // A Trends sub-tab asked for from elsewhere (a guide link), handled once by TrendsHub.
    var trendsRequest by remember { mutableStateOf<TrendsSubTab?>(null) }
    fun openTrends(tab: TrendsSubTab) {
        trendsRequest = tab
        navController.navigateToTab(SukoonTab.TRENDS)
    }
    // Now's Today sheet asked for from elsewhere (a guide link); Now opens it once.
    var todayRequest by remember { mutableStateOf(false) }
    /** A guide link ("link" in assets/guide) to the screen it names. */
    fun openLink(link: String) {
        when (link) {
            "now" -> navController.navigateToTab(SukoonTab.NOW)
            "today" -> {
                todayRequest = true
                navController.navigateToTab(SukoonTab.NOW)
            }
            "profile" -> openYou(YouPage.PROFILE)
            "supplies" -> openYou(YouPage.SUPPLIES)
            "reminders" -> openYou(YouPage.REMINDERS)
            "clinic" -> openYou(YouPage.CLINIC)
            "sensor" -> openYou(YouPage.SENSOR)
            "sensor_start" -> {
                appContainer.requestedStartSensor.value = true
                openYou(YouPage.SENSOR)
            }
            "alarms" -> openYou(YouPage.ALARMS)
            "people" -> openYou(YouPage.PEOPLE)
            "fitness" -> openYou(YouPage.FITNESS)
            "insulin" -> openYou(YouPage.INSULIN)
            "learning" -> {
                appContainer.requestedLearning.value = true
                openYou(YouPage.INSULIN)
            }
            "apps" -> openYou(YouPage.APPS)
            "reports" -> openYou(YouPage.REPORTS)
            "appearance" -> openYou(YouPage.APPEARANCE)
            "help" -> openYou(YouPage.HELP)
            "trends_graph" -> openTrends(TrendsSubTab.GRAPH)
            "trends_logbook" -> openTrends(TrendsSubTab.LOGBOOK)
            "trends_insights" -> openTrends(TrendsSubTab.INSIGHTS)
            "trends_report" -> openTrends(TrendsSubTab.REPORT)
            "trends_ask" -> openTrends(TrendsSubTab.ASK)
            "entry_carb" -> appContainer.requestedEntry.value = LogEventType.CARB
            "entry_insulin" -> appContainer.requestedEntry.value = LogEventType.INSULIN
            "entry_basal" -> appContainer.requestedEntry.value = LogEventType.BASAL
            "entry_fingerstick" -> appContainer.requestedEntry.value = LogEventType.FINGERSTICK
        }
    }
    val requestedLink by appContainer.requestedLink.collectAsStateWithLifecycle()
    LaunchedEffect(requestedLink) { requestedLink?.let { appContainer.requestedLink.value = null; openLink(it) } }
    // An announcement everyone must see (web/notices.json "kind"), over everything.
    com.sukoon.app.ui.components.ForcedNotice()
    if (showGuide) HelpDialog(onClose = { showGuide = false; guideTopic = null }, onLink = { showGuide = false; guideTopic = null; openLink(it) }, startTopic = guideTopic)
    // An invite link tapped (sukoon://follow): follow in one tap, or sign in first and it comes back.
    val pendingInvite by appContainer.pendingInvite.collectAsStateWithLifecycle()
    val account by appContainer.sharing.supabase.session.collectAsStateWithLifecycle()
    var signingInForInvite by remember(pendingInvite) { mutableStateOf(false) }
    pendingInvite?.takeIf { account != null || !signingInForInvite }?.let { code ->
        com.sukoon.app.ui.sharing.FollowInviteDialog(
            code, signedIn = account != null, sharing = appContainer.sharing, watch = appContainer.followerWatch,
            onSignIn = { signingInForInvite = true; openYou(YouPage.PEOPLE) },
            onDone = { appContainer.pendingInvite.value = null },
        )
    }
    Scaffold(
        bottomBar = { SukoonBottomBar(navController, onYou = { openYou() }) },
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
                        if (missing.isNotEmpty() && !later) SetupBanner(missing, onFix = { openYou(YouPage.HELP) }, onLater = { later = true })
                        if (com.sukoon.app.BuildConfig.SELF_UPDATE) UpdateBanner(home.updates, onBackUp = { openYou(YouPage.REPORTS) })
                        FollowingHome(home.sharing, home.settings, onAddPerson = { openYou(YouPage.PEOPLE) }, modifier = Modifier.weight(1f))
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
                        modesOf = { home.modes.now() },
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
                // Read each time Now opens: it's arranged in You › Appearance › Now.
                val nowLayout = remember { home.settings.nowLayout }
                if (pickingStats) {
                    StatsPicker(chosenStats, home.healthConnect, onChange = { home.settings.homeStats = it; chosenStats = it }, onDismiss = { pickingStats = false })
                }
                val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                val brief by homeViewModel.brief.collectAsStateWithLifecycle()
                val toSensor = { openYou(YouPage.SENSOR) }
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
                // Beta: what the learning would change, worked out once each time Home opens.
                val doseNow = home.settings.doseSettings
                val learningReport by produceState<RatioLearner.Report?>(null) {
                    if (!doseNow.enabled) return@produceState
                    value = withContext(Dispatchers.Default) {
                        runCatching {
                            val since = System.currentTimeMillis() - Duration.ofDays(30).toMillis()
                            RatioLearner.report(repository.readingsSince(since).first(), home.logbookRepository.eventsSince(since).first(), doseNow.correctionFactor, ZoneId.systemDefault(), home.settings.insulinAction)
                        }.getOrNull()
                    }
                }
                var dismissedNudge by remember { mutableStateOf(home.settings.nudgeDismissed) }
                val nudge = learningReport?.takeIf { doseNow.enabled }?.let { RatioLearner.nudge(it, doseNow) }?.takeIf { it.key != dismissedNudge }
                val nudgeText = nudge?.let { n ->
                    if (n.slot == null) stringResource(R.string.nudge_factor, formatAmountLocalized(n.value))
                    else stringResource(R.string.nudge_ratio, slot(n.slot), formatAmountLocalized(n.value))
                }
                // Beta: a correction when you're above your range and haven't eaten in 2 hours.
                val recentEvents by remember { home.logbookRepository.eventsSince(System.currentTimeMillis() - Duration.ofHours(6).toMillis()) }.collectAsStateWithLifecycle(initialValue = emptyList())
                var showModes by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(todayRequest) { if (todayRequest) { showModes = true; todayRequest = false } }
                // ponytail: the newest 150 rows cover 2 hours at the 1-minute save interval (sparser saves cover more).
                val lastReadings by remember { repository.recentReadings(150) }.collectAsStateWithLifecycle(initialValue = emptyList())
                // A value under 70 in the last 2 hours: Home suggests no dose (the carbs since are treating it).
                val lowAt = com.sukoon.app.insulin.Dose.lowAt(
                    lastReadings.map { it.timestamp to it.glucoseMgDl } +
                        recentEvents.filter { it.logType == LogEventType.FINGERSTICK }.mapNotNull { e -> e.value?.let { Instant.ofEpochMilli(e.timestampMillis) to kotlin.math.round(it).toInt() } },
                    Instant.now(),
                )
                // The number to dose on: a fresh sensor reading, else a finger-prick from the last 15 min.
                val current = FingerPricks.current(latest, FingerPricks.of(recentEvents), Instant.now())
                val fresh = if (current?.prick == true) FingerPricks.FRESH else Dose.FRESH
                val correction = current?.let { r ->
                    val now = Instant.now()
                    Dose.correction(
                        settings = home.settings.doseSettings,
                        glucose = r.mgDl,
                        trend = r.trend,
                        readingAt = r.at,
                        fresh = fresh,
                        now = now,
                        top = TargetRange.high,
                        onBoard = insulinOnBoard,
                        lastCarbsAt = recentEvents.lastOrNull { it.logType == LogEventType.CARB && (it.value ?: 0.0) > 0 }?.let { Instant.ofEpochMilli(it.timestampMillis) },
                        slot = InsightEngine.slotFor(now.atZone(ZoneId.systemDefault()).hour),
                        lowAt = lowAt,
                        change = com.sukoon.app.modes.Modes.doseChange(home.modes.now(), now, null, ZoneId.systemDefault(), home.modes.afterWorkoutCut),
                        lastInsulinAt = recentEvents.lastOrNull { it.logType == LogEventType.INSULIN && (it.value ?: 0.0) > 0 }?.let { Instant.ofEpochMilli(it.timestampMillis) },
                    )
                }
                // Beta: a meal in the last hour with no insulin logged and none working.
                val mealDose = current?.let { r ->
                    val now = Instant.now()
                    recentEvents.lastOrNull { it.logType == LogEventType.CARB && !it.treatment && (it.value ?: 0.0) >= 10 }?.takeUnless { it.summary }?.let { meal ->
                        val mealAt = Instant.ofEpochMilli(meal.timestampMillis)
                        // A meal from another app is timed when it was logged, which can be hours after you ate (and dosed): look further back.
                        val lookBack = Duration.ofHours(if (meal.source != null && !meal.timeSet) 3 else 1).toMillis()
                        val insulinSince = recentEvents.any { it.logType == LogEventType.INSULIN && (it.mealId == meal.id || it.timestampMillis >= meal.timestampMillis - lookBack) }
                        Dose.mealDose(
                            settings = home.settings.doseSettings,
                            carbs = meal.value ?: 0.0,
                            mealAt = mealAt,
                            insulinSince = insulinSince,
                            glucose = r.mgDl,
                            trend = r.trend,
                            readingAt = r.at,
                            fresh = fresh,
                            now = now,
                            onBoard = insulinOnBoard,
                            slot = InsightEngine.slotFor(mealAt.atZone(ZoneId.systemDefault()).hour),
                            lowAt = lowAt,
                            change = com.sukoon.app.modes.Modes.doseChange(home.modes.now(), now, mealAt, ZoneId.systemDefault(), home.modes.afterWorkoutCut),
                        )?.let { meal to it }
                    }
                }
                // Finger-prick mode: when the next reminder to prick comes, asked again as the logbook changes.
                val nextCheck by produceState<Instant?>(null, homeState is com.sukoon.app.ui.home.HomeUiState.FingerPrick, recentEvents) {
                    value = if (homeState is com.sukoon.app.ui.home.HomeUiState.FingerPrick) runCatching { com.sukoon.app.reminders.Checks.nextPrick(context, home)?.at }.getOrNull() else null
                }
                if (askingForHelp) EmergencyActionsDialog(emergencyAlerts, latest, onDismiss = { askingForHelp = false })
                var viewing by remember { mutableStateOf<String?>(null) }
                viewing?.let { id ->
                    Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            FollowingHome(home.sharing, home.settings, onAddPerson = { viewing = null; openYou(YouPage.PEOPLE) }, personId = id)
                        }
                    }
                }
                Column {
                    // Wearing and following: the people followed sit above your own Home.
                    if (role == UserRole.BOTH) FollowingStrip(home.sharing) { viewing = it.id }
                    val app = (context.applicationContext as SukoonApp).container
                    var startDismissed by remember { mutableStateOf(app.settings.gettingStartedDismissed) }
                    val startSteps = startDone()
                    // All three ticked once: the card is gone for good; later problems show in the setup banner only.
                    if (!startDismissed && startSteps.all { it }) LaunchedEffect(Unit) {
                        app.settings.gettingStartedDismissed = true
                        startDismissed = true
                    }
                    // After onboarding's first reading: what it no longer asks; it stands in for the card and banner.
                    val finishing = com.sukoon.app.ui.help.FinishSetupCard(startSteps, ::openLink, onDismiss = {
                        app.settings.gettingStartedDismissed = true
                        startDismissed = true
                    })
                    val showStart = !startDismissed && !startSteps.all { it } && !finishing
                    // The banner or the card, never both.
                    if (missingSetup.isNotEmpty() && !setupLater && !showStart && !finishing) {
                        SetupBanner(missingSetup, onFix = { openYou(YouPage.HELP) }, onLater = { setupLater = true })
                    }
                    if (showStart) {
                        GettingStartedCard(
                            done = startSteps,
                            onStep = ::openLink,
                            onGuide = { showGuide = true },
                            onDismiss = {
                                app.settings.gettingStartedDismissed = true
                                startDismissed = true
                            },
                        )
                    }
                    val life by homeViewModel.sensorLife.collectAsStateWithLifecycle()
                    val source by home.sourceKind.collectAsStateWithLifecycle()
                    if (source == SourceKind.LIBRE_BLE && !rememberBluetoothOn()) BluetoothOffBanner { turnOnBluetooth(context) }
                    // What's on, as chips under Now's header; Today (the sliders button) switches things on.
                    val lastReadings by remember { repository.recentReadings(600) }.collectAsStateWithLifecycle(initialValue = emptyList())
                    val modesNow by home.modes.active.collectAsStateWithLifecycle()
                    val alarmsPaused by home.alarms.pausedUntil.collectAsStateWithLifecycle()
                    val goal by home.modes.goalFlow.collectAsStateWithLifecycle()
                    val supplyStock by home.supplies.stock.collectAsStateWithLifecycle()
                    val supplyPickup by home.supplies.pickup.collectAsStateWithLifecycle()
                    val supplySince = remember { System.currentTimeMillis() - Duration.ofDays(120).toMillis() }
                    val supplyEvents by remember { home.logbookRepository.eventsSince(supplySince) }.collectAsStateWithLifecycle(initialValue = emptyList())
                    // Steps so far today from Health Connect, every 5 minutes (null: not allowed or none).
                    val steps by produceState<Long?>(null) {
                        while (true) {
                            value = runCatching { home.healthConnect.today().steps }.getOrNull()
                            kotlinx.coroutines.delay(5 * 60_000L)
                        }
                    }
                    val lastBasal by remember { home.logbookRepository.latest(LogEventType.BASAL) }.collectAsStateWithLifecycle(initialValue = null)
                    val lastKetoneEvent = recentEvents.lastOrNull { it.logType == LogEventType.KETONE && it.value != null }
                    val lastKetones = lastKetoneEvent?.let { it.value!! to Instant.ofEpochMilli(it.timestampMillis) }
                    val ketonesDue = modesNow.sick && (lastKetones?.second ?: modesNow.sickSince!!) <= Instant.now().minus(com.sukoon.app.modes.Modes.KETONES_EVERY)
                    val chips = com.sukoon.app.ui.home.statusChips(
                        m = modesNow,
                        paused = alarmsPaused,
                        goal = com.sukoon.app.modes.Goals.targets(home.settings.profile, goal)?.takeIf { nowLayout.shows(com.sukoon.app.ui.home.NowPart.GOAL) }?.let { t ->
                            val gym = home.modes.gymToday()
                            val day = if (goal.gymDays.isEmpty()) t else t.copy(kcal = t.kcal?.let { com.sukoon.app.modes.Goals.dayKcal(it, goal, gym) })
                            day to (stats[com.sukoon.app.ui.home.HomeStat.PROTEIN] to stats[com.sukoon.app.ui.home.HomeStat.KCAL])
                        },
                        gymDay = if (goal.gymDays.isEmpty()) null else home.modes.gymToday(),
                        lowSupply = supplyStock.mapNotNull { (k, s) ->
                            s ?: return@mapNotNull null
                            com.sukoon.app.supplies.Supply.daysLeft(com.sukoon.app.supplies.Supply.remaining(s, supplyEvents, k), com.sukoon.app.supplies.Supply.perDay(supplyEvents, k, Instant.now(), java.time.ZoneId.systemDefault()))
                                ?.takeIf { it <= com.sukoon.app.supplies.Supply.LOW_DAYS }?.let { context.getString(com.sukoon.app.ui.settings.kindName(k)) to it }
                        },
                        pickup = com.sukoon.app.supplies.Supply.daysToPickup(supplyPickup, java.time.LocalDate.now())?.takeIf { it <= supplyPickup.remindDays }
                            ?.let { com.sukoon.app.supplies.SuppliesReminder.pickupLine(context, it, supplyPickup.next!!) },
                        onSupplies = { openYou(YouPage.SUPPLIES) },
                        newMeals = learningReport?.takeIf { doseNow.enabled && nudge == null }?.let { r ->
                            val since = home.settings.ratiosReviewedAt
                            r.meals.count { it.verdict == RatioLearner.Verdict.CLEAN && it.atMillis > since }.takeIf { it >= RatioLearner.MIN_POINTS }
                        },
                        onRatios = { home.requestedLearning.value = true; openYou(YouPage.INSULIN) },
                        steps = steps,
                        gap = if (source == SourceKind.LIBRE_BLE) com.sukoon.app.domain.metrics.MemoryGap.fillable(lastReadings, Instant.now()) else null,
                        ketonesDue = ketonesDue,
                        onResume = { scope.launch { home.alarms.resumeAll(); context.toast(context.getString(R.string.toast_alarms_back)) } },
                        onToday = { showModes = true },
                        onFitness = { openYou(YouPage.FITNESS) },
                        onFillGap = { home.requestedCheckSensor.value = true; openYou(YouPage.SENSOR) },
                        basal = com.sukoon.app.reminders.BasalReminder.overdue(listOfNotNull(lastBasal), home.settings.basalReminder, java.time.ZonedDateTime.now()),
                        onBasal = { home.requestedEntry.value = LogEventType.BASAL },
                    )
                    // Under the parent lock, pausing alarms asks for the PIN first.
                    var pinFor by remember { mutableStateOf<(() -> Unit)?>(null) }
                    val lockedAlarms = home.settings.parentPin != null && com.sukoon.app.platform.ParentLock.appliesTo(home.settings.profile.ageYears)
                    pinFor?.let { action ->
                        com.sukoon.app.ui.settings.PinDialog(setting = false, stored = home.settings.parentPin, onDone = { action(); pinFor = null }, onDismiss = { pinFor = null })
                    }
                    if (showModes) {
                        com.sukoon.app.ui.home.TodaySheet(
                            home.modes, home.alarms, latest?.glucoseMgDl, lastKetones,
                            askPin = if (lockedAlarms) { action -> pinFor = action } else null,
                            onLogKetones = { showModes = false; home.requestedEntry.value = LogEventType.KETONE },
                            onFitness = { showModes = false; openYou(YouPage.FITNESS) },
                            onGuide = { showModes = false; guideTopic = "modes"; showGuide = true },
                            onDismiss = { showModes = false },
                        )
                    }
                    (life as? SensorLife.Running)
                        ?.takeIf { nowLayout.shows(com.sukoon.app.ui.home.NowPart.SENSOR_ENDING) && Duration.between(Instant.now(), it.endsAt) <= Duration.ofHours(24) }
                        ?.let { SensorEndingBanner(it, onClick = toSensor) }
                    if (com.sukoon.app.BuildConfig.SELF_UPDATE) UpdateBanner(home.updates, onBackUp = { openYou(YouPage.REPORTS) })
                    HomeScreen(
                        state = homeState,
                        brief = brief,
                        onSync = syncMfp,
                        modifier = Modifier.weight(1f),
                        onPairSensor = toSensor,
                        onTroubleshoot = toSensor,
                        onTreated = { scope.launch { alarms.treated() } },
                        onSnooze = { scope.launch { alarms.acknowledge(AlarmType.LOW, home.settings.alarmSettings.lowSnoozeMinutes) } }, // the snooze you set, not a fixed 15
                        onAlertEmergencyContact = {
                            if (emergencyAlerts.contacts.isEmpty()) {
                                context.toast(context.getString(R.string.toast_emergency_no_contacts), long = true)
                                openYou(YouPage.PEOPLE)
                            } else {
                                askingForHelp = true
                            }
                        },
                        onAddFood = { pendingEntry = LogEventType.CARB; navController.navigateToTab(SukoonTab.TRENDS) },
                        onAddInsulin = { pendingEntry = LogEventType.INSULIN; navController.navigateToTab(SukoonTab.TRENDS) },
                        onPrick = { pendingEntry = LogEventType.FINGERSTICK; navController.navigateToTab(SukoonTab.TRENDS) },
                        nextCheck = nextCheck,
                        onModes = { showModes = true },
                        status = chips,
                        insulinOnBoard = insulinOnBoard,
                        correction = correction,
                        mealDose = mealDose,
                        onMealDose = { meal ->
                            pendingMealInsulin = meal.id
                            navController.navigateToTab(SukoonTab.TRENDS)
                        },
                        nudge = nudgeText,
                        onNudge = {
                            home.requestedLearning.value = true
                            openYou(YouPage.INSULIN)
                        },
                        onNudgeDismiss = {
                            home.settings.nudgeDismissed = nudge?.key
                            dismissedNudge = nudge?.key
                        },
                        stats = stats,
                        chosenStats = chosenStats,
                        onChooseStats = { pickingStats = true },
                        layout = nowLayout,
                        dosing = home.settings.doseSettings.enabled,
                        demo = source == SourceKind.SIMULATED,
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
                // The Guide as text, read once (~25 KB of assets), so Ask can answer questions about the app.
                val guideContext = LocalContext.current
                val askGuide = remember { runCatching { com.sukoon.app.ui.help.GuideContent.load(guideContext).asText() }.getOrDefault("") }
                val askViewModel: AskViewModel = viewModel(
                    factory = AskViewModel.factory(container.glucoseRepository, container.logbookRepository, container.gemini, doseSettings = { container.settings.doseSettings }, profile = { container.settings.profile }, goal = { container.modes.goalLine(container.settings.profile) }, guide = { askGuide }) { container.settings.insulinAction },
                )
                val askState by askViewModel.uiState.collectAsStateWithLifecycle()
                val insightsViewModel: InsightsViewModel = viewModel(
                    factory = InsightsViewModel.factory(container.glucoseRepository, container.logbookRepository, container.settings) { container.calibration.enabled },
                )
                val insightsState by insightsViewModel.uiState.collectAsStateWithLifecycle()
                val reportViewModel: ReportViewModel = viewModel(factory = ReportViewModel.factory(container.glucoseRepository))
                val reportState by reportViewModel.state.collectAsStateWithLifecycle()
                val trendsContext = LocalContext.current
                TrendsHub(
                    requestedSubTab = trendsRequest,
                    onSubTabHandled = { trendsRequest = null },
                    onInsightAction = { action ->
                        when (action) {
                            InsightAction.TURN_ON_GOING_LOW -> {
                                container.settings.alarmSettings = container.settings.alarmSettings.copy(goingLowEnabled = true)
                                trendsContext.toast(trendsContext.getString(R.string.in2_toast_going_low))
                                insightsViewModel.settingsChanged()
                            }
                            InsightAction.TURN_ON_WORKOUT_CHECK -> {
                                container.settings.afterWorkoutCheck = true
                                trendsContext.toast(trendsContext.getString(R.string.in2_toast_workout))
                                insightsViewModel.settingsChanged()
                            }
                            InsightAction.OPEN_DOSE_SETUP, InsightAction.OPEN_SITES -> openYou(YouPage.INSULIN)
                            InsightAction.OPEN_LEARNING -> openLink("learning")
                            InsightAction.OPEN_CALIBRATION -> openYou(YouPage.SENSOR)
                            InsightAction.LOG_INSULIN -> container.requestedEntry.value = LogEventType.INSULIN
                            InsightAction.LOG_MEAL -> container.requestedEntry.value = LogEventType.CARB
                            InsightAction.REPORT, InsightAction.LOOK_AT -> Unit // TrendsHub switches tabs itself
                        }
                    },
                    graphState = graphState,
                    onSelectRange = graphViewModel::selectRange,
                    onGraphDay = graphViewModel::showDay,
                    onLogbookDay = logbookViewModel::showDay,
                    logbookState = logbookState,
                    onSaveEntry = logbookViewModel::save,
                    onUndoEntry = logbookViewModel::undo,
                    onUpdateEvent = logbookViewModel::updateEvent,
                    onDeleteEvent = logbookViewModel::deleteEvent,
                    onEstimateCarbs = logbookViewModel::estimateCarbs,
                    onEntryPhoto = logbookViewModel::setPhotos,
                    askState = askState,
                    hasAiKey = askViewModel.hasKey,
                    onAsk = askViewModel::ask,
                    onClearAsk = askViewModel::clear,
                    onOpenSettings = { openYou(YouPage.APPS) }, // the AI assistant's key lives in Apps & data
                    insightsState = insightsState,
                    onAcknowledgeInsights = insightsViewModel::acknowledge,
                    pendingEntry = pendingEntry,
                    onPendingEntryHandled = { pendingEntry = null },
                    onSync = syncMfp,
                    reportState = reportState,
                    onSelectReportDays = reportViewModel::selectDays,
                    reportName = container.settings.emergency.yourName,
                    doseSettings = { container.settings.doseSettings },
                    pendingMealInsulin = pendingMealInsulin,
                    onPendingMealInsulinHandled = { pendingMealInsulin = null },
                    mealRecord = container.healthConnect::mealRecord,
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
                val lastWeighIn by container.lastWeighIn.collectAsStateWithLifecycle()
                var profile by remember(lastWeighIn) { mutableStateOf(container.settings.profile) }
                var parentPin by remember { mutableStateOf(container.settings.parentPin) }
                var targetHigh by remember { mutableIntStateOf(container.settings.targetHigh) }
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
                        container.selectSource(SourceKind.NONE)
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
                    onOpenGuideTopic = { guideTopic = it; showGuide = true },
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
                    openPage = youOpen,
                    openLearning = container.requestedLearning.collectAsStateWithLifecycle().value,
                    onOpenedLearning = { container.requestedLearning.value = false },
                    parentPin = parentPin,
                    onParentPin = { pin ->
                        container.settings.parentPin = pin
                        parentPin = pin
                    },
                    profile = profile,
                    onProfile = { changed ->
                        container.settings.profile = changed
                        profile = changed
                    },
                    targetHigh = targetHigh,
                    onTargetHigh = { high ->
                        // A high alarm sitting at the old top of the range moves with it; one set apart stays.
                        val alarms = container.settings.alarmSettings
                        if (alarms.highMgDl == container.settings.targetHigh) {
                            container.settings.alarmSettings = alarms.copy(highMgDl = high).sanitized()
                            alarmSettings = container.settings.alarmSettings
                        }
                        container.settings.targetHigh = high
                        targetHigh = container.settings.targetHigh
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
                    onLink = ::openLink,
                )
            }
        }
    }
}

@Composable
private fun SukoonBottomBar(navController: NavHostController, onYou: () -> Unit) {
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
                .padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            SukoonTab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                BottomBarItem(
                    label = stringResource(tab.labelRes),
                    icon = tab.icon,
                    iconOn = tab.iconOn,
                    selected = selected,
                    onClick = {
                        // You always opens on its hub, even when it's the tab already open.
                        if (tab == SukoonTab.YOU) onYou() else if (!selected) navController.navigateToTab(tab)
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
private fun BottomBarItem(label: String, @DrawableRes icon: Int, @DrawableRes iconOn: Int, selected: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // The selected tab: a sage wash behind a filled icon; the others stay quiet outlines.
        val wash by animateColorAsState(if (selected) SageMist else Color.Transparent, tween(Motion.BASE, easing = Motion.Standard), label = "navWash")
        Box(Modifier.size(width = 56.dp, height = 30.dp).clip(CircleShape).background(wash), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (selected) iconOn else icon),
                contentDescription = null,
                tint = if (selected) SageDeep else ink.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 11.sp,
            color = if (selected) ink else ink.copy(alpha = 0.6f),
        )
    }
}

/** What a follower-only phone needs for alerts to reach it (no sensor, no emergency texts). */
private val FOLLOWER_SETUP = setOf(SetupItem.NOTIFICATIONS, SetupItem.FULL_SCREEN, SetupItem.OVERLAY, SetupItem.BATTERY)
