package com.sukoon.app.ui.settings

import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.PillNeutralBg
import com.sukoon.app.ui.theme.UiFontFamily
import com.sukoon.app.ui.components.drawSensor
import com.sukoon.app.ui.components.SukoonSheet
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.sukoon.app.ui.theme.Motion
import com.sukoon.app.ui.theme.SageMist
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
import androidx.compose.material3.TextButton
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

private enum class TapMode { CHECK, CONNECT, START }

/**
 * Where the card's NFC actions are, one value per step (Sukoon Redesign Canvas §5–6): the visuals
 * switch on this, and only the reader callback moves it on from Holding / Lost.
 */
private sealed interface SensorStep {
    data object Idle : SensorStep
    /** Start's confirmation is open. */
    data object Confirm : SensorStep
    /** Listening for the sensor. */
    data class Holding(val mode: TapMode) : SensorStep
    /** Lost it for a moment halfway through a read: still listening. */
    data class Lost(val mode: TapMode) : SensorStep
    /** Started, warming up, and paired. */
    data object Started : SensorStep
    /** Streaming to Sukoon. */
    data object Paired : SensorStep
    /** A read-only check's findings. */
    data class Checked(val check: LibreNfc.Check) : SensorStep
    /** A final no, worded for the person: what happened and what to try. */
    data class Refused(val reason: String) : SensorStep
}

/** The action the reader is listening for, if any. */
private val SensorStep.listeningFor: TapMode?
    get() = when (this) {
        is SensorStep.Holding -> mode
        is SensorStep.Lost -> mode
        else -> null
    }

/** Milliseconds between Android's "is the tag still there?" checks while Sukoon reads (default 125). */
private const val PRESENCE_CHECK_MS = 2_000

