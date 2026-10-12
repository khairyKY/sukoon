package com.sukoon.app.ui.insights

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.sukoon.app.ui.theme.Motion
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.LayoutDirection
import com.sukoon.app.insights.Finding
import com.sukoon.app.insights.InEffect
import com.sukoon.app.insights.InsightAction
import com.sukoon.app.insights.InsightDigest
import com.sukoon.app.insights.LowCause
import com.sukoon.app.insights.LowsStory
import com.sukoon.app.insights.NotEnough
import com.sukoon.app.platform.TimeFormat
import com.sukoon.app.ui.logbook.Eyebrow
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.SageMist
import java.time.LocalTime
import kotlin.math.abs
import com.sukoon.app.insights.Insight
import com.sukoon.app.insights.Level
import com.sukoon.app.insights.MealSlot
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import java.util.Locale
import com.sukoon.app.ui.theme.Motion.staggerIn
import com.sukoon.app.ui.theme.SageLight
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.ui.logbook.siteName
import com.sukoon.app.ui.logbook.typeLabel

/**
 * Trends → Insights (Redesign Canvas §8, docs/insights-rework.md). Top to bottom: the 14 days in one
 * line, the one thing with one button (the first action not already in effect, else Look at it), up
 * to three more as plain rows, the rest folded. Every row opens the full card with its numbers and
 * source. Gated by a one-time acknowledgement.
 */
@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onAcknowledge: () -> Unit,
    onAction: (InsightAction) -> Unit = {},
    modifier: Modifier = Modifier,
    /** False on a followed person's data: their settings aren't this phone's to change. */
    showActions: Boolean = true,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.acknowledged) {
            Text(stringResource(R.string.insights_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
            Acknowledgement(onAcknowledge)
            return@Column
        }
        val digest = state.digest ?: return@Column
        FourteenDays(digest, state.insights.orEmpty())
        when {
            digest.oneThing != null -> OneThing(digest.oneThing, state.inEffect, state.insights.orEmpty(), onAction, showActions)
            digest.notEnough != null -> NotEnoughYet(digest.notEnough, onAction)
            else -> Calm()
        }
        if (digest.alsoWorth.isNotEmpty()) {
            Eyebrow(stringResource(R.string.in2_also), Modifier.padding(top = 8.dp, start = 4.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)) {
                digest.alsoWorth.forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                    FindingRow(f, state.insights.orEmpty())
                }
            }
        }
        if (digest.more.isNotEmpty()) {
            var open by rememberSaveable { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (open) stringResource(R.string.in2_less) else pluralStringResource(R.plurals.in2_more, digest.more.size, digest.more.size),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep, modifier = Modifier.weight(1f),
                )
                Icon(painterResource(R.drawable.ic_chevron), null, tint = SageDeep, modifier = Modifier.size(16.dp).rotate(if (open) 270f else 90f))
            }
            Reveal(open) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)) {
                    digest.more.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                        FindingRow(f, state.insights.orEmpty())
                    }
                }
            }
        }
        Text(stringResource(R.string.insights_banner), fontSize = 11.5.sp, color = CaptionMuted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
    }
}

/** "Your 14 days · 78 % in range · ↑ 4": tap for the full targets and the week against the one before. */
@Composable
private fun FourteenDays(digest: InsightDigest, insights: List<Insight>) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = digest.targets != null) { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.in2_days_label, digest.days), fontSize = 12.5.sp, color = CaptionMuted)
            Text(digest.inRange?.let { stringResource(R.string.in2_in_range, it) } ?: "–", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            digest.inRangeChange?.takeIf { it != 0 }?.let { change ->
                val up = change > 0
                Text(
                    (if (up) "↑ " else "↓ ") + abs(change),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (up) SageDeep else PillHighText,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (up) SageMist else StateHigh.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (digest.targets != null) Icon(painterResource(R.drawable.ic_chevron), null, tint = CaptionMuted, modifier = Modifier.size(16.dp).rotate(if (open) 90f else 0f).autoMirror())
        }
        Reveal(open) {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                digest.targets?.let { InsightCard(it) }
                insights.filterIsInstance<Insight.WeekOverWeek>().firstOrNull()?.let { InsightCard(it) }
            }
        }
    }
}

