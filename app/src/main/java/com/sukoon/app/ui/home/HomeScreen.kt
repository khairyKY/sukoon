package com.sukoon.app.ui.home

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import com.sukoon.app.platform.TimeFormat
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.ui.theme.SageDeep
import androidx.compose.material3.TextButton
import com.sukoon.app.ui.logbook.formatAmountLocalized
import com.sukoon.app.insulin.DoseAdvice
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
import com.sukoon.app.ui.components.SukoonMark
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
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
import com.sukoon.app.domain.metrics.GlucoseMetrics
import com.sukoon.app.domain.metrics.TargetRange
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import com.sukoon.app.ui.theme.Motion
import com.sukoon.app.ui.components.NoticeBanner
import com.sukoon.app.ui.components.drawSensor
import com.sukoon.app.ui.theme.Motion.pressScale
import com.sukoon.app.ui.theme.Motion.settleSize
import com.sukoon.app.ui.theme.Motion.staggerIn
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.ui.components.durationText
import com.sukoon.app.ui.graph.GlucoseChart
import com.sukoon.app.ui.graph.GraphRange
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.width
import java.time.Duration
import java.time.Instant
import com.sukoon.app.ui.components.MiniGraph
import com.sukoon.app.ui.theme.NeutralWarm
import com.sukoon.app.ui.components.PullToSync
import androidx.compose.ui.res.pluralStringResource

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
    /** The message under the number and its next step ([HomeBriefs]); null until there's a fresh reading. */
    brief: Brief? = null,
    onTreated: () -> Unit = {},
    onSnooze: () -> Unit = {},
    onAlertEmergencyContact: () -> Unit = {},
    onTroubleshoot: () -> Unit = {},
    onPairSensor: () -> Unit = {},
    onAddFood: () -> Unit = {},
    onAddInsulin: () -> Unit = {},
    /** Opens Today (pause alarms, workout, fasting, sick day). */
    onModes: () -> Unit = {},
    /** What's on, as chips under the header (StatusChips). */
    status: List<StatusChip> = emptyList(),
    /** Rapid insulin still active, shown beside the shortcuts when there is any. */
    insulinOnBoard: Double = 0.0,
    /** Today's numbers, and which of them the user chose to see. */
    stats: Map<HomeStat, Double> = emptyMap(),
    chosenStats: List<HomeStat> = emptyList(),
    onChooseStats: () -> Unit = {},
    /** Pull down: bring in MyFitnessPal's latest; returns what to say. */
    onSync: (suspend () -> String)? = null,
    /** Beta: a correction when you're above your range and not eating ([com.sukoon.app.insulin.Dose.correction]). */
    correction: DoseAdvice.Suggestion? = null,
    /** Beta: a learned number that differs from yours ("Lunch: your meals suggest 1 : 11"). */
    nudge: String? = null,
    onNudge: () -> Unit = {},
    onNudgeDismiss: () -> Unit = {},
    /** Beta: a meal in the last hour with no insulin ([com.sukoon.app.insulin.Dose.mealDose]) and its dose. */
    mealDose: Pair<EventEntity, DoseAdvice.Suggestion>? = null,
    onMealDose: (EventEntity) -> Unit = {},
    /** What shows on Now and in what order (You › Appearance › Now). */
    layout: NowLayout = NowLayout(),
    /** Made-up readings (You › Sensor › Demo): Now says so instead of LIVE. */
    demo: Boolean = false,
    /** Dose suggestions (beta) are on: Now can say why there's no correction. */
    dosing: Boolean = false,
    /** Finger-prick mode's "Prick now": the finger-prick entry. */
    onPrick: () -> Unit = {},
    /** Finger-prick mode: when the next reminder to prick comes (You → Reminders). */
    nextCheck: Instant? = null,
) {
    CompositionLocalProvider(LocalShortcuts provides Shortcuts(onAddFood, onAddInsulin, insulinOnBoard, stats, chosenStats, onChooseStats, correction, nudge, onNudge, onNudgeDismiss, mealDose, onMealDose, onModes, status, layout, demo, dosing, onPrick, nextCheck), LocalEntrance provides Motion.firstShow()) {
        PullToSync(onSync, modifier) {
            HomeContent(state, brief, Modifier.fillMaxSize(), onTreated, onSnooze, onAlertEmergencyContact, onTroubleshoot, onPairSensor)
        }
    }
}

/** Home's quick-log shortcuts and today's stats, reached from any state without threading them through each one. */
private class Shortcuts(
    val onFood: () -> Unit,
    val onInsulin: () -> Unit,
    val insulinOnBoard: Double = 0.0,
    val stats: Map<HomeStat, Double> = emptyMap(),
    val chosenStats: List<HomeStat> = emptyList(),
    val onChooseStats: () -> Unit = {},
    val correction: DoseAdvice.Suggestion? = null,
    val nudge: String? = null,
    val onNudge: () -> Unit = {},
    val onNudgeDismiss: () -> Unit = {},
    val mealDose: Pair<EventEntity, DoseAdvice.Suggestion>? = null,
    val onMealDose: (EventEntity) -> Unit = {},
    val onModes: () -> Unit = {},
    val status: List<StatusChip> = emptyList(),
    val layout: NowLayout = NowLayout(),
    val demo: Boolean = false,
    val dosing: Boolean = false,
    val onPrick: () -> Unit = {},
    val nextCheck: Instant? = null,
)
private val LocalShortcuts = staticCompositionLocalOf { Shortcuts({}, {}) }

/** Now's first appearance (the cards under the number cascade in); held here so crossing 180 doesn't replay it. */
private val LocalEntrance = staticCompositionLocalOf { false }

