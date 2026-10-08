package com.sukoon.app.alarms

import com.sukoon.app.platform.TimeFormat
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.emergency.EscalationPhase
import com.sukoon.app.ui.components.durationText
import com.sukoon.app.ui.theme.CanvasDark
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Motion
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.StateUrgent
import com.sukoon.app.ui.theme.SukoonTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.Build
import android.view.WindowManager

/**
 * The full-screen alert, for every alarm: urgent low, low, going low, high and no readings, yours
 * or someone's you follow. Shown over the lock screen (full-screen intent) or straight over
 * whatever is open (with "display over other apps"). One big answer ("I'm treating it" for your
 * own lows, "OK" otherwise: the alarm's usual snooze) and snooze choices under it; an urgent low
 * can't be snoozed past 5 minutes. It closes itself once its alarm is over.
 *
 * During an emergency escalation it becomes the countdown ("Alerting Mum, Dad in 43 s" + I'm OK),
 * and after the texts went out it says so and offers to tell the contacts you're OK.
 */
class AlarmActivity : ComponentActivity() {

    private var shown by mutableStateOf<Shown?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        shown = Shown.of(intent)
        val container = (applicationContext as SukoonApp).container
        // The theme carries the app's fonts; its colours don't show here (every colour on this screen is explicit).
        setContent { SukoonTheme {
            val phase by container.alarms.escalation.collectAsState()
            val names = container.emergency.contacts.joinToString { it.name }
            when (val p = phase) {
                is EscalationPhase.Countdown -> {
                    val seconds by produceState(secondsUntil(p.endsAt), p) {
                        while (value > 0) {
                            delay(200)
                            value = secondsUntil(p.endsAt)
                        }
                    }
                    Takeover(
                        title = stringResource(R.string.emergency_countdown_title),
                        big = String.format(Locale.getDefault(), "%d", seconds),
                        body = stringResource(R.string.emergency_screen_countdown, names),
                        action = stringResource(R.string.emergency_im_ok),
                    ) { answer { container.alarms.imOk() } }
                }
                is EscalationPhase.Sent -> Takeover(
                    title = stringResource(R.string.emergency_sent_title),
                    big = TIME.format(p.at),
                    body = stringResource(R.string.emergency_screen_sent, p.to.joinToString().ifBlank { names }),
                    action = stringResource(R.string.emergency_screen_tell_ok),
                ) { answer { container.alarms.imOk() } }
                EscalationPhase.Idle -> when (val s = shown) {
                    // Opened for a countdown that has already been answered: nothing left to show.
                    null -> LaunchedEffect(Unit) { finish() }
                    else -> {
                        val mine by container.alarms.active.collectAsState()
                        val theirs by container.followerWatch.active.collectAsState()
                        val on = s.test || s.type in (if (s.person == null) mine else theirs[s.person].orEmpty())
                        LaunchedEffect(on) { if (!on) finish() } // over (back in range, signal back): nothing to answer
                        val settings = container.settings.alarmSettings
                        val treatable = s.person == null && (s.type == AlarmType.URGENT_LOW || s.type == AlarmType.LOW)
                        fun snooze(minutes: Int, treated: Boolean) = answer {
                            when {
                                s.test -> container.alarms.endTest(s.type)
                                s.person != null -> container.followerWatch.acknowledge(s.person, s.type, minutes)
                                else -> container.alarms.acknowledge(s.type, minutes, treated = treated)
                            }
                        }
                        Takeover(
                            title = listOfNotNull(
                                if (s.test) stringResource(R.string.alarm_test_prefix) else null,
                                s.who?.let { "$it ·" },
                                stringResource(s.type.titleRes),
                            ).joinToString(" "),
                            big = if (s.type == AlarmType.SIGNAL_LOSS || s.mgDl == null) "- - -" else String.format(Locale.getDefault(), "%d", s.mgDl),
                            body = alarmBody(LocalContext.current, s.type, s.minutes, s.who),
                            action = stringResource(if (treatable) R.string.alarm_action_treating else R.string.alarm_action_ok),
                            color = s.type.screenColor,
                            snoozes = s.type.snoozeChoices,
                            onSnooze = { snooze(it, treated = false) },
                        ) { snooze(s.type.defaultSnooze(settings), treated = treatable) }
                    }
                }
            }
        } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val next = Shown.of(intent) ?: return
        // A newer alarm takes the screen, except over your own urgent low: that one stays until answered.
        val current = shown
        if (current == null || current.test || current.person != null || current.type != AlarmType.URGENT_LOW || next.type == AlarmType.URGENT_LOW) shown = next
    }