/**
 * You → Your sensor. Three NFC actions:
 * - Check (read-only): reads + decrypts the sensor and shows its state and decoded value — DiaBox
 *   keeps working, so the two numbers can be compared before switching (the B7 validation step).
 * - Connect: the same read, then Enable Streaming — Sukoon takes the sensor's Bluetooth stream.
 * - Start: for a new sensor nobody has started — Activate, then the same as Connect.
 * A read the phone loses halfway ("Tag was lost") doesn't end the action: the card keeps listening
 * and Android hands the sensor back as soon as it finds it again. Every read is also logged (tag
 * LibreNfc) with the raw bytes for debugging over adb.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    var step by remember { mutableStateOf<SensorStep>(SensorStep.Idle) }
    /** The action the Bluetooth permission prompt was opened for (pairing needs it; so does starting). */
    var afterPermission by remember { mutableStateOf(TapMode.CONNECT) }
    val paired by rememberUpdatedState(onPaired)
    // A guide link ("Start a new sensor") opens the confirmation straight away.
    val startAsked by (context.applicationContext as com.sukoon.app.SukoonApp).container.requestedStartSensor.collectAsStateWithLifecycle()
    LaunchedEffect(startAsked) {
        if (startAsked) {
            step = SensorStep.Confirm
            (context.applicationContext as com.sukoon.app.SukoonApp).container.requestedStartSensor.value = false
        }
    }
    // Every outcome is also toasted, so it is seen even if the card is scrolled away.
    fun settle(next: SensorStep, message: String) {
        step = next
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
        if (bluetoothGranted()) {
            step = SensorStep.Holding(afterPermission)
        } else {
            context.getString(R.string.sensor_need_permissions).let { settle(SensorStep.Refused(it), it) }
        }
    }

    val active = step.listeningFor
    if (active != null && activity != null && adapter != null) {
        DisposableEffect(active) {
            adapter.enableReaderMode(
                activity,
                { tag ->
                    val scannedAt = System.currentTimeMillis()
                    val outcome = runCatching { LibreNfc.readAndPair(tag, pair = active != TapMode.CHECK, start = active == TapMode.START) }
                    outcome.onSuccess { logRead(it) }.onFailure { Log.w("LibreNfc", "Read failed", it) }
                    activity.runOnUiThread {
                        if (step.listeningFor != active) return@runOnUiThread // a late answer for a tap already settled or cancelled
                        val error = outcome.exceptionOrNull()
                        if (error != null && LibreNfc.retryable(error)) {
                            // Lost for a moment, not refused: keep listening; Android calls back when it finds the sensor again.
                            step = SensorStep.Lost(active)
                            return@runOnUiThread
                        }
                        outcome.onSuccess { read ->
                            if (read.bleMac != null) {
                                paired(read, scannedAt)
                                awaitingReadings = true
                                val warming = read.info?.state == Libre2.State.STARTING
                                if (active == TapMode.START && warming) {
                                    settle(SensorStep.Started, context.getString(R.string.sensor_started_now))
                                } else {
                                    settle(SensorStep.Paired, context.getString(R.string.sensor_connected_now))
                                }
                            } else {
                                val check = read.check()
                                when {
                                    check != null -> settle(SensorStep.Checked(check), describe(context, check))
                                    !read.supported -> context.getString(R.string.sensor_unsupported, read.patchInfo.hex()).let { settle(SensorStep.Refused(it), it) }
                                    else -> context.getString(R.string.sensor_memory_bad).let { settle(SensorStep.Refused(it), it) }
                                }
                            }
                        }.onFailure {
                            when (it) {
                                // Pair again on a sensor nobody started: offer to start it, it's what's needed.
                                is LibreNfc.NotStartedException -> step = SensorStep.Confirm
                                // Sukoon's own sentences already say what happened and what to do.
                                is LibreNfc.NfcException -> settle(SensorStep.Refused(it.message.orEmpty()), it.message.orEmpty())
                                else -> context.getString(R.string.sensor_failed, it.message ?: it.javaClass.simpleName).let { message -> settle(SensorStep.Refused(message), message) }
                            }
                        }
                    }
                },
                NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                // Android checks the tag is still there every 125 ms by default; on a Libre those extra
                // commands are what cut reads short ("Tag was lost"), so check far less often.
                Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_CHECK_MS) },
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
    // A pairing saved from a sensor nobody started (0.8.1 did that): no life, no "no readings", just Start.
    val notStarted = pairing != null && !pairing.started
    val life = pairing?.takeIf { it.started }?.let { SensorLifecycle.of(it.startMillis, it.lifetimeMinutes, now) }
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
                    notStarted -> stringResource(R.string.sensor_state_not_started) to CaptionMuted
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
                    if (!notStarted) Text(
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
                active != null -> {
                    ScanTarget()
                    if (step is SensorStep.Lost) {
                        Text(stringResource(R.string.sensor_hold_again), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PillHighText)
                    } else {
                        Text(stringResource(R.string.sensor_hold), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    CardButton(stringResource(R.string.sensor_cancel), filled = false) { step = SensorStep.Idle }
                }
                notStarted -> {
                    // Nothing to pair until it runs: starting it is the one thing to do; Check stays to look at it.
                    StartRow { step = SensorStep.Confirm }
                    CardButton(stringResource(R.string.sensor_check), filled = false) { step = SensorStep.Holding(TapMode.CHECK) }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CardButton(stringResource(if (pairing == null) R.string.sensor_connect else R.string.sensor_repair)) {
                            afterPermission = TapMode.CONNECT
                            if (bluetoothGranted()) step = SensorStep.Holding(TapMode.CONNECT) else requestPermissions.launch(permissions.toTypedArray())
                        }
                        CardButton(stringResource(R.string.sensor_check), filled = false) { step = SensorStep.Holding(TapMode.CHECK) }
                    }
                    Text(stringResource(R.string.sensor_connect_hint) + " " + stringResource(R.string.sensor_check_hint), fontSize = 12.sp, color = CaptionMuted)
                    StartRow { step = SensorStep.Confirm }
                }
            }
            when (val s = step) {
                SensorStep.Started -> StartedBlock()
                SensorStep.Paired -> Text(stringResource(R.string.sensor_connected_now), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Sage)
                is SensorStep.Checked -> {
                    CheckCard(s.check)
                    if (s.check.stage == LibreNfc.Stage.NOT_STARTED && !notStarted) StartRow { step = SensorStep.Confirm }
                }
                is SensorStep.Refused -> Issue(danger = true, title = stringResource(R.string.sensor_refused_title), body = s.reason)
                else -> Unit
            }
        }
        if (step == SensorStep.Confirm) {
            SukoonSheet(onDismissRequest = { step = SensorStep.Idle }) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val ink = MaterialTheme.colorScheme.onBackground
                    Canvas(Modifier.size(120.dp)) { drawSensor(center, size.minDimension / 2 * 0.86f, ink) }
                    Text(stringResource(R.string.sensor_start_title), fontFamily = HeadlineSerifFontFamily, fontSize = 23.sp, color = ink)
                    Text(stringResource(R.string.sensor_start_body), fontSize = 14.sp, lineHeight = 21.sp, color = CaptionMuted, textAlign = TextAlign.Center)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Sage)
                            .clickable {
                                afterPermission = TapMode.START
                                if (bluetoothGranted()) step = SensorStep.Holding(TapMode.START) else requestPermissions.launch(permissions.toTypedArray())
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.sensor_start_confirm), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
                    TextButton(onClick = { step = SensorStep.Idle }) { Text(stringResource(R.string.sensor_cancel), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep) }
                }
            }
        }
        if (pairing != null && active == null) {
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

/** Waiting for a tap (motion spec "Scan ripple"): three Sage rings spread from the sensor, 800 ms apart; reduced, just the sensor. */
@Composable
private fun ScanTarget() {
    val reduced = Motion.reduced()
    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
        if (!reduced) {
            val loop = rememberInfiniteTransition(label = "scan")
            repeat(3) { i ->
                val t by loop.animateFloat(
                    0f,
                    1f,
                    infiniteRepeatable(tween(Motion.LOOP, easing = Motion.Out), initialStartOffset = StartOffset(i * Motion.LOOP / 3)),
                    label = "ring",
                )
                Box(
                    Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = 0.3f + 1.2f * t
                            scaleY = scaleX
                            alpha = 0.65f * (1 - t)
                        }
                        .border(2.dp, Sage, CircleShape),
                )
            }
        }
        Box(Modifier.size(52.dp).clip(CircleShape).background(SageMist), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_sensor), contentDescription = null, tint = Sage, modifier = Modifier.size(26.dp))
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