@Composable
private fun HomeContent(
    state: HomeUiState,
    brief: Brief?,
    modifier: Modifier,
    onTreated: () -> Unit,
    onSnooze: () -> Unit,
    onAlertEmergencyContact: () -> Unit,
    onTroubleshoot: () -> Unit,
    onPairSensor: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (state) {
            is HomeUiState.InRange -> InRangeContent(state, brief)
            is HomeUiState.Low -> LowContent(state, brief, onTreated, onSnooze)
            is HomeUiState.High -> HighContent(state, brief)
            is HomeUiState.Urgent -> UrgentContent(state, brief, onTreated, onAlertEmergencyContact)
            is HomeUiState.WarmingUp -> WarmingUpContent(state)
            is HomeUiState.Stale -> StaleContent(state, onTroubleshoot)
            HomeUiState.NoSensor -> NoSensorContent(onPairSensor)
            HomeUiState.SensorEnded -> NoSensorContent(onPairSensor, ended = true)
            is HomeUiState.FingerPrick -> FingerPrickContent(state, onPairSensor, onTroubleshoot)
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
private fun HomeStatusBar(statusLabel: String, dotColor: Color, pulsing: Boolean, labelColor: Color = CaptionMuted, top: androidx.compose.ui.unit.Dp = 16.dp) {
    if (LocalShortcuts.current.demo) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = top), horizontalArrangement = Arrangement.End) {
            Text(stringResource(R.string.home_status_demo), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.5.sp, color = PillLowText,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(PillLowBg).padding(horizontal = 10.dp, vertical = 3.dp))
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = top),
        horizontalArrangement = Arrangement.End, // the design mock's fake "9:41" clock is gone; the system bar shows real time
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PulsingDot(color = dotColor, pulsing = pulsing)
            Text(text = statusLabel, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp, color = labelColor)
        }
    }
}

@Composable
private fun HomeAppBar(dimmed: Boolean = false) {
    Column {
        HomeAppBarRow(dimmed)
        StatusChips(LocalShortcuts.current.status)
        // A notice from Sukoon, under the bar on the calm screens (not on a low or urgent one).
        NoticeBanner(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp))
    }
}

@Composable
private fun HomeAppBarRow(dimmed: Boolean) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            // The sliders icon alone didn't say "pause alarms, workout, fasting": it has its word.
            Row(
                Modifier.padding(end = 4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                    .clickable(onClick = shortcuts.onModes).padding(start = 10.dp, end = 12.dp).height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(painterResource(R.drawable.ic_modes), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.today_title), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
            ShortcutIcon(R.drawable.ic_food, stringResource(R.string.home_add_food), shortcuts.onFood)
            ShortcutIcon(R.drawable.ic_insulin, stringResource(R.string.home_add_insulin), shortcuts.onInsulin)
        }
    }
}

@Composable
private fun ShortcutIcon(iconRes: Int, description: String, onClick: () -> Unit) {
    // A 40 dp circle in a 48 dp touch target.
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(iconRes), contentDescription = description, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
        }
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
    // Left to right in every language: the arrow is a trend on a timeline, so it stays right of the number.
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = value, fontFamily = HeadlineSerifFontFamily, fontWeight = FontWeight.Light, fontSize = fontSize, color = color)
            if (trend != null) com.sukoon.app.ui.components.TrendArrow(trend, color, size = 34.dp, modifier = Modifier.padding(top = 18.dp), label = trendArrow(trend))
        }
    }
}

private fun trendArrow(trend: TrendDirection): String = trend.arrow

private fun digits(n: Int) = String.format(Locale.getDefault(), "%d", n)

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
private fun ColumnScope.InRangeContent(state: HomeUiState.InRange, brief: Brief?) = CalmContent(
    pill = { StatusPill(stringResource(R.string.home_pill_in_range), com.sukoon.app.ui.theme.InRange, com.sukoon.app.ui.theme.InRangeMist, com.sukoon.app.ui.theme.InRangeDeep) },
    mgDl = state.glucoseMgDl, trend = state.trend, color = MaterialTheme.colorScheme.onBackground, dot = Sage,
    readings = state.recentReadings, brief = brief, updated = true,
)

/**
 * In range and high (design A, "Calm: one number, one next step"): chips, the number, then what
 * the user keeps under it (Make Now yours): next step, the 3-hour graph, today's numbers, dose cards.
 * Arranged in You › Appearance › Now.
 */
