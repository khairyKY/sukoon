package com.sukoon.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.sukoon.app.R
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SukoonTheme
import com.sukoon.app.ui.widget.GlucoseWidget.Companion.toWidgetOptions
import kotlinx.coroutines.launch

/**
 * Per-widget settings: graph range (or none), details, background. Shown when a widget is added
 * and, on Android 12+, from the launcher's "reconfigure" — each placed widget has its own options.
 * Size isn't a setting: the user drags the widget to any size and the layout follows.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result) // backing out of the first configure cancels adding the widget

        // Exported for the launcher, so only accept ids that are actually our widgets.
        if (AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.packageName != packageName) {
            finish()
            return
        }
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)

        lifecycleScope.launch {
            val current = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId).toWidgetOptions()
            setContent {
                SukoonTheme {
                    WidgetConfigScreen(current) { chosen ->
                        lifecycleScope.launch {
                            GlucoseWidget.saveOptions(this@WidgetConfigActivity, glanceId, chosen)
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetConfigScreen(initial: WidgetOptions, onSave: (WidgetOptions) -> Unit) {
    var options by remember { mutableStateOf(initial) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.widget_config_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.widget_config_resize_hint), fontSize = 12.5.sp, color = CaptionMuted)

        Label(stringResource(R.string.widget_config_graph))
        Chips(
            choices = WidgetOptions.GRAPH_CHOICES,
            selected = options.graphHours,
            label = { if (it == 0) stringResource(R.string.widget_config_graph_off) else stringResource(R.string.widget_config_hours, it) },
        ) { options = options.copy(graphHours = it) }

        Label(stringResource(R.string.widget_config_background))
        Chips(
            choices = WidgetBackground.entries,
            selected = options.background,
            label = {
                stringResource(
                    when (it) {
                        WidgetBackground.AUTO -> R.string.widget_config_bg_auto
                        WidgetBackground.LIGHT -> R.string.widget_config_bg_light
                        WidgetBackground.DARK -> R.string.widget_config_bg_dark
                        WidgetBackground.CLEAR -> R.string.widget_config_bg_clear
                    },
                )
            },
        ) { options = options.copy(background = it) }

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.widget_config_details), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.widget_config_details_body), fontSize = 12.sp, color = CaptionMuted)
            }
            Switch(
                checked = options.showDetails,
                onCheckedChange = { options = options.copy(showDetails = it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Sage),
            )
        }

        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Sage)
                .clickable { onSave(options) }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.widget_config_save), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(22.dp))
    Text(text.uppercase(), fontSize = 10.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(choices: List<T>, selected: T, label: @Composable (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            val isSelected = choice == selected
            Text(
                label(choice),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isSelected) Modifier.background(Sage)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    )
                    .clickable { onPick(choice) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}
