package com.sukoon.app.ui.settings

import com.sukoon.app.platform.TimeFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sukoon.app.R
import com.sukoon.app.insights.MealSlot
import com.sukoon.app.insulin.DoseSettings
import com.sukoon.app.insulin.RatioLearner
import com.sukoon.app.insulin.RatioLearner.Verdict
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.insights.slot
import com.sukoon.app.ui.logbook.RoundIconButton
import com.sukoon.app.ui.logbook.formatAmountLocalized
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Design "Learning from your meals": every meal time's progress toward a lesson, the clean meals
 * and what each says, what was left out and why, and the whole report as CSV. Always reachable
 * from the dose card, suggestions on or off.
 */
@Composable
internal fun LearningScreen(report: RatioLearner.Report?, settings: DoseSettings, insulinHours: Int, onChange: (DoseSettings) -> Unit, onDismiss: () -> Unit) {
    var howTo by remember { mutableStateOf(false) }
    FullScreen(stringResource(R.string.learn_title), onDismiss) {
        Text(
            when {
                report == null -> stringResource(R.string.learn_loading)
                report.factor == null -> stringResource(R.string.learn_no_factor)
                report.factorEstimated -> stringResource(R.string.learn_factor_estimated, formatAmountLocalized(report.factor))
                else -> stringResource(R.string.learn_factor, formatAmountLocalized(report.factor))
            },
            fontSize = 13.sp,
            color = CaptionMuted,
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(SageMist).clickable { howTo = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.learn_how_title), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = SageDeep, modifier = Modifier.weight(1f))
            Text("›", fontSize = 18.sp, color = SageDeep)
        }
        if (report == null) return@FullScreen
        FitCard(report.fit, settings, onChange)
        MealSlot.entries.forEach { s -> SlotCard(s, report, settings, onChange) }
        LeftOut(report.meals.filter { it.verdict != Verdict.CLEAN })
        ExportButton(report)
    }
    if (howTo) HowToScreen(insulinHours) { howTo = false }
}