@Composable
private fun ColumnScope.CalmContent(
    pill: @Composable () -> Unit,
    mgDl: Int,
    trend: TrendDirection,
    color: Color,
    dot: Color,
    readings: List<GlucoseReading>,
    brief: Brief?,
    updated: Boolean,
) {
    val s = LocalShortcuts.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var viewport by remember { mutableIntStateOf(0) }
    var doseTop by remember { mutableIntStateOf(-1) }
    val hasDose = s.layout.shows(NowPart.DOSE) && (s.mealDose != null || s.correction != null || s.nudge != null)
    Box(Modifier.weight(1f).onSizeChanged { viewport = it.height }) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
        HomeAppBarRow(dimmed = false)
        HomeStatusBar(stringResource(R.string.home_status_live), dot, pulsing = true, top = 2.dp)
        // Alarms paused, a missed long-acting, supplies, a meal watch: these show even with chips off.
        val iob = if (s.insulinOnBoard >= 0.05) StatusChip(stringResource(R.string.home_iob, String.format(Locale.getDefault(), "%.1f", s.insulinOnBoard)), onClick = null) else null
        // Chips fade in as their row opens and out as it closes; one more or one fewer settles the row's size.
        val chips = listOfNotNull(iob) + if (s.layout.shows(NowPart.CHIPS)) s.status else s.status.filter { it.attention }
        val lastChips = remember { arrayOf(chips) }
        val shownChips = if (chips.isNotEmpty()) chips.also { lastChips[0] = it } else lastChips[0]
        androidx.compose.animation.AnimatedVisibility(chips.isNotEmpty(), enter = Motion.appear(), exit = Motion.vanish()) {
            StatusChips(shownChips, Modifier.padding(horizontal = 16.dp).settleSize())
        }
        NoticeBanner(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            pill()
            Spacer(Modifier.height(8.dp))
            HeroNumber(digits(countUp(mgDl)), trend, color)
            Motion.Swap(
                listOfNotNull(
                    stringResource(R.string.home_unit_mgdl),
                    trendLabel(trend),
                    if (updated) stringResource(R.string.home_updated_now) else null,
                ).joinToString(" · "),
            ) { Text(text = it, fontSize = 12.sp, color = CaptionMuted) }
        }
        Spacer(Modifier.height(18.dp))
        // List & card stagger on Now's first appearance (not on a low or urgent screen: those show at once).
        val entering = LocalEntrance.current
        s.layout.order.filter(s.layout::shows).forEachIndexed { i, part ->
            val enter = if (entering) Modifier.staggerIn(i) else Modifier
            when (part) {
                NowPart.NEXT -> Box(Modifier.fillMaxWidth().then(enter).settleSize()) { NextCard(brief) }
                NowPart.GRAPH -> Box(Modifier.fillMaxWidth().then(enter)) { Column { RecentChart(readings); Spacer(Modifier.height(16.dp)) } }
                NowPart.STATS -> Box(Modifier.fillMaxWidth().then(enter)) { Column { StatsRow(); Spacer(Modifier.height(12.dp)) } }
                NowPart.DOSE -> Box(Modifier.onGloballyPositioned { doseTop = it.positionInParent().y.toInt() }.then(enter).settleSize()) { DoseCards() }
                else -> Unit
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    // More under the fold gets a "more below" arrow (like a chat's jump-to-latest); a hidden beta card is named.
    val doseHidden = hasDose && doseTop >= 0 && viewport > 0 && doseTop > scroll.value + viewport - with(density) { 56.dp.roundToPx() }
    androidx.compose.animation.AnimatedVisibility(
        visible = doseHidden || scroll.canScrollForward,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
    ) {
        MoreBelow(if (doseHidden) stringResource(R.string.home_more_below) else null) {
            scope.launch { if (doseHidden) scroll.animateScrollTo(doseTop) else scroll.animateScrollTo(scroll.value + viewport * 3 / 4) }
        }
    }
    }
}

/** A chevron floating at the bottom of Now; with [label] ("Beta suggestion below") a pill that says what's there. */
@Composable
private fun MoreBelow(label: String?, onClick: () -> Unit) {
    val more = stringResource(R.string.home_more_content)
    Row(
        Modifier
            .shadow(6.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(if (label != null) Sage else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label ?: more }
            .padding(start = if (label != null) 14.dp else 13.dp, end = if (label != null) 16.dp else 13.dp)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val ink = if (label != null) Color.White else Sage
        Canvas(Modifier.size(14.dp)) {
            val w = 2.dp.toPx()
            drawLine(ink, Offset(size.width * 0.15f, size.height * 0.35f), Offset(size.width * 0.5f, size.height * 0.7f), w, StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.5f, size.height * 0.7f), Offset(size.width * 0.85f, size.height * 0.35f), w, StrokeCap.Round)
        }
        if (label != null) Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
    }
}


// ---------------------------------------------------------------------------------------------
// Low — screens 5b / 7c
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.LowContent(state: HomeUiState.Low, brief: Brief?, onTreated: () -> Unit, onSnooze: () -> Unit) {
    Column(modifier = Modifier.weight(1f).padding(horizontal = 22.dp)) {
        HomeStatusBar(stringResource(R.string.home_status_live), StateLow, pulsing = true)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StatusPill(stringResource(R.string.home_pill_low), StateLow, PillLowBg, PillLowText)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            HeroNumber(digits(state.glucoseMgDl), state.trend, StateLow, fontSize = 104.sp)
            Text(text = "${stringResource(R.string.home_unit_mgdl)} · ${trendLabel(state.trend)}", fontSize = 12.sp, color = CaptionMuted)
        }
        Text(
            text = stringResource(
                when {
                    brief is Brief.Treated -> R.string.home_headline_treated
                    brief is Brief.Low && brief.again -> R.string.home_headline_still_low
                    else -> R.string.home_headline_going_low
                },
            ),
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
            content = if (brief is Brief.Treated) {
                treatedText(brief)
            } else if (brief is Brief.Low && brief.again) {
                androidx.compose.ui.text.AnnotatedString(stringResource(R.string.home_low_again_action))
            } else {
                buildAnnotatedString {
                    append(stringResource(R.string.home_low_action_prefix))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.home_low_action_bold)) }
                    append(stringResource(R.string.home_low_action_suffix))
                }
            },
        )
        if (brief is Brief.Low && brief.breakFast) {
            Text(stringResource(R.string.home_low_break_fast), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = StateLow, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        InsulinStillWorking(brief)
        OnItNote() // a follower said "I'm on it"
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (brief is Brief.Treated) {
                TreatedButton(brief.at)
                ActionButton(stringResource(R.string.home_btn_log_carbs), filled = false, onClick = LocalShortcuts.current.onFood)
            } else {
                ActionButton(stringResource(R.string.home_btn_treated), filled = true, onClick = treatedWithHaptic(onTreated))
                ActionButton(stringResource(R.string.home_btn_snooze_for, com.sukoon.app.ui.components.durationText(LocalContext.current, ((LocalContext.current.applicationContext as? com.sukoon.app.SukoonApp)?.container?.settings?.alarmSettings?.lowSnoozeMinutes ?: 15).toLong())), filled = false, onClick = onSnooze)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// High — screen 8e
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.HighContent(state: HomeUiState.High, brief: Brief?) = CalmContent(
    pill = { StatusPill(stringResource(R.string.home_pill_high), StateHigh, PillHighBg, PillHighText) },
    mgDl = state.glucoseMgDl, trend = state.trend, color = StateHigh, dot = StateHigh,
    readings = state.recentReadings, brief = brief, updated = false,
)

// ---------------------------------------------------------------------------------------------
// Urgent — screen 8f
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.UrgentContent(state: HomeUiState.Urgent, brief: Brief?, onTreated: () -> Unit, onAlertEmergencyContact: () -> Unit) {
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
            HeroNumber(digits(state.glucoseMgDl), state.trend, StateLow, fontSize = 104.sp)
            Text(text = "${stringResource(R.string.home_unit_mgdl)} · ${trendLabel(state.trend)}", fontSize = 12.sp, color = CaptionMuted)
        }
        Spacer(Modifier.height(20.dp))
        ActionCard(
            labelColor = StateLow,
            content = if (brief is Brief.Treated) {
                treatedText(brief)
            } else {
                buildAnnotatedString {
                    append(stringResource(R.string.home_urgent_action_prefix))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.home_urgent_action_bold)) }
                    append(stringResource(R.string.home_urgent_action_suffix))
                }
            },
        )
        InsulinStillWorking(brief)
        OnItNote() // a follower said "I'm on it"
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (brief is Brief.Treated) TreatedButton(brief.at)
            else ActionButton(stringResource(R.string.home_btn_treated), filled = true, onClick = treatedWithHaptic(onTreated))
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

