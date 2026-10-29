package com.sukoon.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.meter.MeterState
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillNeutralBg
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.map

/** You → Glucose meter: pair a Bluetooth meter (CareSens S Fit BT); its tests come in as finger-pricks. */
@Composable
internal fun MeterPage() {
    val container = (LocalContext.current.applicationContext as SukoonApp).container
    val meter = container.meter
    val s by meter.state.collectAsStateWithLifecycle()
    val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (meter.allowed()) meter.search() }
    val time = DateTimeFormatter.ofPattern("HH:mm")
    val zone = ZoneId.systemDefault()

    if (s.address != null) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Column {
                Text("${s.name ?: stringResource(R.string.meter_title)} · ${s.address!!.takeLast(5).replace(":", "")}", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                val status = when (s.phase) {
                    MeterState.Phase.PAIRING -> stringResource(R.string.meter_pairing)
                    MeterState.Phase.SYNCING -> stringResource(R.string.meter_syncing)
                    else -> s.lastSync?.let { stringResource(R.string.meter_synced, it.atZone(zone).format(time), s.imported) } ?: stringResource(R.string.meter_waiting)
                }
                Text(status, fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.meter_sync_now), SageMist, SageDeep) { meter.syncNow() }
                    Pill(stringResource(R.string.meter_forget), PillNeutralBg, CaptionMuted) { meter.forget() }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Column {
                Text(stringResource(R.string.meter_found_title).uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
                Found(stringResource(R.string.meter_found_service), s.hasService, "0x1808")
                Found(stringResource(R.string.meter_found_count), s.onMeter?.let { true }, s.onMeter?.let { String.format(Locale.getDefault(), "%d", it) } ?: "–")
                Text(
                    stringResource(if (s.hasService == false) R.string.meter_no_service_body else R.string.meter_found_note),
                    fontSize = 12.5.sp, lineHeight = 17.sp, color = if (s.hasService == false) PillHighText else CaptionMuted, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        // What came in from the meter lately.
        val recent by produceState(emptyList<Pair<Instant, Int>>(), s.lastSync) {
            val since = Instant.now().minus(Duration.ofDays(2)).toEpochMilli()
            container.logbookRepository.eventsSince(since).map { list ->
                list.filter { it.type == LogEventType.FINGERSTICK.name && it.value != null }.sortedByDescending { it.timestampMillis }.take(5)
                    .map { Instant.ofEpochMilli(it.timestampMillis) to kotlin.math.round(it.value!!).toInt() }
            }.collect { value = it }
        }
        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Column {
                    Text(stringResource(R.string.meter_recent).uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
                    recent.forEach { (at, mgDl) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(at.atZone(zone).format(time), fontSize = 13.sp, color = CaptionMuted)
                            Text(String.format(Locale.getDefault(), "%d", mgDl), fontFamily = HeadlineSerifFontFamily, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    Text(stringResource(if (s.address == null) R.string.meter_pair_title else R.string.meter_pair_other).uppercase(), fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
    Spacer(Modifier.height(8.dp))
    Step(1, stringResource(R.string.meter_step_1))
    Step(2, stringResource(R.string.meter_step_2))
    Spacer(Modifier.height(12.dp))
    val searching = s.phase == MeterState.Phase.SEARCHING
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).border(1.5.dp, Sage, RoundedCornerShape(25.dp))
            .clickable(enabled = !searching) { if (meter.allowed()) meter.search() else ask.launch(permissions) },
        contentAlignment = Alignment.Center,
    ) { Text(stringResource(if (searching) R.string.meter_searching else R.string.meter_find), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SageDeep) }
    s.found.forEach { (address, name) ->
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                .clickable { meter.pair(address, name) }.heightIn(min = 52.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.meter_pair), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
        }
    }
    if (searching && s.found.isEmpty()) Text(stringResource(R.string.meter_searching_hint), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.padding(top = 8.dp))
    s.error?.let { Text(stringResource(it), fontSize = 13.sp, lineHeight = 18.sp, color = PillHighText, modifier = Modifier.padding(top = 10.dp)) }
}

@Composable
private fun Found(label: String, ok: Boolean?, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(when (ok) { true -> "✓"; false -> "✕"; null -> "·" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (ok == false) PillHighText else Sage)
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.5.sp, color = CaptionMuted)
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).border(1.5.dp, Sage, CircleShape), contentAlignment = Alignment.Center) {
            Text(String.format(Locale.getDefault(), "%d", n), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
        }
        Text(text, fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Pill(text: String, bg: Color, ink: Color, onClick: () -> Unit) {
    Box(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = ink)
    }
}
