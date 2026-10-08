package com.sukoon.app.ui.settings

import com.sukoon.app.platform.TimeFormat
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.sukoon.app.ui.logbook.AppIcon
import com.sukoon.app.ui.logbook.rememberSourceApp
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowText

/** You → Apps & data: MyFitnessPal (meals and workouts in, through Health Connect), then Health Connect itself (glucose out, background sync). */
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
    var lastMeal by remember { mutableStateOf<Instant?>(null) }
    var resyncing by remember { mutableStateOf(false) }
    var inHc by remember { mutableStateOf<Pair<Int, Instant?>?>(null) }
    var last by remember { mutableStateOf<HealthConnectSync.LastSync?>(null) }
    LaunchedEffect(checks) {
        granted = runCatching { sync.granted() }.getOrDefault(emptySet())
        lastMeal = runCatching { sync.lastMfpMeal() }.getOrNull()
        inHc = runCatching { sync.mfpInHealthConnect() }.getOrNull()
        last = sync.lastSync
    }

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

    when (status) {
        HealthConnectClient.SDK_UNAVAILABLE -> Box { Text(stringResource(R.string.hc_unavailable), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground) }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> AppCard {
            Text(stringResource(R.string.hc_update), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
            Pill(stringResource(R.string.hc_update_button)) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")))
                }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val connected = sync.readMeals in granted
            val mfp = rememberSourceApp(HealthConnectSync.MFP)
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(mfp, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text("MyFitnessPal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            lastMeal?.let { stringResource(R.string.hc_mfp_last, mealTime(it)) } ?: stringResource(R.string.hc_mfp_through),
                            fontSize = 12.5.sp,
                            color = CaptionMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (connected) {
                        Text(
                            stringResource(R.string.hc_connected),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SageDeep,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(SageMist).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    } else {
                        Pill(stringResource(R.string.hc_connect_short)) { request.launch(sync.wantedPermissions()) }
                    }
                }
                Line()
                Toggle(stringResource(R.string.hc_meals_title), stringResource(R.string.hc_meals_body), importMeals) {
                    importMeals = it
                    sync.importMeals = it
                    if (it && sync.readMeals !in granted) request.launch(sync.wantedPermissions())
                }
                Toggle(stringResource(R.string.hc_activity), stringResource(R.string.hc_activity_body), importActivity) {
                    importActivity = it
                    sync.importActivity = it
                    if (it && sync.readActivity !in granted) request.launch(sync.wantedPermissions())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(stringResource(if (resyncing) R.string.hc_resyncing else R.string.hc_resync), Modifier.weight(1f)) {
                        if (resyncing) return@OutlineButton
                        resyncing = true
                        scope.launch {
                            val message = runCatching {
                                val r = sync.resync()
                                if (r.mfpMeals == 0) context.getString(R.string.hc_resync_none)
                                else context.getString(R.string.hc_resync_done, r.mfpMeals, r.result.mealsAdded, r.result.mealsUpdated)
                            }.getOrElse { context.getString(R.string.sync_failed, it.message ?: it.javaClass.simpleName) }
                            resyncing = false
                            checks++ // re-read "last meal"
                            context.toast(message, long = true)
                        }
                    }
                    OutlineButton(stringResource(R.string.hc_open_mfp), Modifier.weight(1f)) {
                        val open = context.packageManager.getLaunchIntentForPackage(HealthConnectSync.MFP)
                            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${HealthConnectSync.MFP}"))
                        runCatching { context.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
                if (!connected) Text(stringResource(R.string.hc_mfp_hint), fontSize = 12.sp, color = CaptionMuted)
                // What Health Connect has from MyFitnessPal, and how Sukoon's last look went: says whose side a gap is on.
                if (connected) {
                    inHc?.let { (count, newest) ->
                        Text(
                            if (count == 0) stringResource(R.string.hc_mfp_none_in_hc)
                            else stringResource(R.string.hc_mfp_in_hc, count, newest?.let(::mealTime).orEmpty()),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = if (count == 0) PillHighText else CaptionMuted,
                        )
                    }
                    last?.let { s ->
                        Text(
                            if (s.error != null) stringResource(R.string.hc_last_sync_failed, mealTime(s.at), s.error)
                            else stringResource(R.string.hc_last_sync, mealTime(s.at), s.added, s.updated),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = if (s.error != null) PillLowText else CaptionMuted,
                        )
                    }
                }
            }
            AppCard {
                Column {
                    Text(stringResource(R.string.hc_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(stringResource(R.string.hc_store), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.padding(top = 2.dp))
                }
                Line()
                Toggle(stringResource(R.string.hc_export), stringResource(R.string.hc_share_body), shareGlucose) {
                    shareGlucose = it
                    sync.shareGlucose = it
                    if (it && sync.writeGlucose !in granted) request.launch(sync.wantedPermissions())
                }
                if (remember { sync.backgroundSupported() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.hc_background), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                        if (sync.readInBackground in granted) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = SageDeep, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(R.string.hc_allowed), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
                        } else {
                            Pill(stringResource(R.string.hc_allow)) { request.launch(sync.wantedPermissions()) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(stringResource(if (busy) R.string.hc_syncing else R.string.hc_sync_now)) { syncNow() }
                    Pill(stringResource(R.string.hc_permissions), filled = false) { request.launch(sync.wantedPermissions()) }
                }
            }
        }
    }
}

/** "12:52" today, "Mon 12:52" before. */
private fun mealTime(at: Instant): String {
    val zone = ZoneId.systemDefault()
    val today = at.atZone(zone).toLocalDate() == LocalDate.now(zone)
    return TimeFormat.of(if (today) "HH:mm" else "EEE HH:mm").format(at.atZone(zone))
}

@Composable
private fun AppCard(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, outline(), RoundedCornerShape(18.dp))
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    content = content,
)

@Composable
private fun Line() = HorizontalDivider(color = outline().copy(alpha = 0.08f))

@Composable
private fun OutlineButton(label: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, outline(), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
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