/** A firm buzz with the press, so it registers even before the screen changes. */
@Composable
private fun treatedWithHaptic(onTreated: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onTreated()
    }
}

/** In place of "I've treated it" once pressed: the 15 minutes counting down, then "check now". */
@Composable
private fun TreatedButton(at: Instant) {
    val left by produceState(treatedMinutesLeft(at), at) {
        while (value > 0) {
            delay(10_000)
            value = treatedMinutesLeft(at)
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SageMist).padding(15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = com.sukoon.app.ui.theme.SageDeep, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (left > 0) pluralStringResource(R.plurals.home_treated_wait, left.toInt(), left) else stringResource(R.string.home_treated_check),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = com.sukoon.app.ui.theme.SageDeep,
        )
    }
}

private fun treatedMinutesLeft(at: Instant) = ((Duration.between(Instant.now(), at.plusSeconds(15 * 60)).seconds + 59) / 60).coerceAtLeast(0).toInt()

/** The 15-15 rule once a low is treated: wait the 15 minutes, then check before having more. */
@Composable
private fun treatedText(brief: Brief.Treated): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.AnnotatedString(stringResource(R.string.home_treated_action, HM.format(brief.at), HM.format(brief.at.plusSeconds(15 * 60))))

/** Under a low's card: rapid insulin still working means this low may need more than one round. */
@Composable
private fun InsulinStillWorking(brief: Brief?) {
    val units = (brief as? Brief.Low)?.insulin?.takeIf { it > 0 } ?: return
    Text(
        stringResource(R.string.home_low_insulin, String.format(Locale.getDefault(), "%.1f", units)),
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = CaptionMuted,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
    )
}

/**
 * The last 3 hours as a line over the 70–180 band: the same chart as Trends → Graph, so it reads
 * the same way (it replaced a strip of 15-minute bars that didn't say what it showed).
 */
