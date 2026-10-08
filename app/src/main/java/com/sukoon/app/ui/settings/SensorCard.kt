package com.sukoon.app.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sukoon.app.R
import com.sukoon.app.data.source.SourceStatus
import com.sukoon.app.data.source.libre.FactoryCalibration
import com.sukoon.app.data.source.libre.Libre2
import com.sukoon.app.data.source.libre.LibreNfc
import com.sukoon.app.data.source.libre.SensorPairing
import com.sukoon.app.data.source.libre.hex
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateLow
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import com.sukoon.app.ui.components.toast
import com.sukoon.app.data.source.libre.SensorLifecycle
import java.time.Instant
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import com.sukoon.app.data.source.libre.SensorLife
import com.sukoon.app.ui.components.durationText
import com.sukoon.app.ui.components.rememberBluetoothOn
import com.sukoon.app.ui.components.sensorTime
import com.sukoon.app.ui.components.turnOnBluetooth
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowBg
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.StateHigh
import java.time.Duration
import kotlinx.coroutines.delay

private enum class TapMode { CHECK, CONNECT }

/**
 * You → Your sensor. Two NFC actions:
 * - Check (read-only): reads + decrypts the sensor and shows the decoded value — DiaBox keeps
 *   working, so the two numbers can be compared before switching (the B7 validation step).
 * - Connect: the same read, then Enable Streaming — Sukoon takes the sensor's Bluetooth stream.
 * Every read is also logged (tag LibreNfc) with the raw bytes for debugging over adb.
 */
