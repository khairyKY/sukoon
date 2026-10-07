package com.sukoon.app.ui.logbook

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.insulin.InjectionRegion
import com.sukoon.app.insulin.InjectionSite
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.hypot

/** A zone in figure units. */
internal data class Zone(val x: Float, val y: Float, val w: Float, val h: Float) {
    /** How far ([px], [py]) is from the zone; 0 inside it. */
    fun distance(px: Float, py: Float): Float = hypot(maxOf(x - px, 0f, px - (x + w)), maxOf(y - py, 0f, py - (y + h)))
}

/**
 * The body map's geometry (design "Long-acting: where"): two 120 × 220 figures, front as you look
 * down at yourself and back as seen from behind, so your left is on the left in both.
 */
internal object BodyZones {
    const val WIDTH = 120f
    const val HEIGHT = 220f

    val zones: Map<InjectionSite, Zone> = mapOf(
        InjectionSite.ABDOMEN_LEFT to Zone(37f, 74f, 21f, 30f),
        InjectionSite.ABDOMEN_RIGHT to Zone(62f, 74f, 21f, 30f),
        InjectionSite.THIGH_LEFT to Zone(38f, 120f, 19f, 40f),
        InjectionSite.THIGH_RIGHT to Zone(63f, 120f, 19f, 40f),
        InjectionSite.ARM_LEFT to Zone(18f, 46f, 13f, 32f),
        InjectionSite.ARM_RIGHT to Zone(89f, 46f, 13f, 32f),
        InjectionSite.BUTTOCK_LEFT to Zone(37f, 98f, 21f, 22f),
        InjectionSite.BUTTOCK_RIGHT to Zone(62f, 98f, 21f, 22f),
    )

    fun onFront(site: InjectionSite) = site.region == InjectionRegion.ABDOMEN || site.region == InjectionRegion.THIGH

    /** What a tap at ([x], [y]) on a figure means: the nearest zone within a forgiving [reach], as the arms are narrow. */
    fun hit(x: Float, y: Float, front: Boolean, reach: Float = 10f): InjectionSite? =
        zones.filterKeys { onFront(it) == front }.entries
            .minByOrNull { it.value.distance(x, y) }
            ?.takeIf { it.value.distance(x, y) <= reach }
            ?.key
}

/**
 * The two figures. Tap a zone to [onPick] it (null: a picture only). [suggested] gets a dashed ring
 * while nothing is [selected]; [heat] shades each zone by its doses and shows the count.
 */
@Composable
internal fun BodyMap(
    selected: InjectionSite?,
    suggested: InjectionSite?,
    onPick: ((InjectionSite) -> Unit)?,
    modifier: Modifier = Modifier,
    heat: Map<InjectionSite, Int> = emptyMap(),
    height: Dp = 210.dp,
) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(true, false).forEach { front ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Figure(front, selected, suggested, heat, onPick, height)
                Text(stringResource(if (front) R.string.site_front else R.string.site_back), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
            }
        }
    }
}

