package com.sukoon.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.platform.Notices
import com.sukoon.app.platform.Updates
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.widget.arrow
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * An announcement Kai wants everyone to see (web/notices.json, "kind"): a must-read stays until
 * "Got it"; a forced update can't be closed at all. Both show the latest reading at the top, so the
 * number is never hidden; alarms are separate and keep sounding.
 */
@Composable
fun ForcedNotice() {
    val context = LocalContext.current
    val app = (context.applicationContext as? SukoonApp)?.container ?: return // previews
    val notice by app.notices.urgent.collectAsState()
    val shown = notice ?: return
    val update = shown.kind == Notices.Notice.Kind.UPDATE
    val arabic = context.resources.configuration.locales[0].language == "ar"
    val latest by app.glucoseRepository.latestReading.collectAsState(initial = null)
    val updateState by app.updates.state.collectAsState()
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // The number stays in view.
                latest?.let { r ->
                    val minutes = Duration.between(r.timestamp, Instant.now()).toMinutes().coerceAtLeast(0)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(String.format(Locale.getDefault(), "%d", r.glucoseMgDl), fontFamily = HeadlineSerifFontFamily, fontSize = 30.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text(r.trend.arrow, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text(stringResource(R.string.forced_reading, minutes.toInt()), fontSize = 13.sp, color = CaptionMuted)
                    }
                }
                Spacer(Modifier.heightIn(min = 24.dp))
                Box(Modifier.size(56.dp).clip(CircleShape).background(Sage), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_stat_sukoon), null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text(stringResource(R.string.forced_from), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = SageDeep)
                Text(shown.titleIn(arabic), fontFamily = HeadlineSerifFontFamily, fontSize = 30.sp, lineHeight = 36.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(shown.bodyIn(arabic), fontSize = 16.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.heightIn(min = 16.dp))
                if (update) {
                    val busy = updateState is Updates.State.Checking || updateState is Updates.State.Downloading
                    Button(stringResource(if (busy) R.string.forced_updating else R.string.forced_update)) {
                        if (busy) return@Button
                        scope.launch {
                            (app.updates.state.value as? Updates.State.Available)?.release?.let { app.updates.update(it); return@launch }
                            app.updates.check()
                            (app.updates.state.value as? Updates.State.Available)?.release?.let { app.updates.update(it) }
                                ?: shown.link?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }
                    if (updateState is Updates.State.Failed) Text(stringResource(R.string.forced_update_failed), fontSize = 13.sp, color = CaptionMuted)
                    Text(stringResource(R.string.forced_update_note), fontSize = 12.5.sp, color = CaptionMuted)
                } else {
                    shown.link?.let { link ->
                        Text(
                            stringResource(R.string.forced_read_more), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                .heightIn(min = 44.dp).padding(vertical = 12.dp),
                        )
                    }
                    Button(stringResource(R.string.forced_got_it)) { app.notices.acknowledge(shown.id) }
                }
            }
        }
    }
}

@Composable
private fun Button(label: String, onClick: () -> Unit) = Text(
    label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sage).clickable(onClick = onClick).heightIn(min = 54.dp).padding(16.dp),
)
