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
import com.sukoon.app.ui.graph.GraphViewModel
import com.sukoon.app.ui.home.HomeScreen
import com.sukoon.app.ui.home.HomeViewModel
import com.sukoon.app.ui.logbook.LogbookViewModel
import com.sukoon.app.ui.theme.Sage

/**
 * Top-level navigation, per the shipped design's 3-tab bottom bar (Now / Trends / You) — not the
 * 5 tabs the earlier plan assumed. The design consolidates: Logbook + Insights live under Trends;
 * Sharing + Settings under You. Those sub-screens (A3/A4/A9/A10) become nested destinations of
 * these tabs as they land; for now Trends/You are placeholders.
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
                HomeScreen(state = homeState)
            }
            composable(SukoonTab.TRENDS.route) {
                // Hub over Graph (A3) + Logbook (A4); Insights (A9) slots in as a third sub-tab later.
                val container = (LocalContext.current.applicationContext as SukoonApp).container
                val graphViewModel: GraphViewModel = viewModel(
                    factory = GraphViewModel.factory(container.glucoseRepository, container.logbookRepository),
                )
                val graphState by graphViewModel.uiState.collectAsStateWithLifecycle()
                val logbookViewModel: LogbookViewModel = viewModel(factory = LogbookViewModel.factory(container.logbookRepository))
                val logbookState by logbookViewModel.uiState.collectAsStateWithLifecycle()
                TrendsHub(
                    graphState = graphState,
                    onSelectRange = graphViewModel::selectRange,
                    logbookState = logbookState,
                    onQuickLog = logbookViewModel::log,
                    onUpdateEvent = logbookViewModel::updateEvent,
                    onDeleteEvent = logbookViewModel::deleteEvent,
                )
            }
            composable(SukoonTab.YOU.route) { PlaceholderScreen(R.string.home_nav_you) }
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
                        if (!selected) {
                            navController.navigate(tab.route) {
                                // Preserve each tab's state across switches; avoid stacking duplicates.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        }
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

/** Temporary stub for tabs whose screens land in later milestones (Trends → A3/A4/A9, You → A10). */
@Composable
private fun PlaceholderScreen(@StringRes titleRes: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(titleRes),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.placeholder_coming_soon),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        )
    }
}