@Composable
fun SensorCard(
    pairing: SensorPairing?,
    sensorSelected: Boolean,
    status: SourceStatus,
    onPaired: (LibreNfc.SensorRead, Long) -> Unit,
    onForget: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    var mode by remember { mutableStateOf<TapMode?>(null) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val paired by rememberUpdatedState(onPaired)
    // Shown on the card and toasted, so the outcome is seen even if the card is scrolled away.
    fun report(message: String, isFailure: Boolean) {
        result = message
        failed = isFailure
        context.toast(message, long = true)
    }
    var awaitingReadings by remember { mutableStateOf(false) }
    LaunchedEffect(status, awaitingReadings) {
        if (awaitingReadings && status == SourceStatus.Connected) {
            awaitingReadings = false
            context.toast(context.getString(R.string.toast_sensor_live))
        }
    }

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun bluetoothGranted() = permissions.filter { it != Manifest.permission.POST_NOTIFICATIONS }
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (bluetoothGranted()) mode = TapMode.CONNECT else report(context.getString(R.string.sensor_need_permissions), isFailure = true)
    }

    val active = mode
    if (active != null && activity != null && adapter != null) {
        DisposableEffect(active) {
            adapter.enableReaderMode(
                activity,
                { tag ->
                    val scannedAt = System.currentTimeMillis()
                    val outcome = runCatching { LibreNfc.readAndPair(tag, pair = active == TapMode.CONNECT) }
                    outcome.onSuccess { logRead(it) }.onFailure { Log.w("LibreNfc", "Read failed", it) }
                    activity.runOnUiThread {
                        outcome.onSuccess { read ->
                            if (read.bleMac != null) {
                                paired(read, scannedAt)
                                awaitingReadings = true
                                report(context.getString(R.string.sensor_connected_now), isFailure = false)
                            } else {
                                report(describe(context, read), isFailure = read.fram == null || !read.supported)
                            }
                        }.onFailure {
                            report(context.getString(R.string.sensor_failed, it.message ?: it.javaClass.simpleName), isFailure = true)
                        }
                        mode = null
                    }
                },
                NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null,
            )
            onDispose { adapter.disableReaderMode(activity) }
        }
    }

    val bluetooth = rememberBluetoothOn()
    // The newest reading's time is what "Live" and "No signal" are judged by (here, in onboarding and on You → Sensor alike).
    val latest by (context.applicationContext as com.sukoon.app.SukoonApp).container.glucoseRepository.latestReading.collectAsStateWithLifecycle(null)
    val lastReadingAt = latest?.timestamp
    val now by produceState(Instant.now()) {
        while (true) {
            delay(30_000)
            value = Instant.now()
        }
    }
    val life = pairing?.let { SensorLifecycle.of(it.startMillis, it.lifetimeMinutes, now) }
    val agoMinutes = lastReadingAt?.let { Duration.between(it, now).toMinutes().coerceAtLeast(0) }
    val inUse = pairing != null && sensorSelected

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // What needs you, first and loud (design "Sensor, redone").
        when {
            inUse && !bluetooth -> Issue(true, stringResource(R.string.issue_bt_title), stringResource(R.string.issue_bt_body), stringResource(R.string.issue_bt_action)) { turnOnBluetooth(context) }
            inUse && life is SensorLife.Running && (agoMinutes == null || agoMinutes >= 10) -> Issue(
                true,
                if (agoMinutes == null) stringResource(R.string.issue_no_readings_title) else stringResource(R.string.issue_signal_title, durationText(context, agoMinutes)),
                stringResource(R.string.issue_signal_body),
            )
        }
        when (life) {
            is SensorLife.Ended -> Issue(true, stringResource(R.string.issue_ended_title), stringResource(R.string.issue_ended_body))
            is SensorLife.Running -> {
                val left = Duration.between(now, life.endsAt)
                if (left <= Duration.ofDays(3)) {
                    Issue(left <= Duration.ofDays(1), stringResource(R.string.issue_ending_title, sensorTime(life.endsAt)), stringResource(R.string.issue_ending_body, durationText(context, left.toMinutes())))
                }
            }
            else -> Unit
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, outline(), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pairing == null) {
                Text(stringResource(R.string.sensor_none_title), fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.sensor_body_unpaired), fontSize = 13.5.sp, color = CaptionMuted)
            } else {
                // The state from the last reading, not the Bluetooth link: the sensor drops it after every reading.
                val (state, color) = when {
                    !sensorSelected -> stringResource(R.string.sensor_state_unused) to CaptionMuted
                    life is SensorLife.Ended -> stringResource(R.string.sensor_state_ended) to StateLow
                    life is SensorLife.WarmingUp || status == SourceStatus.WarmingUp -> stringResource(R.string.sensor_state_warming) to StateHigh
                    !bluetooth -> stringResource(R.string.sensor_state_bt_off) to StateLow
                    agoMinutes != null && agoMinutes < 6 -> stringResource(R.string.sensor_state_live) to Sage
                    agoMinutes != null && agoMinutes < 20 -> stringResource(R.string.sensor_state_reconnecting) to StateHigh
                    else -> stringResource(R.string.sensor_state_no_signal) to StateLow
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(state, fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Text(
                        when (agoMinutes) {
                            null -> stringResource(R.string.sensor_last_none)
                            0L -> stringResource(R.string.sensor_last_now)
                            else -> stringResource(R.string.sensor_last_ago, durationText(context, agoMinutes))
                        },
                        fontSize = 12.5.sp,
                        color = CaptionMuted,
                    )
                }
                Text(stringResource(R.string.sensor_serial, pairing.serial), fontSize = 12.5.sp, color = CaptionMuted)
                life?.let { LifeBar(it, now) }
                (status as? SourceStatus.Error)?.let { Text(stringResource(R.string.sensor_status_error, it.message), fontSize = 12.5.sp, color = StateLow) }
            }

            when {
                adapter == null -> Text(stringResource(R.string.sensor_no_nfc), fontSize = 13.sp, color = StateLow)
                !adapter.isEnabled -> {
                    Text(stringResource(R.string.sensor_nfc_off), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                    CardButton(stringResource(R.string.sensor_nfc_settings)) { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                }
                mode != null -> {
                    Text(stringResource(R.string.sensor_hold), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    CardButton(stringResource(R.string.sensor_cancel), filled = false) { mode = null }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CardButton(stringResource(if (pairing == null) R.string.sensor_connect else R.string.sensor_repair)) {
                            result = null
                            if (bluetoothGranted()) mode = TapMode.CONNECT else requestPermissions.launch(permissions.toTypedArray())
                        }
                        CardButton(stringResource(R.string.sensor_check), filled = false) { result = null; mode = TapMode.CHECK }
                    }
                    Text(stringResource(R.string.sensor_connect_hint) + " " + stringResource(R.string.sensor_check_hint), fontSize = 12.sp, color = CaptionMuted)
                }
            }
            result?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (failed) StateLow else Sage) }
        }
        if (pairing != null && mode == null) {
            Text(
                stringResource(R.string.sensor_forget),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onForget).padding(horizontal = 4.dp, vertical = 12.dp),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = StateLow,
            )
        }
    }
}

