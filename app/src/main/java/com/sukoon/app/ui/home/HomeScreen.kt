package com.sukoon.app.ui.home

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.data.source.TrendDirection
import com.sukoon.app.ui.components.MiniGraph
import com.sukoon.app.ui.components.SukoonMark
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.NeutralWarm
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowBg
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.PillNeutralBg
import com.sukoon.app.ui.theme.PlaceholderMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageMist
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import com.sukoon.app.ui.theme.SukoonTheme
import com.sukoon.app.ui.widget.arrow
import com.sukoon.app.ui.theme.TextMuted
import com.sukoon.app.ui.theme.UiFontFamily
import java.util.Locale
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import com.sukoon.app.ui.theme.Motion
import com.sukoon.app.ui.theme.Motion.pressScale
import kotlin.math.roundToInt

/**
 * The Home/Now screen, in every state it can be in (docs/design-screens.md §2). Copy, colors,
 * and layout are pulled from the shipped design (Sukoon Brand Directions.dc.html, screens
 * 5a/7b, 5b/7c, 8e, 8f, 8g, 8h, 8i) — see HomeUiState.kt for what data each state carries.
 *
 * Two corrections this implementation made against earlier (pre-design) assumptions, worth
 * knowing if you're editing this file: the hero number uses the serif display font, not a
 * monospace/tabular one — and Low/Urgent share the exact same red rather than two shades.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    onTreated: () -> Unit = {},
    onSnooze: () -> Unit = {},
    onAlertEmergencyContact: () -> Unit = {},
    onTroubleshoot: () -> Unit = {},
    onPairSensor: () -> Unit = {},
    onEnterCodeManually: () -> Unit = {},
    onAddFood: () -> Unit = {},
    onAddInsulin: () -> Unit = {},
    /** Rapid insulin still active, shown beside the shortcuts when there is any. */
    insulinOnBoard: Double = 0.0,
) {
    CompositionLocalProvider(LocalShortcuts provides Shortcuts(onAddFood, onAddInsulin, insulinOnBoard)) {
        HomeContent(state, modifier, onTreated, onSnooze, onAlertEmergencyContact, onTroubleshoot, onPairSensor, onEnterCodeManually)
    }
}

/** Home's quick-log shortcuts, reached from every state's top bar without threading them through each one. */
private class Shortcuts(val onFood: () -> Unit, val onInsulin: () -> Unit, val insulinOnBoard: Double = 0.0)
private val LocalShortcuts = staticCompositionLocalOf { Shortcuts({}, {}) }

@Composable
private fun HomeContent(
    state: HomeUiState,
    modifier: Modifier,
    onTreated: () -> Unit,
    onSnooze: () -> Unit,
    onAlertEmergencyContact: () -> Unit,
    onTroubleshoot: () -> Unit,
    onPairSensor: () -> Unit,
    onEnterCodeManually: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (state) {
            is HomeUiState.InRange -> InRangeContent(state)
            is HomeUiState.Low -> LowContent(state, onTreated, onSnooze)
            is HomeUiState.High -> HighContent(state)
            is HomeUiState.Urgent -> UrgentContent(state, onTreated, onAlertEmergencyContact)
            is HomeUiState.WarmingUp -> WarmingUpContent(state)
            is HomeUiState.Stale -> StaleContent(state, onTroubleshoot)
            HomeUiState.NoSensor -> NoSensorContent(onPairSensor, onEnterCodeManually)
            HomeUiState.SensorEnded -> NoSensorContent(onPairSensor, onEnterCodeManually, ended = true)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared chrome
// ---------------------------------------------------------------------------------------------

@Composable
private fun PulsingDot(color: Color, pulsing: Boolean, size: androidx.compose.ui.unit.Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    // Live pulse (motion spec): one 2400 ms breath; with "remove animations" the dot holds solid.
    val alpha by if (pulsing && !Motion.reduced()) {
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(Motion.LOOP / 2, easing = Motion.Standard), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha)))
}

@Composable
private fun HomeStatusBar(statusLabel: String, dotColor: Color, pulsing: Boolean, labelColor: Color = CaptionMuted) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.End, // the design mock's fake "9:41" clock is gone; the system bar shows real time
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PulsingDot(color = dotColor, pulsing = pulsing)
            Text(text = statusLabel, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 1.sp, color = labelColor)
        }
    }
}