@Composable
private fun OneThing(finding: Finding, inEffect: InEffect, insights: List<Insight>, onAction: (InsightAction) -> Unit, showActions: Boolean) {
    val (sentence, evidence) = words(finding)
    val action = finding.action(inEffect)
    val skipped = action == InsightAction.LOOK_AT && finding.actions.first() != InsightAction.LOOK_AT
    var why by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.in2_one_thing).uppercase(), fontSize = 11.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
        Text(sentence, fontFamily = HeadlineSerifFontFamily, fontSize = 21.sp, lineHeight = 27.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(evidence, fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.clickable { why = !why })
        if (showActions) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sage).clickable { onAction(action) }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(actionLabel(action), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            Text(if (skipped) stringResource(R.string.in2_cap_already) else actionCaption(action), fontSize = 12.5.sp, color = CaptionMuted)
        }
        Reveal(why) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { related(finding, insights).forEach { InsightCard(it) } } }
    }
}

/** One plain row: the sentence, its evidence, a chevron; tap opens its full card(s) in place. */
@Composable
private fun FindingRow(finding: Finding, insights: List<Insight>) {
    val (sentence, evidence) = words(finding)
    var open by rememberSaveable(finding.insight.toString()) { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(sentence, fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(evidence, fontSize = 12.sp, color = CaptionMuted)
            }
            Icon(painterResource(R.drawable.ic_chevron), null, tint = CaptionMuted, modifier = Modifier.padding(start = 8.dp).size(16.dp).rotate(if (open) 90f else 0f).autoMirror())
        }
        Reveal(open) { Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { related(finding, insights).forEach { InsightCard(it) } } }
    }
}

/** The cards behind a finding: the lows story opens every low rule it merged. */
private fun related(finding: Finding, insights: List<Insight>): List<Insight> = when (finding.insight) {
    is LowsStory -> insights.filter { it is Insight.RecurringLows || it is Insight.Nights || it is Insight.Stacking || it is Insight.ActivityLows || it is Insight.Rebounds }
    else -> listOf(finding.insight)
}

@Composable
private fun NotEnoughYet(need: NotEnough, onAction: (InsightAction) -> Unit) {
    val (sentence, progress, have, of) = when (need) {
        is NotEnough.Readings -> listOf(stringResource(R.string.in2_need_readings, need.needed), stringResource(R.string.in2_need_readings_progress, need.days, need.needed), need.days, need.needed)
        is NotEnough.Meals -> listOf(stringResource(R.string.in2_need_meals, need.needed), stringResource(R.string.in2_need_meals_progress, need.days, need.needed), need.days, need.needed)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.in2_not_enough).uppercase(), fontSize = 11.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
        Text(sentence as String, fontFamily = HeadlineSerifFontFamily, fontSize = 21.sp, lineHeight = 27.sp, color = MaterialTheme.colorScheme.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(of as Int) { i -> Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < (have as Int)) Sage else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))) }
        }
        Text(progress as String, fontSize = 12.5.sp, color = CaptionMuted)
        if (need is NotEnough.Meals) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sage).clickable { onAction(InsightAction.LOG_MEAL) }.padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_food), null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.in2_log_meal), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        Text(stringResource(R.string.in2_need_note), fontSize = 12.5.sp, lineHeight = 18.sp, color = CaptionMuted)
    }
}

/** Enough data and nothing pressing: say so, calmly. */
@Composable
private fun Calm() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.in2_one_thing).uppercase(), fontSize = 11.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
        Text(stringResource(R.string.in2_calm), fontFamily = HeadlineSerifFontFamily, fontSize = 21.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.in2_calm_body), fontSize = 13.sp, color = CaptionMuted)
    }
}