    private fun answer(block: suspend () -> Unit) {
        lifecycleScope.launch {
            block()
            finish()
        }
    }

    /** The alarm on screen, from the intent that opened it. */
    private data class Shown(val type: AlarmType, val mgDl: Int?, val minutes: Long?, val who: String?, val person: String?, val test: Boolean) {
        companion object {
            fun of(intent: Intent): Shown? {
                val type = AlarmType.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TYPE) } ?: return null
                return Shown(
                    type = type,
                    mgDl = intent.getIntExtra(EXTRA_MG_DL, -1).takeIf { it > 0 },
                    minutes = intent.getLongExtra(EXTRA_MINUTES, -1).takeIf { it >= 0 },
                    who = intent.getStringExtra(EXTRA_WHO),
                    person = intent.getStringExtra(EXTRA_PERSON),
                    test = intent.getBooleanExtra(EXTRA_TEST, false),
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_MG_DL = "mg_dl"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_WHO = "who"
        private const val EXTRA_PERSON = "person"
        private const val EXTRA_TEST = "test"
        private val TIME: DateTimeFormatter get() = TimeFormat.of()

        private fun secondsUntil(at: Instant) = ((Duration.between(Instant.now(), at).toMillis() + 999) / 1000).coerceAtLeast(0).toInt()

        /** The screen for [alert] (null: the emergency countdown / texts-sent screen). One per alarm and person. */
        fun intent(context: Context, alert: Alert?, who: String? = null, person: String? = null, test: Boolean = false): Intent {
            val intent = Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (alert == null) return intent
            return intent
                .setData(Uri.parse("sukoon://alert/${person ?: "me"}/${alert.type.name}"))
                .putExtra(EXTRA_TYPE, alert.type.name)
                .putExtra(EXTRA_MG_DL, alert.mgDl ?: -1)
                .putExtra(EXTRA_MINUTES, alert.minutesSinceReading ?: -1)
                .putExtra(EXTRA_WHO, who)
                .putExtra(EXTRA_PERSON, person)
                .putExtra(EXTRA_TEST, test)
        }
    }
}

/** Lows in the alarm red, highs in deep amber, lost signal in the dark canvas: white text reads on all three. */
private val AlarmType.screenColor: Color
    get() = when (this) {
        AlarmType.URGENT_LOW, AlarmType.LOW, AlarmType.GOING_LOW -> StateUrgent
        AlarmType.HIGH -> Color(0xFF8A5A1E) // deep amber
        AlarmType.SIGNAL_LOSS -> CanvasDark
    }

/** The snooze buttons, in minutes. Urgent low stays at 5 (see AlarmEngine.snooze); a low at most an hour. */
private val AlarmType.snoozeChoices: List<Int>
    get() = when (this) {
        AlarmType.URGENT_LOW -> listOf(5)
        AlarmType.LOW, AlarmType.GOING_LOW -> listOf(15, 30, 60)
        AlarmType.HIGH -> listOf(30, 60, 120, 240)
        AlarmType.SIGNAL_LOSS -> listOf(15, 30, 60, 120)
    }

@Composable
private fun Takeover(
    title: String,
    big: String,
    body: String,
    action: String,
    color: Color = StateUrgent,
    snoozes: List<Int> = emptyList(),
    onSnooze: (Int) -> Unit = {},
    onAction: () -> Unit,
) {
    // Motion spec: scales in from 93 % over 560 ms; the reading breathes with the live-dot rhythm.
    // "Remove animations": it simply appears and holds still. Never blocks the buttons.
    val reduced = Motion.reduced()
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(Motion.SLOW, easing = Motion.Out)) }
    val breath by rememberInfiniteTransition(label = "reading").animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(Motion.LOOP / 2, easing = Motion.Standard), RepeatMode.Reverse),
        label = "breath",
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(color)
            .graphicsLayer {
                alpha = entrance.value
                scaleX = 0.93f + 0.07f * entrance.value
                scaleY = 0.93f + 0.07f * entrance.value
            }
            .safeDrawingPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(big, color = Color.White, fontSize = 120.sp, fontFamily = HeadlineSerifFontFamily, modifier = Modifier.graphicsLayer { alpha = if (reduced) 1f else breath })
        Text(body, color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(if (snoozes.isEmpty()) 48.dp else 36.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .clickable(onClick = onAction)
                .padding(vertical = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(action, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (snoozes.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.alarm_snooze_for), color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            val context = LocalContext.current
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                snoozes.forEach { minutes ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            .clickable { onSnooze(minutes) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(durationText(context, minutes.toLong()), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
