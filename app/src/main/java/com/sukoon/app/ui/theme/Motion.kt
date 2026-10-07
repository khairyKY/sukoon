package com.sukoon.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The motion spec's tokens (_design-export/Sukoon Motion Spec): stillness by default, one calm
 * curve for the moments that matter, slow in and quick out, loops only for live or urgent states.
 */
object Motion {
    const val INSTANT = 90
    const val QUICK = 160
    const val BASE = 240
    const val CALM = 360
    const val SLOW = 560
    const val DRAW = 900
    const val LOOP = 2400

    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val Out = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val In = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val Calm = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    private const val STAGGER_MS = 70L
    private const val STAGGER_CAP = 8

    /** The phone's "Remove animations": every motion then shows its end state (the spec's reduced-motion rules). */
    @Composable
    fun reduced(): Boolean {
        val context = LocalContext.current
        return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    }

    /** List & card stagger: the first eight items rise 14 dp and fade in, 70 ms apart, when the list first appears. */
    @Composable
    fun Modifier.staggerIn(index: Int): Modifier {
        if (index >= STAGGER_CAP || reduced()) return this
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(index * STAGGER_MS)
            progress.animateTo(1f, tween(CALM, easing = Out))
        }
        return graphicsLayer {
            alpha = progress.value
            translationY = (1 - progress.value) * 14.dp.toPx()
        }
    }

    /** Button press: dips to 96 % while held (pass the same source to the clickable). */
    @Composable
    fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
        val pressed by source.collectIsPressedAsState()
        val reduced = reduced()
        val scale by animateFloatAsState(if (pressed && !reduced) 0.96f else 1f, tween(QUICK, easing = Standard), label = "press")
        val alpha by animateFloatAsState(if (pressed && reduced) 0.85f else 1f, tween(INSTANT), label = "pressAlpha")
        return graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    }
}