/** "From everything you've logged": the fit over every usable meal and correction, each number with its own Use. */
@Composable
private fun FitCard(fit: RatioLearner.Fit?, settings: DoseSettings, onChange: (DoseSettings) -> Unit) {
    Column(Card(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.learn_fit_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        if (fit == null) {
            Text(stringResource(R.string.learn_fit_none), fontSize = 12.5.sp, color = CaptionMuted)
            return@Column
        }
        Text(stringResource(R.string.learn_fit_body, fit.meals, fit.corrections), fontSize = 12.5.sp, color = CaptionMuted)
        FitRow(stringResource(R.string.learn_fit_factor, formatAmountLocalized(fit.factor)), settings.correctionFactor, fit.factor) {
            onChange(settings.copy(correctionFactor = it))
        }
        fit.ratios.forEach { (s, ratio) ->
            FitRow(slot(s) + " · 1 : " + formatAmountLocalized(ratio), settings.carbRatio[s], ratio) { onChange(settings.copy(carbRatio = settings.carbRatio + (s to it))) }
        }
        Text(stringResource(R.string.learn_fit_note), fontSize = 12.sp, color = CaptionMuted)
    }
}

/** One fitted number, what you use now, and Use: one step of at most 20% toward it. */
@Composable
private fun FitRow(label: String, current: Double?, fitted: Double, onUse: (Double) -> Unit) {
    val next = RatioLearner.step(current, fitted)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SageMist).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label + (current?.let { " · " + stringResource(R.string.learn_you_use_value, formatAmountLocalized(it)) } ?: ""),
            fontSize = 13.sp,
            color = SageDeep,
            modifier = Modifier.weight(1f).padding(vertical = 9.dp),
        )
        if (current == null || next != current) {
            Text(
                stringResource(R.string.learn_use_value, formatAmountLocalized(next)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = SageDeep,
                modifier = Modifier.clickable { onUse(next) }.padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SlotCard(s: MealSlot, report: RatioLearner.Report, settings: DoseSettings, onChange: (DoseSettings) -> Unit) {
    val clean = report.meals.filter { it.slot == s && it.verdict == Verdict.CLEAN }
    val lesson = report.learned.firstOrNull { it.slot == s }
    var all by remember { mutableStateOf(false) }
    Column(Card(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(slot(s), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            repeat(RatioLearner.MIN_MEALS) { i -> Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < clean.size) Sage else outline())) }
            Text(pluralStringResource(R.plurals.learn_clean, clean.size, clean.size), fontSize = 12.5.sp, color = CaptionMuted)
        }
        if (lesson != null) {
            val current = settings.carbRatio[s]
            val next = RatioLearner.step(current, lesson.ratio)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SageMist).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.learn_says, formatAmountLocalized(lesson.ratio), formatAmountLocalized(lesson.low), formatAmountLocalized(lesson.high)) +
                        (current?.let { " · " + stringResource(R.string.learn_you_use, formatAmountLocalized(it)) } ?: ""),
                    fontSize = 13.sp,
                    color = SageDeep,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                if (current == null || next != current) {
                    Text(
                        stringResource(R.string.learn_use, formatAmountLocalized(next)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SageDeep,
                        modifier = Modifier.clickable { onChange(settings.copy(carbRatio = settings.carbRatio + (s to next))) }.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
        } else if (clean.size < RatioLearner.MIN_MEALS) {
            val left = RatioLearner.MIN_MEALS - clean.size
            Text(pluralStringResource(R.plurals.learn_more_needed, left, left), fontSize = 12.5.sp, color = CaptionMuted)
        }
        (if (all) clean else clean.take(2)).forEach { m -> MealRow(m) }
        if (clean.size > 2) {
            Text(
                if (all) stringResource(R.string.learn_fewer) else pluralStringResource(R.plurals.learn_all_meals, clean.size, clean.size),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SageDeep,
                modifier = Modifier.clickable { all = !all }.padding(vertical = 6.dp),
            )
        }
    }
}

/** "Tue 6 Oct · 60 g · 5 u · +50 at 4 h · 1 : 10" (the ratio only for a clean meal with a factor). */
@Composable
private fun MealRow(m: RatioLearner.Meal, reason: String? = null) {
    val day = TimeFormat.of("EEE d MMM HH:mm")
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(day.format(Instant.ofEpochMilli(m.atMillis)), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.width(104.dp))
        Column(Modifier.weight(1f)) {
            val change = m.change4h
            Text(
                stringResource(R.string.learn_meal_row, formatAmountLocalized(m.carbs), formatAmountLocalized(m.insulin)) +
                    (change?.let { " · " + stringResource(R.string.learn_change_4h, String.format(Locale.getDefault(), "%+d", it)) } ?: ""),
                fontSize = 13.sp,
                color = when {
                    change != null && change > 30 -> PillHighText
                    change != null && change < -30 -> PillLowText
                    else -> MaterialTheme.colorScheme.onBackground
                },
            )
            reason?.let { Text(it, fontSize = 12.sp, color = CaptionMuted) }
        }
        m.ratio?.let { Text("1 : " + formatAmountLocalized(Math.round(it * 2) / 2.0), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground) }
    }
}

/** Meals that didn't count, grouped by why; each reason opens its meals. */
@Composable
private fun LeftOut(meals: List<RatioLearner.Meal>) {
    if (meals.isEmpty()) return
    var open by remember { mutableStateOf<Verdict?>(null) }
    Column(Card(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.learn_left_out), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            Text(pluralStringResource(R.plurals.learn_meals, meals.size, meals.size), fontSize = 12.5.sp, color = CaptionMuted)
        }
        meals.groupBy { it.verdict }.entries.sortedByDescending { it.value.size }.forEach { (verdict, group) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { open = if (open == verdict) null else verdict },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(reason(verdict), fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                Text(formatAmountLocalized(group.size.toDouble()), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
            if (open == verdict) group.forEach { MealRow(it) }
        }
    }
}

@Composable
private fun reason(v: Verdict): String = stringResource(
    when (v) {
        Verdict.CLEAN -> R.string.learn_v_clean
        Verdict.TOO_SMALL -> R.string.learn_v_too_small
        Verdict.NO_INSULIN -> R.string.learn_v_no_insulin
        Verdict.NO_READINGS -> R.string.learn_v_no_readings
        Verdict.OUT_OF_RANGE -> R.string.learn_v_out_of_range
        Verdict.ATE_AGAIN -> R.string.learn_v_ate_again
        Verdict.MORE_INSULIN -> R.string.learn_v_more_insulin
        Verdict.STILL_WORKING -> R.string.learn_v_still_working
        Verdict.ACTIVE -> R.string.learn_v_active
        Verdict.RICH -> R.string.learn_v_rich
        Verdict.ODD -> R.string.learn_v_odd
    },
)

/** The whole report (every meal, its verdict and ratio) as a CSV saved wherever you choose. */
@Composable
private fun ExportButton(report: RatioLearner.Report) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    (context.contentResolver.openOutputStream(uri) ?: error("can't open the file")).use { it.write(RatioLearner.csv(report, ZoneId.systemDefault()).toByteArray()) }
                }
                context.getString(R.string.export_done, report.meals.size)
            }.getOrElse { context.getString(R.string.export_failed, it.message ?: it.javaClass.simpleName) }
            context.toast(message)
        }
    }
    Text(
        stringResource(R.string.learn_export),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(14.dp))
            .clickable { save.launch("sukoon-learning-${LocalDate.now()}.csv") }
            .padding(vertical = 14.dp),
        fontSize = 14.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
}

