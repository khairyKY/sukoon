package com.sukoon.app.ui.navigation

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

private enum class TrendsSubTab { GRAPH, LOGBOOK, INSIGHTS, REPORT, ASK }

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
    onEstimateCarbs: suspend (String, ByteArray?) -> CarbEstimate,
    onEntryPhoto: (Long, ByteArray?) -> Unit,
    askState: AskUiState,
    hasAiKey: Boolean,
    onAsk: (String) -> Unit,
    onClearAsk: () -> Unit,
    onOpenSettings: () -> Unit,
    insightsState: InsightsUiState,
    onAcknowledgeInsights: () -> Unit,
    pendingEntry: LogEventType?,
    onPendingEntryHandled: () -> Unit,
    reportState: ReportUiState,
    onSelectReportDays: (Int) -> Unit,
    reportName: String,
    modifier: Modifier = Modifier,
) {
    var subTab by rememberSaveable { mutableStateOf(TrendsSubTab.GRAPH) }
    if (pendingEntry != null) subTab = TrendsSubTab.LOGBOOK

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubTabChip(stringResource(R.string.graph_title), subTab == TrendsSubTab.GRAPH) { subTab = TrendsSubTab.GRAPH }
            SubTabChip(stringResource(R.string.logbook_title), subTab == TrendsSubTab.LOGBOOK) { subTab = TrendsSubTab.LOGBOOK }
            SubTabChip(stringResource(R.string.insights_tab), subTab == TrendsSubTab.INSIGHTS) { subTab = TrendsSubTab.INSIGHTS }
            SubTabChip(stringResource(R.string.report_tab), subTab == TrendsSubTab.REPORT) { subTab = TrendsSubTab.REPORT }
            SubTabChip(stringResource(R.string.ask_tab), subTab == TrendsSubTab.ASK) { subTab = TrendsSubTab.ASK }
        }
        when (subTab) {
            TrendsSubTab.GRAPH -> GraphScreen(state = graphState, onSelectRange = onSelectRange, modifier = Modifier.weight(1f))
            TrendsSubTab.LOGBOOK -> LogbookScreen(
                state = logbookState,
                onSaveEntry = onSaveEntry,
                onUndoEntry = onUndoEntry,
                onUpdateEvent = onUpdateEvent,
                onDeleteEvent = onDeleteEvent,
                modifier = Modifier.weight(1f),
                onEstimateCarbs = onEstimateCarbs,
                onEntryPhoto = onEntryPhoto,
                openNewEntry = pendingEntry,
                onOpenedEntry = onPendingEntryHandled,
            )
            TrendsSubTab.INSIGHTS -> InsightsScreen(insightsState, onAcknowledgeInsights, Modifier.weight(1f))
            TrendsSubTab.REPORT -> ReportScreen(reportState, reportName, onSelectReportDays, Modifier.weight(1f))
            TrendsSubTab.ASK -> AskScreen(
                state = askState,
                hasKey = hasAiKey,
                onAsk = onAsk,
                onClear = onClearAsk,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SubTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