@Composable
private fun HomeAppBar(dimmed: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
            .alpha(if (dimmed) 0.6f else 1f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SukoonMark(markSize = 28.dp)
            Text(
                text = stringResource(R.string.app_name),
                fontFamily = HeadlineSerifFontFamily,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        val shortcuts = LocalShortcuts.current
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (shortcuts.insulinOnBoard >= 0.05) {
                Text(
                    stringResource(R.string.home_iob, String.format(Locale.getDefault(), "%.1f", shortcuts.insulinOnBoard)),
                    fontSize = 12.sp,
                    color = CaptionMuted,
                )
            }
            ShortcutIcon(R.drawable.ic_food, stringResource(R.string.home_add_food), shortcuts.onFood)
            ShortcutIcon(R.drawable.ic_insulin, stringResource(R.string.home_add_insulin), shortcuts.onInsulin)
        }
    }
}

@Composable
private fun ShortcutIcon(iconRes: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription = description, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun StatusPill(label: String, dotColor: Color, backgroundColor: Color, textColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
        Text(text = label, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

@Composable
private fun HeroNumber(value: String, trend: TrendDirection?, color: Color, fontSize: androidx.compose.ui.unit.TextUnit = 100.sp) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = value, fontFamily = HeadlineSerifFontFamily, fontWeight = FontWeight.Light, fontSize = fontSize, color = color)
        if (trend != null) {
            Text(
                text = trendArrow(trend),
                fontFamily = HeadlineSerifFontFamily,
                fontSize = 32.sp,
                color = color,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

private fun trendArrow(trend: TrendDirection): String = trend.arrow

/**
 * Count-up (motion spec): a new reading counts from the last one shown over 560 ms on the calm
 * curve (from a little below on first appearance). Lows don't use it: they show at once.
 */
@Composable
private fun countUp(mgDl: Int): Int {
    val reduced = Motion.reduced()
    val shown = remember { Animatable(if (reduced) mgDl.toFloat() else mgDl * 0.85f) }
    LaunchedEffect(mgDl) {
        if (reduced) shown.snapTo(mgDl.toFloat()) else shown.animateTo(mgDl.toFloat(), tween(Motion.SLOW, easing = Motion.Calm))
    }
    return shown.value.roundToInt()
}

@Composable
private fun trendLabel(trend: TrendDirection): String = when (trend) {
    TrendDirection.FALLING_FAST -> stringResource(R.string.home_trend_falling_fast)
    TrendDirection.FALLING -> stringResource(R.string.home_trend_falling)
    TrendDirection.STEADY -> stringResource(R.string.home_trend_steady)
    TrendDirection.RISING, TrendDirection.RISING_FAST -> stringResource(R.string.home_trend_rising)
}

@Composable
private fun MessageCard(title: String?, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        if (title != null) {
            Text(text = title, fontFamily = HeadlineSerifFontFamily, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
        }
        Text(text = body, fontFamily = UiFontFamily, fontSize = 12.sp, lineHeight = 17.sp, color = CaptionMuted)
    }
}

@Composable
private fun ActionButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(press)
            .clip(RoundedCornerShape(14.dp))
            .let {
                if (filled) it.background(Sage) else it.border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            }
            .clickable(interactionSource = press, indication = LocalIndication.current, onClick = onClick)
            .padding(15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (filled) 14.sp else 13.sp,
            color = if (filled) Color.White else CaptionMuted,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// In range — screens 5a / 7b
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.InRangeContent(state: HomeUiState.InRange) {
    Column(modifier = Modifier.weight(1f)) {
        HomeStatusBar(stringResource(R.string.home_status_live), Sage, pulsing = true)
        HomeAppBar()
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
            StatusPill(stringResource(R.string.home_pill_in_range), Sage, SageMist, com.sukoon.app.ui.theme.SageDeep)
            Spacer(Modifier.height(8.dp))
            HeroNumber(countUp(state.glucoseMgDl).toString(), state.trend, MaterialTheme.colorScheme.onBackground)
            Text(
                text = "${stringResource(R.string.home_unit_mgdl)} · ${trendLabel(state.trend)} · ${stringResource(R.string.home_updated_now)}",
                fontSize = 12.sp,
                color = CaptionMuted,
            )
        }
        Spacer(Modifier.height(22.dp))
        MiniGraph(state.recentReadings, modifier = Modifier.padding(horizontal = 22.dp))
        Text(
            text = stringResource(R.string.home_last_n_hours, 3).uppercase(),
            fontSize = 9.5.sp,
            letterSpacing = 1.sp,
            color = NeutralWarm,
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            MessageCard(stringResource(R.string.home_msg_steady_title), stringResource(R.string.home_msg_steady_body))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Low — screens 5b / 7c
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.LowContent(state: HomeUiState.Low, onTreated: () -> Unit, onSnooze: () -> Unit) {
    Column(modifier = Modifier.weight(1f).padding(horizontal = 22.dp)) {
        HomeStatusBar(stringResource(R.string.home_status_live), StateLow, pulsing = true)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StatusPill(stringResource(R.string.home_pill_low), StateLow, PillLowBg, PillLowText)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            HeroNumber(state.glucoseMgDl.toString(), state.trend, StateLow, fontSize = 104.sp)
            Text(text = "${stringResource(R.string.home_unit_mgdl)} · ${trendLabel(state.trend)}", fontSize = 12.sp, color = CaptionMuted)
        }
        Text(
            text = stringResource(R.string.home_headline_going_low),
            fontFamily = HeadlineSerifFontFamily,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
        )
        Spacer(Modifier.height(16.dp))
        ActionCard(
            labelColor = Sage,
            content = buildAnnotatedString {
                append(stringResource(R.string.home_low_action_prefix))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.home_low_action_bold)) }
                append(stringResource(R.string.home_low_action_suffix))
            },
        )
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(stringResource(R.string.home_btn_treated), filled = true, onClick = onTreated)
            ActionButton(stringResource(R.string.home_btn_snooze_15), filled = false, onClick = onSnooze)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// High — screen 8e
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.HighContent(state: HomeUiState.High) {
    Column(modifier = Modifier.weight(1f)) {
        HomeStatusBar(stringResource(R.string.home_status_live), StateHigh, pulsing = true)
        HomeAppBar()
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
            StatusPill(stringResource(R.string.home_pill_high), StateHigh, PillHighBg, PillHighText)
            Spacer(Modifier.height(8.dp))
            HeroNumber(countUp(state.glucoseMgDl).toString(), state.trend, StateHigh)
            Text(text = "${stringResource(R.string.home_unit_mgdl)} · ${trendLabel(state.trend)}", fontSize = 12.sp, color = CaptionMuted)
        }
        Spacer(Modifier.height(22.dp))
        MiniGraph(state.recentReadings, modifier = Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(18.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            MessageCard(stringResource(R.string.home_msg_high_title), stringResource(R.string.home_msg_high_body))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Urgent — screen 8f
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.UrgentContent(state: HomeUiState.Urgent, onTreated: () -> Unit, onAlertEmergencyContact: () -> Unit) {
    Column(modifier = Modifier.weight(1f).padding(horizontal = 22.dp)) {
        HomeStatusBar(stringResource(R.string.home_status_live), StateLow, pulsing = true, labelColor = StateLow)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StateLow)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PulsingDot(color = Color.White, pulsing = true, size = 9.dp)
            Text(
                text = stringResource(R.string.home_urgent_banner),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            HeroNumber(state.glucoseMgDl.toString(), state.trend, StateLow, fontSize = 104.sp)
            Text(text = "${stringResource(R.string.home_unit_mgdl)} · ${trendLabel(state.trend)}", fontSize = 12.sp, color = CaptionMuted)
        }
        Spacer(Modifier.height(20.dp))
        ActionCard(
            labelColor = StateLow,
            content = buildAnnotatedString {
                append(stringResource(R.string.home_urgent_action_prefix))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.home_urgent_action_bold)) }
                append(stringResource(R.string.home_urgent_action_suffix))
            },
        )
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ActionButton(stringResource(R.string.home_btn_treated), filled = true, onClick = onTreated)
            ActionButton(stringResource(R.string.home_btn_alert_emergency), filled = false, onClick = onAlertEmergencyContact)
        }
    }
}

@Composable
private fun ActionCard(labelColor: Color, content: androidx.compose.ui.text.AnnotatedString) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_do_this_now).uppercase(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = labelColor,
        )
        Spacer(Modifier.height(8.dp))
        Text(text = content, fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------------------------------------------------------------------------------------------
// Warm-up — screen 8g
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.WarmingUpContent(state: HomeUiState.WarmingUp) {
    Column(modifier = Modifier.weight(1f)) {
        HomeStatusBar(stringResource(R.string.home_status_warming), StateHigh, pulsing = true)
        HomeAppBar()
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(150.dp)) {
                    drawCircle(color = PillNeutralBg, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx()))
                    drawArc(
                        color = StateHigh,
                        startAngle = -45f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx()),
                    )
                }
                Text(text = "- -", fontFamily = HeadlineSerifFontFamily, fontWeight = FontWeight.Light, fontSize = 52.sp, color = PlaceholderMuted)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.home_getting_ready),
                fontFamily = HeadlineSerifFontFamily,
                fontSize = 23.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.home_first_reading_prefix))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = StateHigh)) {
                        append(stringResource(R.string.home_first_reading_bold, state.minutesRemaining))
                    }
                },
                fontSize = 13.sp,
                color = CaptionMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 0.dp)) {
            MessageCard(title = null, body = stringResource(R.string.home_warmup_body))
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Stale / signal lost — screen 8h
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.StaleContent(state: HomeUiState.Stale, onTroubleshoot: () -> Unit) {
    Column(modifier = Modifier.weight(1f)) {
        HomeStatusBar(stringResource(R.string.home_status_no_signal), TextMuted, pulsing = false)
        HomeAppBar(dimmed = true)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            StatusPill(stringResource(R.string.home_pill_signal_lost), TextMuted, PillNeutralBg, TextMuted)
            Text(
                text = "- - -",
                fontFamily = HeadlineSerifFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 100.sp,
                color = PlaceholderMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.home_last_reading_ago, state.lastGlucoseMgDl, state.minutesAgo),
                fontSize = 12.sp,
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(22.dp))
        MiniGraph(state.recentReadings, modifier = Modifier.padding(horizontal = 22.dp), dimmed = true)
        Spacer(Modifier.height(22.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            MessageCard(stringResource(R.string.home_msg_lost_contact_title), stringResource(R.string.home_msg_lost_contact_body))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            ActionButton(stringResource(R.string.home_btn_troubleshoot), filled = false, onClick = onTroubleshoot)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// No sensor — screen 8i
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.NoSensorContent(onPairSensor: () -> Unit, onEnterCodeManually: () -> Unit, ended: Boolean = false) {
    Column(modifier = Modifier.weight(1f)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
            Text(
                text = stringResource(if (ended) R.string.home_status_sensor_ended else R.string.home_status_not_connected),
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                color = TextMuted,
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .border(2.dp, PlaceholderMuted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SukoonMark(markSize = 48.dp)
            }
            Spacer(Modifier.height(26.dp))
            Text(
                text = stringResource(if (ended) R.string.home_sensor_ended_title else R.string.home_no_sensor_title),
                fontFamily = HeadlineSerifFontFamily,
                fontSize = 25.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(if (ended) R.string.home_sensor_ended_body else R.string.home_no_sensor_body),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = CaptionMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
            ActionButton(stringResource(if (ended) R.string.home_btn_new_sensor else R.string.home_btn_pair_sensor), filled = true, onClick = onPairSensor)
            if (!ended) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEnterCodeManually)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(R.string.home_btn_enter_code), fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Sage)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Preview(showBackground = true, name = "In range")
@Composable
private fun HomeInRangePreview() {
    SukoonTheme { HomeScreen(HomeUiState.InRange(112, TrendDirection.STEADY, listOf(100, 105, 108, 110, 130, 115, 108, 104, 110, 118, 114, 110))) }
}

@Preview(showBackground = true, name = "Low")
@Composable
private fun HomeLowPreview() {
    SukoonTheme { HomeScreen(HomeUiState.Low(63, TrendDirection.FALLING)) }
}

@Preview(showBackground = true, name = "High")
@Composable
private fun HomeHighPreview() {
    SukoonTheme { HomeScreen(HomeUiState.High(243, TrendDirection.RISING, listOf(150, 160, 165, 175, 185, 195, 205, 215, 220, 230, 220, 243))) }
}

@Preview(showBackground = true, name = "Urgent")
@Composable
private fun HomeUrgentPreview() {
    SukoonTheme { HomeScreen(HomeUiState.Urgent(47, TrendDirection.FALLING_FAST)) }
}

@Preview(showBackground = true, name = "Warming up")
@Composable
private fun HomeWarmingUpPreview() {
    SukoonTheme { HomeScreen(HomeUiState.WarmingUp(minutesRemaining = 47)) }
}

@Preview(showBackground = true, name = "Stale")
@Composable
private fun HomeStalePreview() {
    SukoonTheme { HomeScreen(HomeUiState.Stale(104, 12, listOf(120, 118, 115, 112, 108, 105, 100, 95, 90, 85, 80, 78))) }
}

@Preview(showBackground = true, name = "No sensor")
@Composable
private fun HomeNoSensorPreview() {
    SukoonTheme { HomeScreen(HomeUiState.NoSensor) }
}

@Preview(showBackground = true, name = "In range — dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeInRangeDarkPreview() {
    SukoonTheme(darkTheme = true) { HomeScreen(HomeUiState.InRange(112, TrendDirection.STEADY, listOf(100, 105, 108, 110, 130, 115, 108, 104, 110, 118, 114, 110))) }
}

@Preview(showBackground = true, name = "Urgent — Arabic", locale = "ar")
@Composable
private fun HomeUrgentArabicPreview() {
    SukoonTheme { HomeScreen(HomeUiState.Urgent(47, TrendDirection.FALLING_FAST)) }
}
