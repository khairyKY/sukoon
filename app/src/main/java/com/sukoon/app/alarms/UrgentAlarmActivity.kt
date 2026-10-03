package com.sukoon.app.alarms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.emergency.EscalationPhase
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.StateUrgent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import com.sukoon.app.ui.theme.Motion

/**
 * The urgent-low takeover: shown over the lock screen (full-screen intent) or straight over
 * whatever is open (with "display over other apps"). One big action — "I'm treating it" —
 * silences the alarm for 5 minutes; if glucose is still urgent then, it comes back.
 *
 * During an emergency escalation it becomes the countdown ("Alerting Mum, Dad in 43 s" + I'm OK),
 * and after the texts went out it says so and offers to tell the contacts you're OK.
 */
class UrgentAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val mgDl = intent.getIntExtra(EXTRA_MG_DL, -1).takeIf { it > 0 }
        val container = (applicationContext as SukoonApp).container
        fun answer(block: suspend () -> Unit) {
            lifecycleScope.launch {
                block()
                finish()
            }
        }
        setContent {
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
                EscalationPhase.Idle -> if (mgDl == null) {
                    // Opened for a countdown that has already been answered: nothing left to show.
                    LaunchedEffect(Unit) { finish() }
                } else {
                    Takeover(
                        title = stringResource(R.string.alarm_urgent_title),
                        big = String.format(Locale.getDefault(), "%d", mgDl),
                        body = stringResource(R.string.alarm_urgent_body),
                        action = stringResource(R.string.alarm_action_treating),
                    ) { answer { container.alarms.acknowledge(AlarmType.URGENT_LOW, 5) } }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_MG_DL = "mg_dl"
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

        private fun secondsUntil(at: Instant) = ((Duration.between(Instant.now(), at).toMillis() + 999) / 1000).coerceAtLeast(0).toInt()

        fun intent(context: Context, mgDl: Int?) = Intent(context, UrgentAlarmActivity::class.java)
            .putExtra(EXTRA_MG_DL, mgDl ?: -1)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun Takeover(title: String, big: String, body: String, action: String, onAction: () -> Unit) {
    // Motion spec: scales in from 93 % over 560 ms; the reading breathes with the live-dot rhythm.
    // "Remove animations": it simply appears and holds still. Never blocks the button.
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
            .background(StateUrgent)
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
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .clickable(onClick = onAction)
                .padding(vertical = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(action, color = StateUrgent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
