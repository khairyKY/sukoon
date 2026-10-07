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

/**
 * Trends → Insights. Gated by a one-time acknowledgement; afterwards a standing one-line reminder
 * sits above the cards. Every card: what the data shows (numbers), why it matters, what people
 * commonly discuss with their care team — never "do X" — and the published source.
 */
@Composable
fun InsightsScreen(state: InsightsUiState, onAcknowledge: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.insights_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
        if (!state.acknowledged) {
            Acknowledgement(onAcknowledge)
            return@Column
        }
        Text(stringResource(R.string.insights_banner), fontSize = 12.sp, color = CaptionMuted)
        state.insights?.forEachIndexed { i, insight -> Box(Modifier.staggerIn(i)) { InsightCard(insight) } }
    }
}

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
private fun slot(slot: MealSlot) = stringResource(
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