@Composable
private fun actionLabel(action: InsightAction): String = stringResource(
    when (action) {
        InsightAction.TURN_ON_GOING_LOW -> R.string.in2_act_going_low
        InsightAction.TURN_ON_WORKOUT_CHECK -> R.string.in2_act_workout
        InsightAction.OPEN_DOSE_SETUP -> R.string.in2_act_dose
        InsightAction.OPEN_LEARNING -> R.string.in2_act_learning
        InsightAction.LOG_INSULIN -> R.string.in2_act_log_insulin
        InsightAction.OPEN_CALIBRATION -> R.string.in2_act_calibration
        InsightAction.OPEN_SITES -> R.string.in2_act_sites
        InsightAction.LOG_MEAL -> R.string.in2_log_meal
        InsightAction.REPORT -> R.string.in2_act_report
        InsightAction.LOOK_AT -> R.string.in2_act_look
    },
)

@Composable
private fun actionCaption(action: InsightAction): String = stringResource(
    when (action) {
        InsightAction.TURN_ON_GOING_LOW -> R.string.in2_cap_going_low
        InsightAction.TURN_ON_WORKOUT_CHECK -> R.string.in2_cap_workout
        InsightAction.OPEN_DOSE_SETUP -> R.string.in2_cap_dose
        InsightAction.OPEN_LEARNING -> R.string.in2_cap_learning
        InsightAction.LOG_INSULIN, InsightAction.LOG_MEAL -> R.string.in2_cap_log_insulin
        InsightAction.OPEN_CALIBRATION -> R.string.in2_cap_calibration
        InsightAction.OPEN_SITES -> R.string.in2_cap_sites
        InsightAction.REPORT -> R.string.in2_cap_report
        InsightAction.LOOK_AT -> R.string.in2_cap_look
    },
)

/** A finding in words: one sentence, and what it's from. */
@Composable
private fun words(f: Finding): Pair<String, String> {
    val i = f.insight
    return when (i) {
        is LowsStory -> {
            val cause = when (i.cause) {
                LowCause.WORKOUTS -> stringResource(R.string.in2_cause_workouts)
                LowCause.STACKING -> stringResource(R.string.in2_cause_stacking)
                null -> null
            }
            val r = i.recurring
            val sentence = when {
                r != null -> stringResource(R.string.in2_lows_cluster, clock(r.fromHour), clock(r.toHour))
                i.nightsWithLows >= 2 -> stringResource(R.string.in2_lows_nights, i.nightsWithLows, i.nights)
                i.totalLows > 0 -> stringResource(R.string.in2_lows_count, i.totalLows)
                else -> stringResource(R.string.in2_lows_often)
            }
            val base = when {
                r != null -> stringResource(R.string.in2_ev_days, r.days)
                i.totalLows > 0 -> stringResource(R.string.in2_ev_lows, i.totalLows)
                else -> stringResource(R.string.in2_ev_goal)
            }
            sentence to listOfNotNull(base, cause).joinToString(" · ")
        }
        is Insight.MealOutcomes -> when {
            i.lowWithin4h >= 2 -> stringResource(R.string.in2_meal_low, i.lowWithin4h, i.meals, slot(i.slot).lowercase())
            f.priority >= 60 -> stringResource(R.string.in2_meal_hard, slot(i.slot), i.averageRise)
            else -> stringResource(R.string.in2_meal_rise, slot(i.slot), i.averageRise)
        } to stringResource(R.string.in2_ev_meals, i.meals)
        is Insight.RecurringHighs -> stringResource(R.string.in2_highs, clock(i.fromHour), clock(i.toHour)) to stringResource(R.string.in2_ev_percent_days, i.percentOfDays)
        is Insight.Rebounds -> stringResource(R.string.in2_rebound) to stringResource(R.string.in2_ev_of_lows, i.rebounds, i.lows)
        is Insight.MeterAgreement -> stringResource(if (i.level == Level.GOOD) R.string.in2_meter_ok else R.string.in2_meter_off) to stringResource(R.string.in2_ev_checks, i.agreeing, i.checks)
        is Insight.DawnRise -> stringResource(R.string.in2_dawn, i.medianRise) to stringResource(R.string.in2_ev_mornings, i.nightsWithRise, i.nights)
        is Insight.Variability -> stringResource(if (i.level == Level.GOOD) R.string.in2_cv_ok else R.string.in2_cv_high) to stringResource(R.string.in2_ev_cv, i.cvPercent)
        is Insight.Rotation -> stringResource(R.string.in2_rotation, siteName(i.site), i.count, i.total) to stringResource(R.string.in2_ev_rotation)
        is Insight.RichMeals -> stringResource(R.string.in2_rich) to stringResource(R.string.in2_ev_rich, signed(i.richAt4h), signed(i.leanAt4h))
        is Insight.PreBolus -> stringResource(R.string.in2_prebolus, i.earlyRise, i.lateRise) to stringResource(R.string.in2_ev_prebolus, i.earlyMeals, i.lateMeals)
        is Insight.CarbDays -> stringResource(R.string.in2_carbdays, i.higherTir, i.lowerTir) to stringResource(R.string.in2_ev_carbdays, i.splitGrams, i.days)
        is Insight.CarbResponse -> stringResource(R.string.in2_carb_response, slot(i.slots.maxBy { it.per10g }.slot)) to stringResource(R.string.in2_ev_peak, i.peakMinutes)
        is Insight.Nights -> stringResource(if (i.withLows >= 2) R.string.in2_lows_nights else R.string.in2_nights_ok, i.withLows, i.nights) to stringResource(R.string.in2_ev_nights, i.inRange, i.nights)
        else -> "" to ""
    }
}