/** "Day 12 of 15 · ends Sun 11 Oct, 14:10" over a bar that turns amber in the last 3 days and red in the last. */
@Composable
private fun LifeBar(life: SensorLife, now: Instant) {
    val context = LocalContext.current
    when (life) {
        is SensorLife.WarmingUp -> Text(context.resources.getQuantityString(R.plurals.sensor_life_warming, life.minutesLeft, life.minutesLeft), fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onBackground)
        is SensorLife.Ended -> Text(stringResource(R.string.sensor_life_ended, sensorTime(life.endedAt)), fontSize = 13.5.sp, color = StateLow)
        is SensorLife.Running -> {
            val total = Duration.between(life.startedAt, life.endsAt)
            val elapsed = Duration.between(life.startedAt, now)
            val left = Duration.between(now, life.endsAt)
            val color = when {
                left <= Duration.ofDays(1) -> StateLow
                left <= Duration.ofDays(3) -> StateHigh
                else -> Sage
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.sensor_day_of, elapsed.toDays() + 1, (total.toHours() + 12) / 24), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.sensor_ends_at, sensorTime(life.endsAt)), fontSize = 12.5.sp, color = CaptionMuted)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth((elapsed.toMinutes().toFloat() / total.toMinutes()).coerceIn(0.02f, 1f)).fillMaxHeight().background(color))
                }
            }
        }
    }
}

/** A problem with what to do about it: red when readings stop, amber when one is coming. */
@Composable
private fun Issue(danger: Boolean, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    val bg = if (danger) PillLowBg else PillHighBg
    val fg = if (danger) PillLowText else PillHighText
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(fg), contentAlignment = Alignment.Center) {
            Text("!", color = bg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg)
            Text(body, fontSize = 13.sp, lineHeight = 17.sp, color = fg)
        }
        action?.let {
            Text(
                it,
                modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(fg).clickable(onClick = onAction).padding(horizontal = 16.dp, vertical = 14.dp),
                color = bg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}

private fun days(minutes: Int) = String.format(Locale.getDefault(), "%.1f", minutes / 1440.0)

/** One-tap summary: what the sensor is, whether its memory decrypted, and the decoded current value. */
private fun describe(context: Context, read: LibreNfc.SensorRead): String {
    if (!read.supported) return context.getString(R.string.sensor_unsupported, read.patchInfo.hex())
    val fram = read.fram ?: return context.getString(R.string.sensor_memory_bad)
    val info = Libre2.sensorInfo(fram)
    val latest = Libre2.parseFram(FactoryCalibration.fromFram(fram), fram).maxByOrNull { it.minute }
    return context.getString(R.string.sensor_check_result, read.serial, days(info.ageMinutes), info.state.name.lowercase(), latest?.mgDl?.toString() ?: "-")
}

private fun logRead(read: LibreNfc.SensorRead) {
    val fram = read.fram
    Log.i(
        "LibreNfc",
        "uid=${read.uid.hex("")} patch=${read.patchInfo.hex("")} supported=${read.supported} crcOk=${fram != null} " +
            "serial=${read.serial} mac=${read.bleMac} rawFram=${read.rawFram.hex("")}",
    )
    if (fram != null) {
        val calibration = FactoryCalibration.fromFram(fram)
        Log.i("LibreNfc", "info=${Libre2.sensorInfo(fram)} calibration=$calibration points=${Libre2.parseFram(calibration, fram).joinToString { "${it.minute}:${it.mgDl}" }}")
    }
}

@Composable
private fun CardButton(label: String, filled: Boolean = true, onClick: () -> Unit) {
    Row {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (filled) Sage else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = if (filled) 18.dp else 4.dp, vertical = 10.dp),
        ) {
            Text(label, color = if (filled) Color.White else Sage, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}