@Composable
private fun RecentChart(readings: List<GlucoseReading>, dimmed: Boolean = false) {
    if (readings.isEmpty()) return
    // Home keeps its bar strip (Kai, 2026-10-07); Trends has the line chart.
    Column(Modifier.padding(horizontal = 22.dp)) {
        MiniGraph(readings, now = Instant.now(), dimmed = dimmed)
        Text(
            text = stringResource(R.string.home_last_3_hours).uppercase(),
            fontSize = 9.5.sp,
            letterSpacing = 1.sp,
            color = NeutralWarm,
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Today's chosen numbers (design "Home · your stats"); "Choose" opens the picker. */
@Composable
private fun StatsRow() {
    val s = LocalShortcuts.current
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_today).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.home_choose_stats),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = s.onChooseStats).padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
        if (s.chosenStats.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.chosenStats.forEach { stat -> StatTile(stat, s.stats[stat], Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatTile(stat: HomeStat, value: Double?, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(statLabel(stat), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted, maxLines = 1)
        Motion.Swap(value?.let { statValue(stat, it) } ?: "—", Modifier.padding(top = 2.dp), rise = true) {
            Text(it, fontFamily = HeadlineSerifFontFamily, fontSize = 21.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
        }
        Text(statUnit(stat), fontSize = 11.5.sp, color = CaptionMuted, maxLines = 1)
    }
}

/** The message and the one thing to do now, as one card (design A); the low screens word their own. */
@Composable
private fun NextCard(brief: Brief?) {
    if (brief == null || brief is Brief.Low || brief is Brief.Treated) return
    val s = LocalShortcuts.current
    val (title, base) = briefText(brief)
    val working = (brief as? Brief.VeryHighFor)?.insulin?.takeIf { it > 0 } ?: (brief as? Brief.InsulinWorking)?.units
    // With insulin working: either the correction card below takes over, or it says why there is none.
    val body = if (working != null && s.dosing && s.correction == null) base + " " + stringResource(R.string.home_no_correction_yet) else base
    val step = brief.step?.takeUnless { it == Step.DONT_STACK && s.correction != null }
    Column(
        Modifier
            .padding(horizontal = 20.dp).padding(bottom = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Sage.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.home_next_step).uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp, color = Sage)
        Text(title, fontFamily = HeadlineSerifFontFamily, fontSize = 18.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(body, fontSize = 13.sp, lineHeight = 18.sp, color = CaptionMuted)
        if (step != null) {
            Text(stepText(step), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
            if (step == Step.LOG_MEAL) {
                Text(
                    stringResource(R.string.home_add_food),
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Sage,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = LocalShortcuts.current.onFood).padding(vertical = 8.dp),
                )
            }
        } else {
            Text(stringResource(R.string.home_nothing_to_do), fontSize = 12.sp, color = CaptionMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Beta dose cards: a meal with no insulin, a correction, a learned ratio. */
@Composable
private fun DoseCards() {
    val s = LocalShortcuts.current
    if (s.mealDose == null && s.correction == null && s.nudge == null) return
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.mealDose?.let { (meal, dose) -> MealDoseCard(meal, dose) { s.onMealDose(meal) } }
        s.correction?.let { CorrectionCard(it, s.onInsulin) }
        s.nudge?.let { NudgeCard(it, s.onNudge, s.onNudgeDismiss) }
    }
}

/** Beta: you ate in the last hour and logged no insulin. "Log it" opens insulin for that meal, with the maths. */
@Composable
private fun MealDoseCard(meal: EventEntity, dose: DoseAdvice.Suggestion, onLog: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, Sage, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.home_meal_eyebrow).uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp, color = Sage)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatAmountLocalized(dose.units) + stringResource(R.string.logbook_unit_units),
                fontFamily = HeadlineSerifFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.home_correction_log),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Sage).clickable(onClick = onLog).padding(horizontal = 18.dp, vertical = 12.dp),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
        Text(
            stringResource(R.string.home_meal_body, formatAmountLocalized(meal.value ?: 0.0), TimeFormat.of().format(java.time.Instant.ofEpochMilli(meal.timestampMillis))),
            fontSize = 13.sp,
            color = CaptionMuted,
        )
    }
}

/** Beta: a learned number differs from yours. It changes nothing: Take a look opens Learning, Not now hides this one. */
@Composable
private fun NudgeCard(text: String, onLook: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SageMist)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(stringResource(R.string.nudge_eyebrow).uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp, color = SageDeep)
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nudge_not_now), color = SageDeep) }
            TextButton(onClick = onLook) { Text(stringResource(R.string.nudge_look), color = SageDeep, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Beta: above your range and not eating. The maths, and "Log it" opens the insulin entry with it suggested. */
@Composable
private fun CorrectionCard(c: DoseAdvice.Suggestion, onLog: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, Sage, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.home_correction_eyebrow).uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp, color = Sage)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatAmountLocalized(c.units) + stringResource(R.string.logbook_unit_units),
                fontFamily = HeadlineSerifFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.home_correction_log),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Sage).clickable(onClick = onLog).padding(horizontal = 18.dp, vertical = 12.dp),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
        Text(
            stringResource(R.string.home_correction_taken),
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onLog).padding(vertical = 6.dp),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = SageDeep,
        )
        Text(
            stringResource(R.string.home_correction_body, c.glucose ?: 0, c.target, String.format(Locale.getDefault(), "%.0f", c.factor ?: 0.0)) +
                if (c.onBoardUsed >= 0.05) " " + stringResource(R.string.home_correction_less_working, String.format(Locale.getDefault(), "%.1f", c.onBoardUsed)) else "",
            fontSize = 13.sp,
            color = CaptionMuted,
        )
    }
}

@Composable
private fun stepText(step: Step): String = stringResource(
    when (step) {
        // Below 70 the low screens' own cards carry these; never shown here.
        Step.FAST_CARBS, Step.RECHECK, Step.CARBS_READY -> R.string.home_step_carbs_ready
        Step.KETONES -> R.string.home_step_ketones
        Step.WATER -> R.string.home_step_water
        Step.WATER_WALK -> R.string.home_step_water_walk
        Step.DONT_STACK -> R.string.home_step_dont_stack
        Step.LET_IT_SETTLE -> R.string.home_step_let_it_settle
        Step.LOG_MEAL -> R.string.home_step_log_meal
        Step.SNACK_IF_MEAL_FAR -> R.string.home_step_snack
        Step.CARBS_BY_BED -> R.string.home_step_carbs_by_bed
        Step.CARBS_WITH_YOU -> R.string.home_step_carbs_with_you
        Step.BEDTIME_SNACK -> R.string.home_step_bedtime_snack
        Step.FINGERPRICK_FIRST -> R.string.home_step_fingerprick
        Step.BREAK_FAST -> R.string.home_step_break_fast
        Step.RECHECK_HOUR -> R.string.home_step_recheck_hour
        Step.URGENT_CARE -> R.string.home_step_urgent_care
        Step.EXTRA_INSULIN -> R.string.home_step_extra_insulin
    },
)

@Composable
private fun briefText(brief: Brief): Pair<String, String> = briefLines(LocalContext.current, brief)

