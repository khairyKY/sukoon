package com.sukoon.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.sukoon.app.R
import com.sukoon.app.health.HealthConnectSync
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import kotlinx.coroutines.launch

/** You → Health Connect: MyFitnessPal (and other apps') meals in, glucose readings out. */
@Composable
fun HealthConnectSection(sync: HealthConnectSync) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = remember { sync.status() }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var checks by remember { mutableIntStateOf(0) }
    var importMeals by remember { mutableStateOf(sync.importMeals) }
    var importActivity by remember { mutableStateOf(sync.importActivity) }
    var shareGlucose by remember { mutableStateOf(sync.shareGlucose) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(checks) { granted = runCatching { sync.granted() }.getOrDefault(emptySet()) }

    fun syncNow() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = sync.sync()
                context.toast(context.getString(R.string.toast_hc_synced, result.mealsAdded, result.mealsUpdated, result.readingsShared), long = true)
            } catch (e: Exception) {
                context.toast(context.getString(R.string.toast_hc_failed, e.message ?: e.javaClass.simpleName), long = true)
            } finally {
                busy = false
            }
        }
    }
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { result ->
        checks++
        if (sync.readMeals in result || sync.writeGlucose in result) syncNow() else context.toast(context.getString(R.string.toast_hc_denied))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.hc_body), fontSize = 12.5.sp, color = CaptionMuted)
        when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> Text(stringResource(R.string.hc_unavailable), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                Text(stringResource(R.string.hc_update), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                Pill(stringResource(R.string.hc_update_button)) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")))
                    }
                }
            }
            else -> if (sync.readMeals !in granted && sync.writeGlucose !in granted) {
                Pill(stringResource(R.string.hc_connect)) { request.launch(sync.wantedPermissions()) }
                Text(stringResource(R.string.hc_mfp_hint), fontSize = 12.sp, color = CaptionMuted)
            } else {
                Toggle(stringResource(R.string.hc_import), stringResource(R.string.hc_import_body), importMeals) {
                    importMeals = it
                    sync.importMeals = it
                    if (it && sync.readMeals !in granted) request.launch(sync.wantedPermissions())
                }
                Toggle(stringResource(R.string.hc_activity), stringResource(R.string.hc_activity_body), importActivity) {
                    importActivity = it
                    sync.importActivity = it
                    if (it && sync.readActivity !in granted) request.launch(sync.wantedPermissions())
                }
                Toggle(stringResource(R.string.hc_export), stringResource(R.string.hc_export_body), shareGlucose) {
                    shareGlucose = it
                    sync.shareGlucose = it
                    if (it && sync.writeGlucose !in granted) request.launch(sync.wantedPermissions())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(stringResource(if (busy) R.string.hc_syncing else R.string.hc_sync_now)) { syncNow() }
                    Pill(stringResource(R.string.hc_permissions), filled = false) { request.launch(sync.wantedPermissions()) }
                }
                Text(stringResource(R.string.hc_mfp_hint), fontSize = 12.sp, color = CaptionMuted)
            }
        }
    }
}

@Composable
private fun Toggle(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(body, fontSize = 12.sp, color = CaptionMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
    }
}

@Composable
private fun Pill(label: String, filled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Sage else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (filled) 18.dp else 6.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (filled) Color.White else Sage, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