/** "Start a new sensor": its own row under Pair again / Check (design "Sensor · start"): a different job. */
@Composable
private fun StartRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(SageMist), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = SageDeep, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sensor_start), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.sensor_start_hint), fontSize = 12.5.sp, color = CaptionMuted)
        }
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = CaptionMuted, modifier = Modifier.size(16.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f })
    }
}

/** Started: the warm-up picture in small (the ring just begun, the glow breathing); Now then counts down. */
@Composable
private fun StartedBlock() {
    val reduced = Motion.reduced()
    val breath by if (reduced) {
        remember { mutableFloatStateOf(0.6f) }
    } else {
        rememberInfiniteTransition(label = "startedGlow").animateFloat(0f, 1f, infiniteRepeatable(tween(Motion.LOOP / 2, easing = Motion.Standard), RepeatMode.Reverse), label = "breath")
    }
    val ink = MaterialTheme.colorScheme.onBackground
    val track = ink.copy(alpha = 0.1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.size(96.dp)) {
            val glow = size.minDimension / 2 * (0.94f + 0.1f * breath)
            drawCircle(Brush.radialGradient(listOf(StateHigh.copy(alpha = 0.36f * (0.35f + 0.65f * breath)), Color.Transparent), center, glow), glow)
            val ring = 40.dp.toPx()
            drawCircle(track, ring, style = Stroke(5.dp.toPx()))
            drawArc(StateHigh, -90f, 8f, false, Offset(center.x - ring, center.y - ring), Size(ring * 2, ring * 2), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawSensor(center, 33.dp.toPx(), ink, adhesive = false)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.sensor_started_title), fontFamily = HeadlineSerifFontFamily, fontSize = 22.sp, color = ink)
            Text(stringResource(R.string.sensor_started_now), fontSize = 13.5.sp, lineHeight = 19.sp, color = CaptionMuted)
        }
    }
}

