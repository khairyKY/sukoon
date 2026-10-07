package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukoon.app.BuildConfig
import com.sukoon.app.R
import com.sukoon.app.platform.Updates
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import kotlinx.coroutines.launch

/** You → Help & about: this version, "Check for updates", and the update's progress and buttons. */
@Composable
fun UpdatesSection(updates: Updates, onBackUp: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state by updates.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, outline(), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.update_this_version, BuildConfig.VERSION_NAME), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            if (state !is Updates.State.Downloading) {
                Button(stringResource(if (state is Updates.State.Checking) R.string.update_checking else R.string.update_check), filled = false) {
                    scope.launch { updates.check() }
                }
            }
        }
        UpdateBody(updates, state, onBackUp)
    }
}

/** On Now: the dismissable notice that a newer Sukoon is out (or its download). */
@Composable
fun UpdateBanner(updates: Updates, onBackUp: () -> Unit) {
    val state by updates.state.collectAsStateWithLifecycle()
    val dismissed by updates.dismissed.collectAsStateWithLifecycle()
    val release = when (val s = state) {
        is Updates.State.Available -> s.release
        is Updates.State.Downloading -> s.release
        is Updates.State.Ready -> s.release
        is Updates.State.NeedsReinstall -> s.release
        else -> null
    } ?: return
    if (state is Updates.State.Available && dismissed == release.version) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(SageMist).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.update_out, release.version), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep, modifier = Modifier.weight(1f))
            if (state is Updates.State.Available) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable { updates.dismiss(release) }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.update_dismiss), tint = SageDeep, modifier = Modifier.size(18.dp))
                }
            }
        }
        UpdateBody(updates, state, onBackUp)
    }
}

@Composable
private fun UpdateBody(updates: Updates, state: Updates.State, onBackUp: () -> Unit) {
    when (state) {
        Updates.State.Idle, Updates.State.Checking -> Unit
        Updates.State.UpToDate -> Text(stringResource(R.string.update_up_to_date), fontSize = 13.sp, color = CaptionMuted)
        is Updates.State.Available -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(stringResource(R.string.update_now)) { updates.update(state.release) }
            Button(stringResource(R.string.update_whats_new), filled = false) { updates.openPage(state.release) }
        }
        is Updates.State.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()), fontSize = 13.sp, color = CaptionMuted)
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), color = Sage)
        }
        is Updates.State.Ready -> Button(stringResource(R.string.update_install)) { updates.install(state.file) }
        is Updates.State.NeedsReinstall -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.update_new_key), fontSize = 13.sp, lineHeight = 18.sp, color = PillHighText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(stringResource(R.string.backup_make), onClick = onBackUp)
                Button(stringResource(R.string.update_get_it), filled = false) { updates.openPage(state.release) }
            }
        }
        is Updates.State.Failed -> Text(stringResource(R.string.update_failed, state.message), fontSize = 13.sp, color = PillLowText)
    }
}

@Composable
private fun Button(label: String, filled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(if (filled) Modifier.background(Sage) else Modifier.border(1.dp, outline(), RoundedCornerShape(20.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Color.White else MaterialTheme.colorScheme.onBackground)
    }
}