@Composable
private fun Figure(front: Boolean, selected: InjectionSite?, suggested: InjectionSite?, heat: Map<InjectionSite, Int>, onPick: ((InjectionSite) -> Unit)?, height: Dp) {
    val pick by rememberUpdatedState(onPick)
    val body = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)
    val navel = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
    val idleFill = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val idleStroke = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
    val deep = SageDeep
    val most = heat.values.maxOrNull() ?: 0
    val zones = BodyZones.zones.filterKeys { BodyZones.onFront(it) == front }
    val unit = height / BodyZones.HEIGHT // dp per figure unit
    val suggestedLabel = stringResource(R.string.site_suggested)
    val names = zones.keys.associateWith { siteName(it) }
    // Laid out left to right whatever the language: the figure is a picture of you, not text.
    Box(
        Modifier
            .size(unit * BodyZones.WIDTH, height)
            .then(
                if (onPick == null) Modifier else Modifier.pointerInput(front) {
                    detectTapGestures { o ->
                        val s = size.width / BodyZones.WIDTH
                        BodyZones.hit(o.x / s, o.y / s, front)?.let { site -> pick?.invoke(site) }
                    }
                },
            ),
    ) {
        Canvas(Modifier.size(unit * BodyZones.WIDTH, height)) {
            val s = size.width / BodyZones.WIDTH
            fun part(x: Float, y: Float, w: Float, h: Float, r: Float) =
                drawRoundRect(body, Offset(x * s, y * s), Size(w * s, h * s), CornerRadius(r * s))
            drawCircle(body, 14f * s, Offset(60f * s, 18f * s))
            part(54f, 30f, 12f, 8f, 0f)
            part(33f, 36f, 54f, 80f, 16f)
            part(17f, 40f, 15f, 72f, 7.5f)
            part(88f, 40f, 15f, 72f, 7.5f)
            part(36f, 108f, 23f, 104f, 11f)
            part(61f, 108f, 23f, 104f, 11f)
            if (front) drawCircle(navel, 2.4f * s, Offset(60f * s, 89f * s)) // the navel: keep away from it

            zones.forEach { (site, z) ->
                val on = site == selected
                val next = selected == null && site == suggested
                val count = heat[site] ?: 0
                val topLeft = Offset(z.x * s, z.y * s)
                val zoneSize = Size(z.w * s, z.h * s)
                val corner = CornerRadius(8f * s)
                val fill = when {
                    on -> Sage
                    count > 0 -> Sage.copy(alpha = 0.15f + 0.7f * count / most)
                    next -> Sage.copy(alpha = 0.14f)
                    else -> idleFill
                }
                drawRoundRect(fill, topLeft, zoneSize, corner)
                val stroke = when {
                    on -> Stroke(2.dp.toPx())
                    next -> Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * s, 3f * s)))
                    count > 0 -> null
                    else -> Stroke(1.2f.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * s, 3f * s)))
                }
                if (stroke != null) drawRoundRect(if (on) deep else if (next) Sage else idleStroke, topLeft, zoneSize, corner, style = stroke)
            }
        }
        // One node per zone for TalkBack (taps are handled above, forgivingly), and the heat counts.
        zones.forEach { (site, z) ->
            val count = heat[site] ?: 0
            val isOn = site == selected
            val isNext = selected == null && site == suggested
            Box(
                Modifier
                    .absoluteOffset(unit * z.x, unit * z.y)
                    .size(unit * z.w, unit * z.h)
                    .semantics {
                        contentDescription = if (heat.isEmpty()) names.getValue(site) else "${names.getValue(site)}: $count"
                        if (onPick != null) {
                            role = Role.Button
                            this.selected = isOn
                            onClick { pick?.invoke(site); true }
                        }
                        if (isNext) stateDescription = suggestedLabel
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (count > 0) {
                    Text(
                        formatAmountLocalized(count.toDouble()),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (count * 2 > most) Color.White else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
        Text(stringResource(R.string.site_l), Modifier.align(AbsoluteAlignment.TopLeft).padding(start = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CaptionMuted)
        Text(stringResource(R.string.site_r), Modifier.align(AbsoluteAlignment.TopRight).padding(end = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CaptionMuted)
    }
}

/**
 * "Where?" on an insulin entry. Closed: one line with the spot, or the one to use next. Open: the
 * body map, where one tap picks the place and the side. Optional: Skip leaves it out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WherePanel(
    site: InjectionSite?,
    suggested: InjectionSite?,
    lastUsed: (InjectionSite) -> Instant?,
    open: Boolean,
    onOpen: () -> Unit,
    onPick: (InjectionSite) -> Unit,
    onSkip: () -> Unit,
) {
    val wash = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
    if (!open) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(wash)
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow(stringResource(R.string.site_where))
            Text(
                when {
                    site != null -> siteName(site)
                    suggested != null -> stringResource(R.string.site_next, siteName(suggested))
                    else -> stringResource(R.string.site_optional)
                },
                fontSize = 14.sp,
                fontWeight = if (site != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (site != null) MaterialTheme.colorScheme.onBackground else CaptionMuted,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(if (site != null) R.string.site_change else R.string.site_add), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
        }
        return
    }
    val view = LocalView.current
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        delay(80) // the keypad folds away first
        bring.bringIntoView()
    }
    Column(Modifier.bringIntoViewRequester(bring).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(wash).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.site_where), Modifier.weight(1f))
            TextButton(onClick = onSkip) { Text(stringResource(R.string.site_skip), color = SageDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
        BodyMap(
            selected = site,
            suggested = suggested,
            onPick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onPick(it)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val shown = site ?: suggested
        if (shown != null) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).then(if (site != null) Modifier.background(Sage) else Modifier.border(1.5.dp, Sage, CircleShape)))
                val name = if (site != null) siteName(site) else stringResource(R.string.site_next, siteName(shown))
                val last = lastUsedLabel(lastUsed(shown))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(name) }
                        withStyle(SpanStyle(color = CaptionMuted)) { append(" · $last") }
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (site == null && suggested != null) {
            Text(stringResource(R.string.site_dashed), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** "last here 3 days ago", "yesterday", "today", or not lately. */
@Composable
internal fun lastUsedLabel(at: Instant?): String {
    val zone = ZoneId.systemDefault()
    val days = at?.let { ChronoUnit.DAYS.between(it.atZone(zone).toLocalDate(), LocalDate.now(zone)).toInt() }
    return when {
        days == null -> stringResource(R.string.site_not_lately)
        days <= 0 -> stringResource(R.string.site_last_today)
        days == 1 -> stringResource(R.string.site_last_yesterday)
        else -> pluralStringResource(R.plurals.site_last_days, days, days)
    }
}

@Composable
internal fun siteName(site: InjectionSite): String = stringResource(
    when (site) {
        InjectionSite.ABDOMEN_LEFT -> R.string.site_abdomen_left
        InjectionSite.ABDOMEN_RIGHT -> R.string.site_abdomen_right
        InjectionSite.THIGH_LEFT -> R.string.site_thigh_left
        InjectionSite.THIGH_RIGHT -> R.string.site_thigh_right
        InjectionSite.ARM_LEFT -> R.string.site_arm_left
        InjectionSite.ARM_RIGHT -> R.string.site_arm_right
        InjectionSite.BUTTOCK_LEFT -> R.string.site_buttock_left
        InjectionSite.BUTTOCK_RIGHT -> R.string.site_buttock_right
    },
)