@Composable
private fun CheckLine(name: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 13.5.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
        value()
    }
}

/** A read-only check, laid out (design "Check sensor result"): state, serial, life, and its value only while running. */
@Composable
private fun CheckCard(check: LibreNfc.Check) {
    val ink = MaterialTheme.colorScheme.onBackground
    val (label, bg, fg) = when (check.stage) {
        LibreNfc.Stage.NOT_STARTED -> Triple(stringResource(R.string.sensor_state_not_started), PillNeutralBg, CaptionMuted)
        LibreNfc.Stage.WARMING_UP -> Triple(stringResource(R.string.sensor_state_warming), PillHighBg, PillHighText)
        LibreNfc.Stage.RUNNING -> Triple(stringResource(R.string.sensor_check_running), SageMist, SageDeep)
        LibreNfc.Stage.ENDED -> Triple(stringResource(R.string.sensor_state_ended), PillLowBg, PillLowText)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, ink.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sensor_check_eyebrow).uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg, modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        CheckLine(stringResource(R.string.sensor_check_serial)) { Text(check.serial, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = ink) }
        when (check.stage) {
            LibreNfc.Stage.WARMING_UP -> check.minutesToReadings?.let { m ->
                CheckLine(stringResource(R.string.sensor_check_warmup)) { Text(stringResource(R.string.sensor_check_readings_in, m), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = ink) }
            }
            LibreNfc.Stage.RUNNING, LibreNfc.Stage.ENDED -> {
                CheckLine(stringResource(R.string.sensor_check_life)) { Text(stringResource(R.string.sensor_day_of, check.day, check.days), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = ink) }
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(ink.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth((check.day.toFloat() / check.days.coerceAtLeast(1)).coerceIn(0.02f, 1f)).fillMaxHeight().background(if (check.stage == LibreNfc.Stage.ENDED) StateLow else Sage))
                }
            }
            LibreNfc.Stage.NOT_STARTED -> Text(stringResource(R.string.sensor_check_fresh), fontSize = 13.sp, lineHeight = 18.sp, color = CaptionMuted)
        }
        check.mgDl?.let { mgDl ->
            CheckLine(stringResource(R.string.sensor_check_reads_now)) {
                Text(
                    buildAnnotatedString {
                        append(String.format(java.util.Locale.getDefault(), "%d", mgDl))
                        withStyle(SpanStyle(fontFamily = UiFontFamily, fontSize = 13.sp, color = CaptionMuted)) { append(" " + stringResource(R.string.home_unit_mgdl)) }
                    },
                    fontFamily = HeadlineSerifFontFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 30.sp,
                    color = ink,
                )
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

/** The check in one line (the toast, and the card until its design is built). */
private fun describe(context: Context, check: LibreNfc.Check): String {
    val stage = when (check.stage) {
        LibreNfc.Stage.NOT_STARTED -> return context.getString(R.string.sensor_check_not_started, check.serial)
        LibreNfc.Stage.WARMING_UP -> context.getString(R.string.sensor_state_warming)
        LibreNfc.Stage.RUNNING -> context.getString(R.string.sensor_check_running)
        LibreNfc.Stage.ENDED -> context.getString(R.string.sensor_state_ended)
    }
    return context.getString(R.string.sensor_check_result, check.serial, check.day.toString(), stage, check.mgDl?.toString() ?: "-")
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
