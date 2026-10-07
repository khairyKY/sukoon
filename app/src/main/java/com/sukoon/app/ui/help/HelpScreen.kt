package com.sukoon.app.ui.help

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sukoon.app.R
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily

/** The guide, one topic per row: tap to open. Order follows how a new user meets the app. */
private val TOPICS = listOf(
    R.string.help_sensor_title to R.string.help_sensor_body,
    R.string.help_home_title to R.string.help_home_body,
    R.string.help_alarms_title to R.string.help_alarms_body,
    R.string.help_emergency_title to R.string.help_emergency_body,
    R.string.help_logbook_title to R.string.help_logbook_body,
    R.string.help_trends_title to R.string.help_trends_body,
    R.string.help_widgets_title to R.string.help_widgets_body,
    R.string.help_sources_title to R.string.help_sources_body,
    R.string.help_mfp_title to R.string.help_mfp_body,
    R.string.help_reminders_title to R.string.help_reminders_body,
    R.string.help_sites_title to R.string.help_sites_body,
    R.string.help_sharing_title to R.string.help_sharing_body,
    R.string.help_data_title to R.string.help_data_body,
    R.string.help_calibration_title to R.string.help_calibration_body,
    R.string.help_backup_title to R.string.help_backup_body,
    R.string.help_reliability_title to R.string.help_reliability_body,
    R.string.help_safety_title to R.string.help_safety_body,
)

/** The user guide, full screen. */
@Composable
fun HelpDialog(onClose: () -> Unit) {
    var open by rememberSaveable { mutableIntStateOf(-1) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.help_title),
                        fontFamily = HeadlineSerifFontFamily,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose) { Text("✕", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground) }
                }
                Text(stringResource(R.string.help_intro), fontSize = 14.sp, lineHeight = 20.sp, color = CaptionMuted)
                Spacer(Modifier.height(16.dp))
                TOPICS.forEachIndexed { i, (title, body) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { open = if (open == i) -1 else i }
                            .padding(16.dp)
                            .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                            Text(if (open == i) "−" else "+", fontSize = 18.sp, color = CaptionMuted)
                        }
                        if (open == i) Text(stringResource(body), fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}
