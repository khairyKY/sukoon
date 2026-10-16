package com.sukoon.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.LocalDarkTheme
import kotlin.math.PI
import kotlin.math.sin

/** Where a spoken entry is: Sotto's pill states. */
enum class PillState { LISTENING, TRANSCRIBING, POLISHING, DONE, ERROR }

/** Sotto's colours (its overlay.js), light and dark. */
private object Sotto {
    val lilac = Color(0xFF8E74D0); val lilacDark = Color(0xFFC9B8EE)
    val amber = Color(0xFFD4A06A); val amberDark = Color(0xFFE8C48E)
    val gold = Color(0xFFE8C78A); val goldDark = Color(0xFFF0D9A4)
    val glint = Color(0xFFF5E2B0); val glintDark = Color(0xFFFAF0D6)
    val blush = Color(0xFFF0BFCF); val blushDark = Color(0xFF5B3F4E)
    val cream = Color(0xFFF0E9DF); val plum = Color(0xFF2C2634)
    val muted = Color(0xFF948C86); val mutedDark = Color(0xFF8F859A)
}

/**
 * Sotto's dictation pill, ported from its overlay (D:\Coding\sotto\ui\overlay.js): lilac bars that
 * follow your voice while listening, amber bubbles rising while the words are written down, gold
 * blobs with a shimmer and a twinkle while they're worked into entries, a lilac tick when done,
 * blush on an error. [level] 0..1 is the microphone's loudness; tapping the pill finishes.
 */
@Composable
fun SottoPill(state: PillState, level: Float, onTap: () -> Unit = {}, onCancel: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    val t by rememberInfiniteTransition(label = "pill").animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing), RepeatMode.Restart), label = "time")
    val now = t * 1000f // milliseconds, as in Sotto
    val accent = if (dark) Sotto.lilacDark else Sotto.lilac
    // The mic's level, gated and eased: room noise reads as silence, and the bars glide instead of jitter.
    val heard by androidx.compose.animation.core.animateFloatAsState(
        if (level < 0.08f) 0f else ((level - 0.08f) / 0.92f).coerceIn(0f, 1f),
        tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "level",
    )
    val cancel = onCancel != null && state != PillState.DONE && state != PillState.ERROR
    val label = stringResource(
        when (state) {
            PillState.LISTENING -> R.string.pill_listening
            PillState.TRANSCRIBING -> R.string.pill_transcribing
            PillState.POLISHING -> R.string.pill_polishing
            PillState.DONE -> R.string.pill_done
            PillState.ERROR -> R.string.pill_error
        },
    )
    Row(
        modifier
            .height(52.dp)
            .shadow(10.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x66C4B7A5), spotColor = Color(0x66C4B7A5))
            .clip(RoundedCornerShape(26.dp))
            .background(if (state == PillState.ERROR) (if (dark) Sotto.blushDark else Sotto.blush) else if (dark) Sotto.plum else Sotto.cream)
            .clickable(enabled = state == PillState.LISTENING, onClick = onTap)
            .semantics { contentDescription = label }
            .padding(horizontal = if (cancel) 8.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // An empty twin of the ✕ on the other side, so the animation sits in the pill's middle.
        if (cancel) Box(Modifier.size(36.dp))
        Canvas(Modifier.width(96.dp).fillMaxHeight()) {
            val cx = size.width / 2
            val yc = size.height / 2
            val u = 1.dp.toPx()
            when (state) {
                PillState.LISTENING -> {
                    val amplitude = (heard * 1.3f).coerceIn(0f, 1f)
                    val barW = 4.5f * u
                    val gap = 5f * u
                    val total = 5 * barW + 4 * gap
                    for (i in 0 until 5) {
                        // Silence is a slow, soft breath (about one a second); speech lifts the bars.
                        val wave = sin(((now * 0.0011f + i * 0.14f) * 2 * PI).toFloat())
                        val mix = amplitude * 0.85f + 0.08f * (0.5f + 0.5f * wave)
                        val v = 0.15f + 0.85f * mix
                        val h = maxOf(5f * u, v * 24f * u)
                        drawRoundRect(accent.copy(alpha = 0.5f + v * 0.5f), Offset(cx - total / 2 + i * (barW + gap), yc - h / 2), Size(barW, h), CornerRadius(barW / 2))
                    }
                }
                PillState.TRANSCRIBING -> {
                    val amber = if (dark) Sotto.amberDark else Sotto.amber
                    val sizes = floatArrayOf(9f, 7f, 11f, 8f)
                    val xs = floatArrayOf(-20.5f, -5.5f, 10.5f, 23f)
                    for (i in 0 until 4) {
                        val p = cycle(now, 2.3f, -0.5f * i)
                        val opacity = when {
                            p < 0.25f -> seg(0f, 0.95f, p / 0.25f)
                            p < 0.72f -> seg(0.95f, 0.55f, (p - 0.25f) / 0.47f)
                            else -> seg(0.55f, 0f, (p - 0.72f) / 0.28f)
                        }
                        val floor = yc + 12f * u
                        drawCircle(amber.copy(alpha = opacity), sizes[i] / 2 * seg(0.4f, 1.05f, p) * u * 1.2f, Offset(cx + xs[i] * u * 1.2f, floor - sizes[i] / 2 * u + seg(9f, -15f, p) * u))
                    }
                }
                PillState.POLISHING -> {
                    val gold = if (dark) Sotto.goldDark else Sotto.gold
                    val glint = if (dark) Sotto.glintDark else Sotto.glint
                    listOf(floatArrayOf(-14f, 1f, 8f, 0f), floatArrayOf(1f, 0f, 10f, -0.5f), floatArrayOf(13.5f, 1.5f, 7.5f, -1f)).forEach { (ox, oy, r, delay) ->
                        val p = cycle(now, 2.6f, delay)
                        val bx = sin(p * 2 * PI.toFloat()) * 2f
                        val by = sin(p * 2 * PI.toFloat() + 1.3f) * 1.5f
                        val bs = 0.92f + 0.12f * (0.5f + 0.5f * sin(p * 2 * PI.toFloat()))
                        drawCircle(gold.copy(alpha = 0.85f), r * bs * u * 1.2f, Offset(cx + (ox + bx) * u * 1.2f, yc + (oy + by) * u * 1.2f))
                    }
                    val sp = cycle(now, 2f, 0f)
                    val sx = cx + seg(-28f, 28f, sp) * u * 1.2f
                    val a = if (sp < 0.5f) seg(0f, 1f, sp * 2) else seg(1f, 0f, sp * 2 - 1)
                    drawRoundRect(Brush.horizontalGradient(listOf(glint.copy(alpha = 0f), glint.copy(alpha = a), glint.copy(alpha = 0f)), sx, sx + 26f * u), Offset(sx, yc), Size(26f * u, 3f * u), CornerRadius(1.5f * u))
                    val tp = cycle(now, 1.8f, 0f)
                    val k = if (tp < 0.5f) seg(0f, 1f, tp * 2) else seg(1f, 0f, tp * 2 - 1)
                    if (k > 0.01f) star(Offset(cx + 30f * u, yc - 11f * u), 5f * u * k, glint.copy(alpha = k))
                }
                PillState.DONE -> {
                    val p1 = Offset(cx - 9 * u, yc + 1 * u)
                    val p2 = Offset(cx - 3 * u, yc + 7 * u)
                    val p3 = Offset(cx + 11 * u, yc - 8 * u)
                    drawPath(Path().apply { moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y) }, accent, style = Stroke(2.8f * u, cap = StrokeCap.Round))
                }
                PillState.ERROR -> {
                    val ink = if (dark) Color(0xFFF0C3D2) else Color(0xFFA85874)
                    drawLine(ink, Offset(cx, yc - 8 * u), Offset(cx, yc + 3 * u), 3f * u, StrokeCap.Round)
                    drawCircle(ink, 1.8f * u, Offset(cx, yc + 8 * u))
                }
            }
        }
        // Sotto's ✕: cancel while it's live.
        if (cancel) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background((if (dark) Sotto.mutedDark else Sotto.muted).copy(alpha = 0.14f)).clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 14.sp, color = if (dark) Sotto.mutedDark else Sotto.muted)
            }
        }
    }
}