/** Title and body for [brief] (Now's message card, and the Now widget). */
internal fun briefLines(context: android.content.Context, brief: Brief): Pair<String, String> {
    fun units(u: Double) = String.format(Locale.getDefault(), "%.1f", u)
    return when (brief) {
        is Brief.HeadingLow -> context.getString(R.string.home_brief_heading_low_title) to listOfNotNull(
            context.resources.getQuantityString(R.plurals.home_brief_heading_low_body, brief.minutes.toInt(), brief.minutes),
            if (brief.insulin > 0) context.getString(R.string.home_insulin_working, units(brief.insulin)) else null,
        ).joinToString(" ")
        is Brief.VeryHighFor -> context.getString(R.string.home_brief_very_high_title) to listOfNotNull(
            context.getString(R.string.home_brief_very_high_body, durationText(context, brief.minutes)),
            if (brief.insulin > 0) context.getString(R.string.home_insulin_working, units(brief.insulin)) else null,
        ).joinToString(" ")
        is Brief.Rebound -> context.getString(R.string.home_brief_rebound_title) to context.getString(R.string.home_brief_rebound_body, HM.format(brief.lowAt))
        is Brief.InsulinWorking -> context.getString(R.string.home_brief_insulin_title) to context.getString(R.string.home_insulin_working, units(brief.units))
        is Brief.HighFor -> context.getString(R.string.home_brief_high_title) to
            if (brief.minutes < 5) context.getString(R.string.home_brief_high_just_now) else context.getString(R.string.home_brief_high_body, durationText(context, brief.minutes))
        is Brief.AfterMeal -> context.getString(R.string.home_brief_meal_title) to (
            if (brief.grams != null) context.getString(R.string.home_brief_meal_body_grams, brief.grams, HM.format(brief.at))
            else context.getString(R.string.home_brief_meal_body, HM.format(brief.at))
            )
        is Brief.LateRise -> context.getString(R.string.home_brief_late_title) to context.getString(
            when {
                brief.protein != null && brief.fat != null -> R.string.home_brief_late_both
                brief.protein != null -> R.string.home_brief_late_protein
                else -> R.string.home_brief_late_fat
            },
            HM.format(brief.at), brief.protein ?: brief.fat ?: 0, brief.fat ?: 0,
        )
        is Brief.BackFromLow -> context.getString(R.string.home_brief_back_title) to context.getString(R.string.home_brief_back_body, HM.format(brief.lowAt))
        Brief.DawnRise -> context.getString(R.string.home_brief_dawn_title) to context.getString(R.string.home_brief_dawn_body)
        Brief.RisingNoMeal -> context.getString(R.string.home_brief_rising_title) to context.getString(R.string.home_brief_rising_body)
        is Brief.Bedtime -> context.getString(R.string.home_brief_bedtime_title) to
            context.getString(if (brief.insulin) R.string.home_brief_bedtime_insulin else R.string.home_brief_bedtime_falling, brief.mgDl)
        is Brief.ActiveToday -> context.getString(R.string.home_brief_active_title) to context.getString(R.string.home_brief_active_body)
        Brief.NewSensor -> context.getString(R.string.home_brief_new_sensor_title) to context.getString(R.string.home_brief_new_sensor_body)
        Brief.QuietNight -> context.getString(R.string.home_brief_quiet_night_title) to context.getString(R.string.home_brief_quiet_night_body)
        Brief.GoodNight -> context.getString(R.string.home_brief_good_night_title) to context.getString(R.string.home_brief_good_night_body)
        is Brief.InRangeFor -> context.getString(R.string.home_brief_streak_title) to context.getString(R.string.home_brief_streak_body, durationText(context, brief.minutes))
        is Brief.GoodDay -> context.getString(R.string.home_brief_good_day_title) to context.getString(R.string.home_brief_good_day_body, brief.percent)
        is Brief.Steady -> STEADY[brief.variant % STEADY.size].let { (title, body) -> context.getString(title) to context.getString(body) }
        Brief.BreakFastHigh -> context.getString(R.string.home_brief_break_fast_high_title) to context.getString(R.string.home_brief_break_fast_high_body)
        is Brief.FastCheck -> context.getString(R.string.home_brief_fast_check_title, brief.mgDl) to context.getString(R.string.home_brief_fast_check_body)
        is Brief.Ketones -> context.getString(R.string.home_brief_ketones_title, String.format(Locale.getDefault(), "%.1f", brief.mmol)) to context.getString(
            when (brief.band) {
                com.sukoon.app.modes.Modes.KetoneBand.DANGER -> R.string.home_brief_ketones_danger
                com.sukoon.app.modes.Modes.KetoneBand.HIGH -> R.string.home_brief_ketones_high
                else -> R.string.home_brief_ketones_raised
            },
        )
        Brief.KetonesDue -> context.getString(R.string.home_brief_ketones_due_title) to context.getString(R.string.home_brief_ketones_due_body)
        is Brief.Low, is Brief.Treated -> "" to "" // the low screens word these (see BriefCards)
    }
}

private val STEADY = listOf(
    R.string.home_brief_steady_0_title to R.string.home_brief_steady_0_body,
    R.string.home_brief_steady_1_title to R.string.home_brief_steady_1_body,
    R.string.home_brief_steady_2_title to R.string.home_brief_steady_2_body,
    R.string.home_brief_steady_3_title to R.string.home_brief_steady_3_body,
)