/** Design "Make a meal count": what makes a meal clean, as a checklist. */
@Composable
internal fun HowToScreen(insulinHours: Int, onDismiss: () -> Unit) {
    FullScreen(stringResource(R.string.learn_how_title), onDismiss) {
        Text(stringResource(R.string.learn_how_intro), fontSize = 13.5.sp, lineHeight = 19.sp, color = CaptionMuted)
        Column(Card()) {
            listOf(
                stringResource(R.string.learn_how_1) to stringResource(R.string.learn_how_1_body),
                stringResource(R.string.learn_how_2) to stringResource(R.string.learn_how_2_body),
                stringResource(R.string.learn_how_3) to stringResource(R.string.learn_how_3_body),
                stringResource(R.string.learn_how_4) to stringResource(R.string.learn_how_4_body),
                stringResource(R.string.learn_how_5) to pluralStringResource(R.plurals.learn_how_5_body, insulinHours, insulinHours),
                stringResource(R.string.learn_how_6) to stringResource(R.string.learn_how_6_body),
                stringResource(R.string.learn_how_7) to stringResource(R.string.learn_how_7_body),
            ).forEachIndexed { i, (title, body) ->
                Row(Modifier.padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(SageMist), contentAlignment = Alignment.Center) {
                        Text(formatAmountLocalized(i + 1.0), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SageDeep)
                    }
                    Column {
                        Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(body, fontSize = 13.sp, color = CaptionMuted)
                    }
                }
            }
        }
        Text(stringResource(R.string.learn_how_after), fontSize = 12.5.sp, color = CaptionMuted)
        Text(
            stringResource(R.string.learn_how_never),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PillHighBg).padding(14.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PillHighText,
        )
    }
}

@Composable
private fun FullScreen(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(R.drawable.ic_close, stringResource(R.string.entry_close), onDismiss)
                    Text(title, fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
                }
                content()
            }
        }
    }
}

@Composable
private fun Card(): Modifier = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(16.dp))
    .background(MaterialTheme.colorScheme.surface)
    .border(1.dp, outline(), RoundedCornerShape(16.dp))
    .padding(horizontal = 14.dp, vertical = 12.dp)
