package com.sukoon.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sukoon.app.R
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.produceState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.widget.GlucoseWidget.Companion.toWidgetOptions

/**
 * You → Home-screen widgets. "Add widget" asks the launcher to pin one (no hunting through the
 * widget picker), and each placed widget gets an Edit row that opens its settings from inside the
 * app — which works even on launchers whose own "reconfigure" button is blocked by Android 14's
 * background-activity-launch rules.
 */
@Composable
fun WidgetsCard() {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    var placed by remember { mutableStateOf(emptyList<Int>()) }
    var making by remember { mutableStateOf<Int?>(null) } // -1: a new one; else the widget being edited

    // Re-read on every resume: the user adds/removes widgets on the home screen, outside the app.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            placed = GlucoseWidget.RECEIVERS.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.widgets_body_maker), fontSize = 13.sp, lineHeight = 18.sp, color = CaptionMuted)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Sage)
                .clickable { making = -1 },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.widgets_make), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        if (placed.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, outline(), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                placed.forEachIndexed { index, id ->
                    val options by produceState<WidgetOptions?>(null, id) {
                        value = runCatching { getAppWidgetState(context, PreferencesGlanceStateDefinition, GlanceAppWidgetManager(context).getGlanceIdBy(id)).toWidgetOptions() }.getOrNull()
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(options?.let { styleName(it.style) } ?: stringResource(R.string.widgets_item, index + 1), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            sizeOfPlaced(context, id)?.let { Text(sizeName(it), fontSize = 12.5.sp, color = CaptionMuted) }
                        }
                        Box(
                            Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, outline(), RoundedCornerShape(20.dp))
                                .clickable { making = id }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.widgets_edit), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    if (index < placed.lastIndex) HorizontalDivider(color = outline().copy(alpha = 0.08f))
                }
            }
        }
    }

    making?.let { which ->
        Dialog(onDismissRequest = { making = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            WidgetMaker(editId = which.takeIf { it >= 0 }) {
                making = null
                placed = GlucoseWidget.RECEIVERS.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
            }
        }
    }
}