/** Say it, in Sotto's colours: the lilac wave tile, the title and hint, and who powers it. */
@Composable
fun SottoBanner(title: String, hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    val lilac = if (dark) Sotto.lilacDark else Sotto.lilac
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) Sotto.plum else Sotto.cream)
            .border(1.dp, lilac.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(lilac), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) {
                val w = size.width
                val barW = w * 0.13f
                floatArrayOf(0.35f, 0.7f, 1f, 0.6f, 0.3f).forEachIndexed { i, h ->
                    val x = w * 0.1f + i * w * 0.2f - barW / 2
                    drawRoundRect(Color(0xFFF0E9DF), Offset(x, (w - w * h * 0.8f) / 2), Size(barW, w * h * 0.8f), CornerRadius(barW / 2))
                }
            }
        }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = if (dark) Sotto.cream else Sotto.plum)
            Text(hint, fontSize = 12.5.sp, color = if (dark) Sotto.mutedDark else Sotto.muted)
        }
        Text(stringResource(R.string.sotto_name), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = lilac,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(lilac.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** "Powered by Sotto", with Sotto's wave in its lilac tile. */
@Composable
fun PoweredBySotto(modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(if (dark) Sotto.lilacDark else Sotto.lilac), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(10.dp)) {
                val w = size.width
                val path = Path().apply {
                    moveTo(0f, w * 0.55f)
                    cubicTo(w * 0.15f, w * 0.2f, w * 0.3f, w * 0.2f, w * 0.45f, w * 0.5f)
                    cubicTo(w * 0.6f, w * 0.8f, w * 0.78f, w * 0.8f, w, w * 0.45f)
                }
                drawPath(path, Color(0xFFF0E9DF), style = Stroke(w * 0.16f, cap = StrokeCap.Round))
            }
        }
        Text(stringResource(R.string.powered_by_sotto), fontSize = 11.5.sp, color = CaptionMuted)
    }
}

/** 0..1 through a loop of [seconds], shifted by [delay] seconds (CSS animation-delay). */
private fun cycle(nowMs: Float, seconds: Float, delay: Float): Float {
    val x = (nowMs / 1000f - delay) / seconds
    return x - kotlin.math.floor(x)
}

private fun seg(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

/** Sotto's 4-point twinkle. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.star(c: Offset, r: Float, color: Color) {
    val i = r * 0.28f
    drawPath(Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + i, c.y - i); lineTo(c.x + r, c.y); lineTo(c.x + i, c.y + i)
        lineTo(c.x, c.y + r); lineTo(c.x - i, c.y + i); lineTo(c.x - r, c.y); lineTo(c.x - i, c.y - i); close()
    }, color)
}

