package com.sukoon.app.ui.logbook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.insulin.Finger
import com.sukoon.app.insulin.FingerDigit
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import kotlin.math.hypot

/**
 * The hands' geometry, in the drawings' units (res/drawable/hand_palm_left.xml and hand_palm_right.xml,
 * 316 tall, one fingertip-to-wrist length): both palms facing you, as you'd hold them up to a camera: the left hand on
 * the left, thumbs pointing out. Each hand is its own drawing, nothing mirrored.
 */
internal object HandZones {
    const val LEFT_W = 199.2f
    const val RIGHT_W = 217.4f
    const val RIGHT_AT = LEFT_W + 20f // the right hand's left edge
    const val WIDTH = RIGHT_AT + RIGHT_W
    const val HEIGHT = 316f
    const val PAD = 11f // a fingertip's radius

    /** Each fingertip's centre, read off each drawing. */
    private fun tip(finger: Finger): Offset = when (finger) {
        Finger.LEFT_INDEX -> Offset(66.1f, 19.2f)
        Finger.LEFT_MIDDLE -> Offset(98.2f, 11.6f)
        Finger.LEFT_RING -> Offset(138.1f, 38.4f)
        Finger.LEFT_PINKY -> Offset(184.6f, 94.9f)
        Finger.LEFT_THUMB -> Offset(13.9f, 120.2f)
        Finger.RIGHT_INDEX -> Offset(141.9f, 19.2f)
        Finger.RIGHT_MIDDLE -> Offset(101.3f, 10f)
        Finger.RIGHT_RING -> Offset(59.3f, 37.1f)
        Finger.RIGHT_PINKY -> Offset(11.6f, 89.6f)
        Finger.RIGHT_THUMB -> Offset(206.6f, 133.5f)
    }

    fun center(finger: Finger): Offset = tip(finger).let { if (finger.left) it else Offset(RIGHT_AT + it.x, it.y) }

    /** What a tap at ([x], [y]) means: the nearest fingertip within a forgiving [reach]. */
    fun hit(x: Float, y: Float, reach: Float = 20f): Finger? =
        Finger.entries.minByOrNull { hypot(center(it).x - x, center(it).y - y) }
            ?.takeIf { hypot(center(it).x - x, center(it).y - y) <= reach }
}

/**
 * Both hands. Tap a fingertip to [onPick] it (null: a picture only). [suggested] gets a dashed ring
 * while nothing is [selected].
 */
@Composable
internal fun HandMap(
    selected: Finger?,
    suggested: Finger?,
    onPick: ((Finger) -> Unit)?,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
) {
    val pick by rememberUpdatedState(onPick)
    val ink = MaterialTheme.colorScheme.onBackground
    val idleStroke = ink.copy(alpha = 0.35f)
    val deep = SageDeep
    val leftHand = rememberVectorPainter(ImageVector.vectorResource(R.drawable.hand_palm_left))
    val rightHand = rememberVectorPainter(ImageVector.vectorResource(R.drawable.hand_palm_right))
    val tint = ColorFilter.tint(ink.copy(alpha = 0.9f))
    val unit = height / HandZones.HEIGHT // dp per unit
    val suggestedLabel = stringResource(R.string.site_suggested)
    val names = Finger.entries.associateWith { fingerName(it) }
    // Laid out left to right whatever the language: the hands are a picture of you, not text.
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Box(
            Modifier
                .size(unit * HandZones.WIDTH, height)
                .then(
                    if (onPick == null) Modifier else Modifier.pointerInput(Unit) {
                        detectTapGestures { o ->
                            val s = size.width / HandZones.WIDTH
                            HandZones.hit(o.x / s, o.y / s)?.let { f -> pick?.invoke(f) }
                        }
                    },
                ),
        ) {
            Canvas(Modifier.size(unit * HandZones.WIDTH, height)) {
                val s = size.width / HandZones.WIDTH
                withTransform({ scale(s, s, Offset.Zero) }) {
                    with(leftHand) { draw(Size(HandZones.LEFT_W, HandZones.HEIGHT), colorFilter = tint) }
                    translate(HandZones.RIGHT_AT, 0f) {
                        with(rightHand) { draw(Size(HandZones.RIGHT_W, HandZones.HEIGHT), colorFilter = tint) }
                    }
                }

                Finger.entries.forEach { finger ->
                    val on = finger == selected
                    val next = selected == null && finger == suggested
                    val c = HandZones.center(finger) * s
                    val r = HandZones.PAD * s
                    if (on || next) drawCircle(if (on) Sage else Sage.copy(alpha = 0.18f), r, c) // idle tips stay open: a filled dot reads as a hole
                    when {
                        on -> drawCircle(deep, r, c, style = Stroke(2.dp.toPx()))
                        next -> drawCircle(Sage, r, c, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                        else -> drawCircle(idleStroke, r, c, style = Stroke(1.2f.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
                    }
                }
            }
            // One node per fingertip for TalkBack (taps are handled above, forgivingly).
            Finger.entries.forEach { finger ->
                val c = HandZones.center(finger)
                val isOn = finger == selected
                val isNext = selected == null && finger == suggested
                Box(
                    Modifier
                        .absoluteOffset(unit * (c.x - 14f), unit * (c.y - 14f))
                        .size(unit * 28f, unit * 28f)
                        .semantics {
                            contentDescription = names.getValue(finger)
                            if (onPick != null) {
                                role = Role.Button
                                this.selected = isOn
                                onClick { pick?.invoke(finger); true }
                            }
                            if (isNext) stateDescription = suggestedLabel
                        },
                )
            }
            Text(stringResource(R.string.site_l), Modifier.align(AbsoluteAlignment.BottomLeft).padding(start = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CaptionMuted)
            Text(stringResource(R.string.site_r), Modifier.align(AbsoluteAlignment.BottomRight).padding(end = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CaptionMuted)
        }
    }
}

@Composable
internal fun fingerName(finger: Finger): String = stringResource(
    when (finger) {
        Finger.LEFT_MIDDLE -> R.string.finger_left_middle
        Finger.RIGHT_MIDDLE -> R.string.finger_right_middle
        Finger.LEFT_RING -> R.string.finger_left_ring
        Finger.RIGHT_RING -> R.string.finger_right_ring
        Finger.LEFT_PINKY -> R.string.finger_left_pinky
        Finger.RIGHT_PINKY -> R.string.finger_right_pinky
        Finger.LEFT_INDEX -> R.string.finger_left_index
        Finger.RIGHT_INDEX -> R.string.finger_right_index
        Finger.LEFT_THUMB -> R.string.finger_left_thumb
        Finger.RIGHT_THUMB -> R.string.finger_right_thumb
    },
)