/** An hour in the chosen clock ("2 AM" / "02:00"). */
private fun clock(hour: Int): String = LocalTime.of(hour.coerceIn(0, 23), 0).format(TimeFormat.of())

/** Opens in place; with the phone's "Remove animations" on, only a short fade (Motion.reduced). */
@Composable
private fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    val reduced = Motion.reduced()
    AnimatedVisibility(
        visible,
        enter = if (reduced) fadeIn(tween(Motion.REDUCED)) else expandVertically(tween(Motion.BASE, easing = Motion.Out)) + fadeIn(tween(Motion.BASE)),
        exit = if (reduced) fadeOut(tween(Motion.REDUCED)) else shrinkVertically(tween(Motion.BASE, easing = Motion.In)) + fadeOut(tween(Motion.QUICK)),
    ) { content() }
}

/** Chevrons point the reading way: flipped right to left. */
@Composable
private fun Modifier.autoMirror(): Modifier = if (LocalLayoutDirection.current == LayoutDirection.Rtl) this.scale(-1f, 1f) else this

@Composable
private fun Acknowledgement(onAcknowledge: () -> Unit) {
    Card(Level.INFO) {
        Text(stringResource(R.string.insights_ack_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.insights_ack_body), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onBackground)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Sage)
                .clickable(onClick = onAcknowledge)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.insights_ack_button), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    Card(insight.level) {
        when (insight) {
            is Insight.NotEnoughData -> {
                Title(stringResource(R.string.insight_nodata_title))
                Body(stringResource(R.string.insight_nodata_body, insight.daysWithData, insight.coveragePercent))
            }
            is Insight.Targets -> {
                Title(stringResource(R.string.insight_tir_title, insight.days))
                TargetRow(R.string.insight_tir_in_range, insight.inRange, stringResource(R.string.insight_goal_over, 70), insight.metInRange)
                if (insight.inYourRange != null && insight.yourHigh != null) Body(stringResource(R.string.insight_tir_yours, insight.yourHigh, insight.inYourRange))
                TargetRow(R.string.insight_tir_below70, insight.below70, stringResource(R.string.insight_goal_under, 4), insight.metBelow70)
                TargetRow(R.string.insight_tir_below54, insight.below54, stringResource(R.string.insight_goal_under, 1), insight.metBelow54)
                TargetRow(R.string.insight_tir_above180, insight.above180, stringResource(R.string.insight_goal_under, 25), insight.metAbove180)
                TargetRow(R.string.insight_tir_above250, insight.above250, stringResource(R.string.insight_goal_under, 5), insight.metAbove250)
                Body(stringResource(R.string.insight_tir_mean, insight.meanMgDl, String.format(Locale.getDefault(), "%.1f", insight.gmiPercent)))
                if (insight.days < 14 || insight.coveragePercent < 70) Note(stringResource(R.string.insight_tir_coverage, insight.days, insight.coveragePercent))
                Source(stringResource(R.string.insight_src_tir))
            }
            is Insight.Variability -> {
                Title(stringResource(R.string.insight_cv_title))
                Body(stringResource(if (insight.level == Level.GOOD) R.string.insight_cv_good else R.string.insight_cv_high, insight.cvPercent))
                if (insight.level != Level.GOOD) Discuss(stringResource(R.string.insight_cv_discuss))
                Source(stringResource(R.string.insight_src_cv))
            }
            is Insight.RecurringLows -> {
                Title(stringResource(R.string.insight_lows_title, hour(insight.fromHour), hour(insight.toHour)))
                Body(stringResource(R.string.insight_lows_body, insight.days, insight.episodes, insight.totalEpisodes))
                Discuss(stringResource(if (insight.overnight) R.string.insight_lows_discuss_night else R.string.insight_lows_discuss_day))
                Source(stringResource(R.string.insight_src_lows))
            }
            is Insight.RecurringHighs -> {
                Title(stringResource(R.string.insight_highs_title, hour(insight.fromHour), hour(insight.toHour)))
                Body(stringResource(R.string.insight_highs_body, insight.percentOfDays))
                Discuss(stringResource(R.string.insight_highs_discuss))
                Source(stringResource(R.string.insight_src_tir))
            }
            is Insight.DawnRise -> {
                Title(stringResource(R.string.insight_dawn_title))
                Body(stringResource(R.string.insight_dawn_body, insight.nightsWithRise, insight.nights, insight.medianRise))
                Discuss(stringResource(R.string.insight_dawn_discuss))
                Source(stringResource(R.string.insight_src_dawn))
            }
            is Insight.MealOutcomes -> {
                Title(stringResource(R.string.insight_meal_title, slot(insight.slot), insight.meals))
                Body(stringResource(R.string.insight_meal_body, insight.highAt2h, insight.meals, insight.lowWithin4h, insight.averageRise))
                insight.gramsPerUnit?.let { Body(stringResource(R.string.insight_meal_ratio, String.format(Locale.getDefault(), "%.1f", it))) }
                when {
                    insight.lowWithin4h >= 2 -> Discuss(stringResource(R.string.insight_meal_discuss_low))
                    insight.level == Level.ATTENTION -> Discuss(stringResource(R.string.insight_meal_discuss_high))
                }
                Source(stringResource(R.string.insight_src_meal))
            }
            is Insight.PreBolus -> {
                Title(stringResource(R.string.insight_prebolus_title))
                Body(stringResource(R.string.insight_prebolus_body, insight.earlyRise, insight.earlyMeals, insight.lateRise, insight.lateMeals))
                Source(stringResource(R.string.insight_src_prebolus))
            }
            is Insight.Stacking -> {
                Title(stringResource(R.string.insight_stacking_title))
                Body(stringResource(R.string.insight_stacking_body, insight.lowsAfterStacking, insight.totalLows))
                Discuss(stringResource(R.string.insight_stacking_discuss))
                Source(stringResource(R.string.insight_src_stacking))
            }
            is Insight.Formulas -> {
                Title(stringResource(R.string.insight_formulas_title))
                Body(
                    stringResource(
                        R.string.insight_formulas_body,
                        number(insight.totalDailyDose), insight.completeDays, number(insight.gramsPerUnit500), number(insight.mgDlPerUnit1800),
                    ),
                )
                Note(stringResource(R.string.insight_formulas_note))
                Source(stringResource(R.string.insight_src_formulas))
            }
            is Insight.MeterAgreement -> {
                Title(stringResource(R.string.insight_meter_title))
                Body(stringResource(R.string.insight_meter_body, insight.agreeing, insight.checks))
                Body(
                    when {
                        insight.meanDiffPercent > 2 -> stringResource(R.string.insight_meter_higher, insight.meanDiffPercent)
                        insight.meanDiffPercent < -2 -> stringResource(R.string.insight_meter_lower, -insight.meanDiffPercent)
                        else -> stringResource(R.string.insight_meter_same)
                    },
                )
                Note(stringResource(R.string.insight_meter_note))
                if (insight.level != Level.GOOD) Discuss(stringResource(R.string.insight_meter_discuss))
                Source(stringResource(R.string.insight_src_meter))
            }
            is Insight.WeekOverWeek -> {
                Title(stringResource(R.string.insight_week_title))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Compare(stringResource(R.string.insight_week_in_range), "${insight.inRange}%", insight.inRange - insight.inRangeBefore, higherIsBetter = true, Modifier.weight(1f))
                    Compare(stringResource(R.string.insight_week_average), "${insight.mean}", insight.mean - insight.meanBefore, higherIsBetter = false, Modifier.weight(1f))
                    Compare(stringResource(R.string.insight_week_lows), "${insight.lows}", insight.lows - insight.lowsBefore, higherIsBetter = false, Modifier.weight(1f))
                }
                Source(stringResource(R.string.insight_src_tir))
            }
            is Insight.CarbResponse -> {
                val top = insight.slots.maxBy { it.per10g }
                val bottom = insight.slots.minBy { it.per10g }
                Title(if (top.per10g >= bottom.per10g * 3 / 2 && top.per10g - bottom.per10g >= 5) stringResource(R.string.insight_carb_title_slot, slot(top.slot).lowercase()) else stringResource(R.string.insight_carb_title))
                Body(stringResource(R.string.insight_carb_body))
                val max = insight.slots.maxOf { it.per10g }.coerceAtLeast(1)
                insight.slots.forEach { s ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(slot(s.slot), fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.width(84.dp))
                        Box(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth((s.per10g.coerceAtLeast(1).toFloat() / max).coerceIn(0.05f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (s == top && insight.slots.size > 1) StateHigh else SageLight))
                        }
                        Text(String.format(Locale.getDefault(), "%+d", s.per10g), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Note(stringResource(R.string.insight_carb_peak, insight.peakMinutes))
                Discuss(stringResource(R.string.insight_carb_discuss))
                Source(stringResource(R.string.insight_src_carb))
            }
            is Insight.RichMeals -> {
                Title(stringResource(R.string.insight_rich_title))
                Body(stringResource(R.string.insight_rich_body, insight.richPeak, insight.leanPeak, signed(insight.richAt4h), signed(insight.leanAt4h), insight.rich))
                if (insight.level == Level.ATTENTION) Discuss(stringResource(R.string.insight_rich_discuss))
                Source(stringResource(R.string.insight_src_rich))
            }
            is Insight.Rebounds -> {
                Title(stringResource(R.string.insight_rebound_title))
                Body(stringResource(R.string.insight_rebound_body, insight.rebounds, insight.lows))
                Discuss(stringResource(R.string.insight_rebound_discuss))
                Source(stringResource(R.string.insight_src_rebound))
            }
            is Insight.ActivityLows -> {
                Title(stringResource(R.string.insight_activity_title))
                Body(stringResource(R.string.insight_activity_body, insight.followed, insight.workouts, insight.overnight))
                Discuss(stringResource(R.string.insight_activity_discuss))
                Source(stringResource(R.string.insight_src_activity))
            }
            is Insight.Nights -> {
                Title(stringResource(R.string.insight_nights_title))
                Body(stringResource(R.string.insight_nights_body, insight.inRange, insight.nights, insight.withLows))
                if (insight.level == Level.ATTENTION) Discuss(stringResource(R.string.insight_nights_discuss))
                Source(stringResource(R.string.insight_src_lows))
            }
            is Insight.CarbDays -> {
                Title(stringResource(R.string.insight_carbdays_title))
                Body(stringResource(R.string.insight_carbdays_body, insight.splitGrams, insight.higherTir, insight.lowerTir, insight.days))
                if (insight.level == Level.ATTENTION) Discuss(stringResource(R.string.insight_carbdays_discuss))
                Source(stringResource(R.string.insight_src_carbdays))
            }
            is LowsStory -> Unit // shown through the rules it merged (related())
            is Insight.Rotation -> {
                Title(stringResource(R.string.insight_rotation_title))
                Body(stringResource(R.string.insight_rotation_body, siteName(insight.site), insight.count, insight.total, typeLabel(if (insight.longActing) LogEventType.BASAL else LogEventType.INSULIN)))
                Discuss(stringResource(R.string.insight_rotation_discuss))
                Source(stringResource(R.string.insight_src_rotation))
            }
        }
    }
}

