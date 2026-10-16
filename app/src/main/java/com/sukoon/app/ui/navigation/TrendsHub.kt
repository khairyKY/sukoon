package com.sukoon.app.ui.navigation

import com.sukoon.app.health.HealthConnectSync
import com.sukoon.app.insulin.DoseSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.sukoon.app.insights.InsightAction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.ai.CarbEstimate
import com.sukoon.app.ui.ai.AskScreen
import com.sukoon.app.ui.ai.AskUiState
import com.sukoon.app.ui.insights.InsightsScreen
import com.sukoon.app.ui.insights.InsightsUiState
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.ui.graph.GraphRange
import com.sukoon.app.ui.graph.GraphScreen
import com.sukoon.app.ui.graph.GraphUiState
import com.sukoon.app.ui.logbook.LogbookScreen
import com.sukoon.app.ui.logbook.LogbookUiState
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.reports.ReportScreen
import com.sukoon.app.ui.reports.ReportUiState
import java.time.Instant
import com.sukoon.app.ui.logbook.EntryDraft
import kotlinx.coroutines.Deferred

enum class TrendsSubTab { GRAPH, LOGBOOK, INSIGHTS, REPORT, ASK }

/**
 * The Trends tab's content (A1 comment in MainScaffold): a hub over Graph (A3) and Logbook (A4),
 * switched by a small in-tab toggle. Not a nested Compose-Navigation graph — there's no back-stack
 * or deep-link need yet (e.g. "tap a graph pin → jump to its Logbook entry"); add real sub-routes
 * if/when that lands. Ask (AI chat about these same readings + logs) is the third option; Insights
 * (A9) slots in here too.
 */
@Composable
fun TrendsHub(
    graphState: GraphUiState,
    onSelectRange: (GraphRange) -> Unit,
    logbookState: LogbookUiState,
    onSaveEntry: (EntryDraft) -> Deferred<List<Long>>,
    onUndoEntry: (List<Long>) -> Unit,
    onUpdateEvent: (EventEntity) -> Unit,
    onDeleteEvent: (EventEntity) -> Unit,
    onEstimateCarbs: suspend (String, List<ByteArray>) -> CarbEstimate,
    onEntryPhoto: (Long, List<ByteArray>) -> Unit,
    askState: AskUiState,
    hasAiKey: Boolean,
    onAsk: (String) -> Unit,
    onClearAsk: () -> Unit,
    onOpenSettings: () -> Unit,
    insightsState: InsightsUiState,
    onAcknowledgeInsights: () -> Unit,
    pendingEntry: LogEventType?,
    onPendingEntryHandled: () -> Unit,
    onSync: (suspend () -> String)? = null,
    reportState: ReportUiState,
    onSelectReportDays: (Int) -> Unit,
    reportName: String,
    modifier: Modifier = Modifier,
    doseSettings: () -> DoseSettings = { DoseSettings() },
    pendingMealInsulin: Long? = null,
    onPendingMealInsulinHandled: () -> Unit = {},
    mealRecord: suspend (Long) -> HealthConnectSync.MealRecord? = { null },
    onGraphDay: (java.time.LocalDate?) -> Unit = {},
    onLogbookDay: (java.time.LocalDate?) -> Unit = {},
    /** A sub-tab asked for from elsewhere (a guide link); handled once. */
    requestedSubTab: TrendsSubTab? = null,
    onSubTabHandled: () -> Unit = {},
    /** Insights' buttons that leave Trends (settings, You pages, new entries). */
    onInsightAction: (InsightAction) -> Unit = {},
) {
    var subTab by rememberSaveable { mutableStateOf(TrendsSubTab.GRAPH) }
    if (pendingEntry != null || pendingMealInsulin != null) subTab = TrendsSubTab.LOGBOOK
    LaunchedEffect(requestedSubTab) {
        requestedSubTab?.let { subTab = it; onSubTabHandled() }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SegmentedTabs(subTab) { subTab = it }
        when (subTab) {
            TrendsSubTab.GRAPH -> GraphScreen(state = graphState, onSelectRange = onSelectRange, modifier = Modifier.weight(1f), onDay = onGraphDay)
            TrendsSubTab.LOGBOOK -> LogbookScreen(
                state = logbookState,
                onSaveEntry = onSaveEntry,
                onUndoEntry = onUndoEntry,
                onUpdateEvent = onUpdateEvent,
                onDay = onLogbookDay,
                onDeleteEvent = onDeleteEvent,
                modifier = Modifier.weight(1f),
                onEstimateCarbs = onEstimateCarbs,
                onEntryPhoto = onEntryPhoto,
                onSync = onSync,
                openNewEntry = pendingEntry,
                onOpenedEntry = onPendingEntryHandled,
                doseSettings = doseSettings,
                openInsulinForMeal = pendingMealInsulin,
                onOpenedInsulinForMeal = onPendingMealInsulinHandled,
                mealRecord = mealRecord,
            )
            TrendsSubTab.INSIGHTS -> InsightsScreen(
                insightsState,
                onAcknowledgeInsights,
                onAction = { action ->
                    when (action) {
                        // "Look at it": the graph over the same 14 days the finding comes from.
                        InsightAction.LOOK_AT -> { onSelectRange(GraphRange.D14); subTab = TrendsSubTab.GRAPH }
                        InsightAction.REPORT -> subTab = TrendsSubTab.REPORT
                        else -> onInsightAction(action)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            TrendsSubTab.REPORT -> ReportScreen(reportState, reportName, onSelectReportDays, Modifier.weight(1f))
            TrendsSubTab.ASK -> AskScreen(
                state = askState,
                hasKey = hasAiKey,
                onAsk = onAsk,
                onClear = onClearAsk,
                onOpenSettings = onOpenSettings,
                insightFinding = insightsState.digest?.oneThing,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Graph · Logbook · Insights · Report · ✦ Ask (Redesign Canvas §8): one track, the chosen segment raised.
 * A long name wraps to two lines (Arabic on a narrow phone) rather than being cut; TalkBack hears tabs.
 */
@Composable
private fun SegmentedTabs(selected: TrendsSubTab, onSelect: (TrendsSubTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
            .padding(3.dp),
    ) {
        TrendsSubTab.entries.forEach { tab ->
            val on = tab == selected
            Row(
                Modifier
                    .weight(if (tab == TrendsSubTab.INSIGHTS || tab == TrendsSubTab.LOGBOOK) 1.12f else 1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { onSelect(tab) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab == TrendsSubTab.ASK) {
                    Icon(painterResource(R.drawable.ic_sparkle), null, tint = if (on) Sage else CaptionMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    stringResource(
                        when (tab) {
                            TrendsSubTab.GRAPH -> R.string.graph_title
                            TrendsSubTab.LOGBOOK -> R.string.logbook_title
                            TrendsSubTab.INSIGHTS -> R.string.insights_tab
                            TrendsSubTab.REPORT -> R.string.report_tab
                            TrendsSubTab.ASK -> R.string.ask_tab
                        },
                    ),
                    fontSize = 12.5.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onBackground else CaptionMuted,
                )
            }
        }
    }
}

/** A small toggle chip (the followed person's Home and Trends). */
@Composable
internal fun SubTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (selected) {
                    Modifier.background(Sage)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else CaptionMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 12.sp,
        )
    }
}