private val HM: DateTimeFormatter get() = TimeFormat.of()

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
            WarmupSensor(progress = if (state.minutesRemaining > 0) 1f - state.minutesRemaining / WARMUP_MINUTES else 1f)
            if (state.minutesRemaining > 0) {
                Text(
                    buildAnnotatedString {
                        append(String.format(java.util.Locale.getDefault(), "%d", state.minutesRemaining))
                        withStyle(SpanStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = CaptionMuted)) {
                            append(" " + stringResource(R.string.logbook_unit_minutes))
                        }
                    },
                    fontFamily = HeadlineSerifFontFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 54.sp,
                    color = PillHighText,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            state.provisionalMgDl?.let { mgDl ->
                Text(
                    text = stringResource(R.string.home_warmup_provisional, mgDl),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PlaceholderMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(if (state.minutesRemaining > 0) 6.dp else 28.dp))
            Text(
                text = stringResource(R.string.home_getting_ready),
                fontFamily = HeadlineSerifFontFamily,
                fontSize = 23.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_warmup_sub),
                fontSize = 13.sp,
                color = CaptionMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 0.dp)) {
            MessageCard(title = null, body = stringResource(if (state.provisionalMgDl != null) R.string.home_warmup_provisional_body else R.string.home_warmup_body))
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** A Libre 2 warms up for an hour before its first reading. */
private const val WARMUP_MINUTES = 60f

/**
 * The warm-up picture: the sensor in line, kept minimal (disc, bevel, the filament port) inside a
 * ring that fills toward the first reading, an amber glow breathing behind it on the live pulse's
 * 2.4 s loop. Reduced motion: the glow holds still and the ring is drawn at once.
 */
@Composable
private fun WarmupSensor(progress: Float) {
    val reduced = Motion.reduced()
    val breath by if (reduced) {
        remember { mutableFloatStateOf(0.6f) }
    } else {
        rememberInfiniteTransition(label = "warmupGlow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(Motion.LOOP / 2, easing = Motion.Standard), RepeatMode.Reverse),
            label = "breath",
        )
    }
    val sweep = remember { Animatable(if (reduced) progress else 0f) }
    LaunchedEffect(progress) { sweep.animateTo(progress, tween(if (reduced) 0 else Motion.SLOW, easing = Motion.Calm)) }
    val track = PillNeutralBg
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(Modifier.size(230.dp)) {
        val c = center
        // The glow: breathes 0.94 → 1.04 in size and 35 % → 100 % in strength.
        val glowR = size.minDimension / 2f * (0.94f + 0.10f * breath)
        drawCircle(
            Brush.radialGradient(
                0f to StateHigh.copy(alpha = 0.42f * (0.35f + 0.65f * breath)),
                0.45f to StateHigh.copy(alpha = 0.14f * (0.35f + 0.65f * breath)),
                1f to Color.Transparent,
                center = c,
                radius = glowR,
            ),
            glowR,
            c,
        )
        val ring = 86.dp.toPx()
        val ringWidth = 10.dp.toPx()
        drawCircle(track, ring, c, style = Stroke(ringWidth))
        drawArc(StateHigh, -90f, 360f * sweep.value, false, Offset(c.x - ring, c.y - ring), Size(ring * 2, ring * 2), style = Stroke(ringWidth, cap = StrokeCap.Round))
        // The sensor sits just inside the ring; the ring takes the adhesive's place.
        drawSensor(c, 76.dp.toPx(), ink, adhesive = false)
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
        RecentChart(state.recentReadings, dimmed = true)
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
private fun ColumnScope.NoSensorContent(onPairSensor: () -> Unit, ended: Boolean = false) {
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
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Finger-prick mode: the sensor ended or went quiet, Now runs on your pricks
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.FingerPrickContent(state: HomeUiState.FingerPrick, onPairSensor: () -> Unit, onTroubleshoot: () -> Unit) {
    val s = LocalShortcuts.current
    val now = Instant.now()
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        HomeAppBar()
        // Why there's no sensor number, in amber (not an alarm): a tap goes where it gets fixed.
        val (title, body) = when (state.why) {
            HomeUiState.FingerPrick.Why.SENSOR_ENDED -> stringResource(R.string.home_fp_ended_title) to stringResource(R.string.home_fp_ended_body)
            HomeUiState.FingerPrick.Why.NO_READINGS -> stringResource(R.string.home_fp_no_readings_title, state.minutesSinceReading ?: 0) to stringResource(R.string.home_fp_no_readings_body)
            HomeUiState.FingerPrick.Why.NO_SENSOR -> stringResource(R.string.home_fp_no_sensor_title) to stringResource(R.string.home_fp_no_sensor_body)
        }
        Row(
            Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PillHighBg)
                .clickable(onClick = if (state.why == HomeUiState.FingerPrick.Why.NO_READINGS) onTroubleshoot else onPairSensor)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = PillHighText)
                Text(body, fontSize = 12.sp, color = PillHighText)
            }
            Text("›", fontSize = 20.sp, color = PillHighText)
        }

        Column(
            Modifier.padding(horizontal = 16.dp).padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface).padding(20.dp),
        ) {
            val last = state.last
            Text(stringResource(R.string.home_fp_last), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = NeutralWarm)
            if (last == null) {
                Text(stringResource(R.string.home_fp_none), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 6.dp))
                Text(stringResource(R.string.home_fp_none_body), fontSize = 13.sp, lineHeight = 19.sp, color = CaptionMuted, modifier = Modifier.padding(top = 4.dp))
            } else {
                val color = when (GlucoseMetrics.bracketFor(last.mgDl, TargetRange.high)) {
                    GlucoseMetrics.RangeBracket.VERY_LOW, GlucoseMetrics.RangeBracket.LOW -> StateLow
                    GlucoseMetrics.RangeBracket.IN_RANGE -> MaterialTheme.colorScheme.onBackground
                    else -> StateHigh
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroNumber(digits(last.mgDl), trend = null, color = color, fontSize = 80.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_unit_mgdl), fontSize = 14.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
                    when (color) {
                        StateLow -> StatusPill(stringResource(R.string.home_pill_low), StateLow, PillLowBg, PillLowText)
                        StateHigh -> StatusPill(stringResource(R.string.home_pill_high), StateHigh, PillHighBg, PillHighText)
                        else -> StatusPill(stringResource(R.string.home_pill_in_range), com.sukoon.app.ui.theme.InRange, com.sukoon.app.ui.theme.InRangeMist, com.sukoon.app.ui.theme.InRangeDeep)
                    }
                }
                val age = Duration.between(last.at, now).toMinutes().coerceAtLeast(0)
                val ago = if (age < 60) stringResource(R.string.home_fp_ago_min, age.toInt()) else stringResource(R.string.home_fp_ago_h, (age / 60).toInt())
                val finger = last.finger?.let { runCatching { com.sukoon.app.insulin.Finger.valueOf(it) }.getOrNull() }?.let { com.sukoon.app.ui.logbook.fingerName(it) }
                Text(listOfNotNull(ago, finger).joinToString(" · "), fontSize = 13.5.sp, color = CaptionMuted)
                // Fresh enough to dose on (FingerPricks.FRESH), or not: then it asks for a new one.
                val fresh = Duration.between(last.at, now) <= com.sukoon.app.domain.metrics.FingerPricks.FRESH
                Text(stringResource(if (fresh) R.string.home_fp_no_arrow else R.string.home_fp_old), fontSize = 12.5.sp, lineHeight = 18.sp, color = TextMuted, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.height(14.dp))
            ActionButton(stringResource(R.string.home_fp_prick_now), filled = true, onClick = s.onPrick)
            s.nextCheck?.let { next ->
                Text(
                    stringResource(R.string.home_fp_next, next.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))),
                    fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally),
                )
            }
            if (s.insulinOnBoard >= 0.05) {
                Text(
                    stringResource(R.string.home_iob, String.format(Locale.getDefault(), "%.1f", s.insulinOnBoard)),
                    fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally),
                )
            }
        }
        if (state.today.isNotEmpty()) PricksToday(state.today)
        Spacer(Modifier.height(14.dp))
        DoseCards()
    }
}

