package com.sukoon.app.ui.settings

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
import com.sukoon.app.ui.components.lifeLine
import java.time.Instant

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

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pairing != null) {
            val now = Instant.now()
            Text(
                stringResource(R.string.sensor_paired, pairing.serial, days(((now.toEpochMilli() - pairing.startMillis) / 60_000).toInt()), days(pairing.lifetimeMinutes)),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(lifeLine(context, SensorLifecycle.of(pairing.startMillis, pairing.lifetimeMinutes, now), now), fontSize = 12.sp, color = CaptionMuted)
            if (sensorSelected) StatusLine(status)
        } else {
            Text(stringResource(R.string.sensor_body_unpaired), fontSize = 12.5.sp, color = CaptionMuted)
        }

        when {
            adapter == null -> Text(stringResource(R.string.sensor_no_nfc), fontSize = 13.sp, color = StateLow)
            !adapter.isEnabled -> {
                Text(stringResource(R.string.sensor_nfc_off), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                CardButton(stringResource(R.string.sensor_nfc_settings)) { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            }
            mode != null -> {
                Text(stringResource(R.string.sensor_hold), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                CardButton(stringResource(R.string.sensor_cancel), filled = false) { mode = null }
            }
            else -> {
                CardButton(stringResource(R.string.sensor_check), filled = false) { result = null; mode = TapMode.CHECK }
                Text(stringResource(R.string.sensor_check_hint), fontSize = 11.5.sp, color = CaptionMuted)
                CardButton(stringResource(if (pairing == null) R.string.sensor_connect else R.string.sensor_repair)) {
                    result = null
                    if (bluetoothGranted()) mode = TapMode.CONNECT else requestPermissions.launch(permissions.toTypedArray())
                }
                Text(stringResource(R.string.sensor_connect_hint), fontSize = 11.5.sp, color = CaptionMuted)
                if (pairing != null) CardButton(stringResource(R.string.sensor_forget), filled = false, onClick = onForget)
            }
        }
        result?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (failed) StateLow else Sage) }
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
private fun StatusLine(status: SourceStatus) {
    val (text, color) = when (status) {
        SourceStatus.Connected -> stringResource(R.string.sensor_status_connected) to Sage
        SourceStatus.WarmingUp -> stringResource(R.string.sensor_status_warming) to CaptionMuted
        is SourceStatus.Error -> stringResource(R.string.sensor_status_error, status.message) to StateLow
        else -> stringResource(R.string.sensor_status_connecting) to CaptionMuted
    }
    Text(text, fontSize = 12.5.sp, color = color)
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