private fun hour(h: Int) = String.format(Locale.getDefault(), "%02d:00", h)
private fun signed(v: Int) = String.format(Locale.getDefault(), "%+d", v)

/** A number this week and how it moved since last week, coloured by whether that's better. */
@Composable
private fun Compare(label: String, value: String, delta: Int, higherIsBetter: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = CaptionMuted)
        Text(value, fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
        val better = if (higherIsBetter) delta > 0 else delta < 0
        Text(
            if (delta == 0) "±0" else signed(delta),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                delta == 0 -> CaptionMuted
                better -> SageDeep
                else -> PillLowText
            },
        )
    }
}
private fun number(v: Double) = String.format(Locale.getDefault(), "%.1f", v)

@Composable
internal fun slot(slot: MealSlot) = stringResource(
    when (slot) {
        MealSlot.BREAKFAST -> R.string.meal_breakfast
        MealSlot.LUNCH -> R.string.meal_lunch
        MealSlot.DINNER -> R.string.meal_dinner
        MealSlot.LATE -> R.string.meal_late
    },
)

@Composable
private fun Card(level: Level, content: @Composable () -> Unit) {
    val accent = when (level) {
        Level.GOOD -> Sage
        Level.INFO -> CaptionMuted
        Level.ATTENTION -> StateHigh
        Level.URGENT -> StateLow
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun TargetRow(labelRes: Int, percent: Int, goal: String, met: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Text(String.format(Locale.getDefault(), "%d%%", percent), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (met) Sage else StateLow)
        Spacer(Modifier.width(8.dp))
        Text(goal, fontSize = 11.sp, color = CaptionMuted)
    }
}

@Composable private fun Title(text: String) = Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
@Composable private fun Body(text: String) = Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground)
@Composable private fun Note(text: String) = Text(text, fontSize = 12.sp, lineHeight = 17.sp, color = CaptionMuted)

@Composable
private fun Discuss(text: String) {
    Spacer(Modifier.height(2.dp))
    Text(stringResource(R.string.insight_discuss_label), fontSize = 10.5.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
    Text(text, fontSize = 12.5.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun Source(text: String) = Text(stringResource(R.string.insight_source, text), fontSize = 10.5.sp, lineHeight = 14.sp, color = CaptionMuted)