/** Today's pricks as rings on the in-range band, the newest filled. ponytail: evenly spaced, not to scale in time; the last 8. */
@Composable
private fun PricksToday(pricks: List<com.sukoon.app.domain.metrics.FingerPricks.Prick>) {
    val shown = pricks.takeLast(8)
    val zone = ZoneId.systemDefault()
    val time = remember { DateTimeFormatter.ofPattern("H:mm") }
    Column(
        Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_fp_today), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = NeutralWarm, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.home_fp_today_summary, pricks.size, kotlin.math.round(pricks.map { it.mgDl }.average()).toInt()), fontSize = 12.5.sp, color = CaptionMuted)
        }
        val band = SageMist
        val ink = Sage
        val description = shown.joinToString(", ") { "${it.mgDl} ${it.at.atZone(zone).format(time)}" }
        Canvas(Modifier.fillMaxWidth().height(72.dp).padding(top = 8.dp).semantics { contentDescription = description }) {
            fun y(mgDl: Int) = size.height * (1f - ((mgDl.coerceIn(40, 300) - 40) / 260f))
            drawRect(band.copy(alpha = 0.6f), Offset(0f, y(180)), androidx.compose.ui.geometry.Size(size.width, y(70) - y(180)))
            val step = size.width / shown.size
            shown.forEachIndexed { i, p ->
                val c = Offset(step * (i + 0.5f), y(p.mgDl))
                if (i == shown.lastIndex) drawCircle(ink, 6.dp.toPx(), c)
                else drawCircle(ink, 6.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            shown.forEach { p ->
                Text(p.at.atZone(zone).format(time), fontSize = 10.5.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

/** A reading every 15 minutes, the last one now. */
private fun previewReadings(vararg mgDl: Int): List<GlucoseReading> {
    val now = java.time.Instant.now()
    return mgDl.mapIndexed { i, v -> GlucoseReading(now.minusSeconds((mgDl.size - 1 - i) * 900L), v, TrendDirection.STEADY, SourceKind.SIMULATED) }
}

@Preview(showBackground = true, name = "In range")
@Composable
private fun HomeInRangePreview() {
    SukoonTheme { HomeScreen(HomeUiState.InRange(112, TrendDirection.STEADY, previewReadings(100, 105, 108, 110, 130, 115, 108, 104, 110, 118, 114, 110)), brief = Brief.Steady(0)) }
}

@Preview(showBackground = true, name = "Low")
@Composable
private fun HomeLowPreview() {
    SukoonTheme { HomeScreen(HomeUiState.Low(63, TrendDirection.FALLING)) }
}

@Preview(showBackground = true, name = "High")
@Composable
private fun HomeHighPreview() {
    SukoonTheme { HomeScreen(HomeUiState.High(243, TrendDirection.RISING, previewReadings(150, 160, 165, 175, 185, 195, 205, 215, 220, 230, 220, 243)), brief = Brief.HighFor(70, false)) }
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
    SukoonTheme { HomeScreen(HomeUiState.Stale(104, 12, previewReadings(120, 118, 115, 112, 108, 105, 100, 95, 90, 85, 80, 78))) }
}

@Preview(showBackground = true, name = "No sensor")
@Composable
private fun HomeNoSensorPreview() {
    SukoonTheme { HomeScreen(HomeUiState.NoSensor) }
}

@Preview(showBackground = true, name = "In range — dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeInRangeDarkPreview() {
    SukoonTheme(darkTheme = true) { HomeScreen(HomeUiState.InRange(112, TrendDirection.STEADY, previewReadings(100, 105, 108, 110, 130, 115, 108, 104, 110, 118, 114, 110)), brief = Brief.RisingNoMeal) }
}

@Preview(showBackground = true, name = "Urgent — Arabic", locale = "ar")
@Composable
private fun HomeUrgentArabicPreview() {
    SukoonTheme { HomeScreen(HomeUiState.Urgent(47, TrendDirection.FALLING_FAST)) }
}
