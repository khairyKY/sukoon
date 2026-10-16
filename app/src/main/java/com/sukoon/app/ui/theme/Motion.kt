package com.sukoon.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The motion spec's tokens (docs/motion.md, from _design-export/Sukoon Motion Spec): stillness by
 * default, one calm curve for the moments that matter, slow in and quick out, loops only for live
 * or urgent states. Nothing here is used on alarm, low or urgent surfaces: those appear at once.
 */
object Motion {
    const val INSTANT = 90
    const val QUICK = 160
    const val BASE = 240
    const val CALM = 360
    const val SLOW = 560
    const val DRAW = 900
    const val LOOP = 2400

    /** Reduced motion: spatial moves (push, sheet, toast) become this crossfade in place. */
    const val REDUCED = 120

    /** Between items of a list or grid as it first appears. */
    const val STAGGER = 36L

    /** Only the first items stagger; the rest arrive with the last of them. */
    const val STAGGER_CAP = 8

    /** How long a screen's first appearance counts as "first" ([firstShow]): the whole cascade fits in it. */
    const val FIRST_SHOW = 1_500L

    /** How far an entrance travels: a rise, or a slide in from the side. */
    val RISE = 10.dp

    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val Out = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val In = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val Calm = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** The phone's "Remove animations": every motion then shows its end state (the spec's reduced-motion rules). */
    @Composable
    fun reduced(): Boolean {
        val context = LocalContext.current
        return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    }

    /** True for a screen's first [FIRST_SHOW] ms (per [keys]); saved, so coming back to it doesn't replay its entrance. */
    @Composable
    fun firstShow(vararg keys: Any?): Boolean {
        var on by rememberSaveable(*keys) { mutableStateOf(true) }
        LaunchedEffect(*keys) {
            delay(FIRST_SHOW)
            on = false
        }
        return on
    }

    /**
     * List & card stagger: the first [STAGGER_CAP] items fade in and rise [RISE] ([fromX] ±1: slide in
     * from the end or start side instead, mirrored in Arabic), [STAGGER] ms apart. Once per [key]:
     * saved, so a row scrolled back into view just shows. Reduced: all fade in together, no travel.
     */
    @Composable
    fun Modifier.staggerIn(index: Int, key: Any? = null, fromX: Int = 0): Modifier {
        if (index >= STAGGER_CAP) return this
        var done by rememberSaveable(key) { mutableStateOf(false) }
        if (done) return this
        val reduced = reduced()
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val progress = remember(key) { Animatable(0f) }
        LaunchedEffect(key) {
            if (!reduced) delay(index * STAGGER)
            progress.animateTo(1f, tween(if (reduced) REDUCED else CALM, easing = Out))
            done = true
        }
        return graphicsLayer {
            alpha = progress.value
            if (!reduced) {
                val travel = (1 - progress.value) * RISE.toPx()
                if (fromX == 0) translationY = travel else translationX = travel * fromX * (if (rtl) -1 else 1)
            }
        }
    }

    /** Something just added (a new log entry): a soft wash of [color] that fades away once. Reduced: none. */
    @Composable
    fun Modifier.arrivalGlow(color: Color): Modifier {
        var done by rememberSaveable { mutableStateOf(false) }
        if (done || reduced()) return this
        val glow = remember { Animatable(1f) }
        LaunchedEffect(Unit) {
            delay(BASE.toLong())
            glow.animateTo(0f, tween(DRAW * 2, easing = Standard))
            done = true
        }
        return drawBehind { drawRoundRect(color.copy(alpha = color.alpha * glow.value), cornerRadius = CornerRadius(12.dp.toPx())) }
    }

    /** Selection colour (chips, choice rows, tabs): crossfades on the standard curve; reduced, capped at [INSTANT]. */
    @Composable
    fun tint(target: Color): Color =
        animateColorAsState(target, tween(if (reduced()) INSTANT else BASE, easing = Standard), label = "tint").value

    /** Progress (rings, bars): sweeps from empty to [target] when first shown, then to each new value. Reduced: drawn at once. */
    @Composable
    fun fill(target: Float): Float {
        val reduced = reduced()
        val shown = remember { Animatable(if (reduced) target else 0f) }
        LaunchedEffect(target) { if (reduced) shown.snapTo(target) else shown.animateTo(target, tween(SLOW, easing = Calm)) }
        return shown.value
    }

    /** Expand / collapse and text that grows (a streaming answer): the size settles instead of jumping. Reduced: jumps. */
    @Composable
    fun Modifier.settleSize(): Modifier = if (reduced()) this else animateContentSize(tween(BASE, easing = Out))

    /** Something appearing in a column (a chip row, a card): fades in as its space opens. Reduced: a short fade. */
    @Composable
    fun appear(): EnterTransition =
        if (reduced()) fadeIn(tween(REDUCED)) else expandVertically(tween(BASE, easing = Out)) + fadeIn(tween(BASE, easing = Out))

    /** The way out: quicker, accelerating. */
    @Composable
    fun vanish(): ExitTransition =
        if (reduced()) fadeOut(tween(REDUCED)) else shrinkVertically(tween(BASE, easing = In)) + fadeOut(tween(QUICK, easing = In))

    /**
     * A value or label that changes in place: the old one fades out as the new one fades in, rising a
     * little when [rise] (small numbers). Reduced: a short crossfade.
     */
    @Composable
    fun <T> Swap(target: T, modifier: Modifier = Modifier, rise: Boolean = false, content: @Composable (T) -> Unit) {
        val reduced = reduced()
        AnimatedContent(
            target,
            modifier,
            transitionSpec = {
                if (reduced || !rise) {
                    fadeIn(tween(if (reduced) REDUCED else BASE, easing = Out)) togetherWith fadeOut(tween(if (reduced) REDUCED else QUICK, easing = In))
                } else {
                    slideInVertically(tween(BASE, easing = Out)) { it / 3 } + fadeIn(tween(BASE, easing = Out)) togetherWith
                        slideOutVertically(tween(QUICK, easing = In)) { -it / 4 } + fadeOut(tween(QUICK, easing = In))
                }
            },
            contentAlignment = Alignment.CenterStart,
            label = "swap",
        ) { content(it) }
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
