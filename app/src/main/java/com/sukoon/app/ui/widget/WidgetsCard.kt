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
    val provider = remember { ComponentName(context, GlucoseWidgetReceiver::class.java) }
    var placed by remember { mutableStateOf(emptyList<Int>()) }

    // Re-read on every resume: the user adds/removes widgets on the home screen, outside the app.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { placed = manager.getAppWidgetIds(provider).toList() }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.widgets_body), fontSize = 12.5.sp, color = CaptionMuted)
        placed.forEachIndexed { index, id ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.widgets_item, index + 1),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.widgets_edit),
                    color = Sage,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            context.startActivity(
                                Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        if (manager.isRequestPinAppWidgetSupported) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Sage)
                    .clickable { manager.requestPinAppWidget(provider, null, null) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.widgets_add), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        } else {
            Text(stringResource(R.string.widgets_add_manually), fontSize = 12.sp, color = CaptionMuted)
        }
    }
}
