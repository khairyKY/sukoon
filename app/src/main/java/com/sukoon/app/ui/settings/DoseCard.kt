package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.insights.Insight
import com.sukoon.app.insights.MealSlot
import com.sukoon.app.insulin.DoseSettings
import com.sukoon.app.insulin.RatioLearner
import com.sukoon.app.ui.components.ChoiceChips
import com.sukoon.app.ui.insights.slot
import com.sukoon.app.ui.logbook.Eyebrow
import com.sukoon.app.ui.logbook.PillButton
import com.sukoon.app.ui.logbook.formatAmount
import com.sukoon.app.ui.logbook.formatAmountLocalized
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * You → Insulin (design "You · Dose suggestions"): beta, carb counting with a ratio per meal
 * (docs/research/dosing-sources.md). Each meal's tile holds what its clean meals learned; an empty
 * box is left out of the maths.
 */
@Composable
fun DoseCard(
    settings: DoseSettings,
    onChange: (DoseSettings) -> Unit,
    startingPoints: suspend () -> Insight.Formulas?,
    learnRatios: suspend (Double?) -> List<RatioLearner.Learned>,
) {
    var why by remember { mutableStateOf<RatioLearner.Learned?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Tile.modifier(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dose_switch), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.dose_beta),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(SageMist).padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = SageDeep,
                )
                Switch(settings.enabled, { onChange(settings.copy(enabled = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
            }
            Text(
                stringResource(if (settings.enabled) R.string.dose_warning else R.string.dose_off_body),
                fontSize = 12.5.sp,
                color = if (settings.enabled) PillHighText else CaptionMuted,
            )
        }
        if (!settings.enabled) return@Column

        val learned by produceState(emptyList<RatioLearner.Learned>(), settings.correctionFactor) {
            delay(800) // the factor may still be being typed
            value = runCatching { learnRatios(settings.correctionFactor) }.getOrDefault(emptyList())
        }
        StartingPoints(settings, onChange, startingPoints)

        Eyebrow(stringResource(R.string.dose_ratio_label), Modifier.padding(top = 4.dp))
        MealSlot.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { s ->
                    val current = settings.carbRatio[s]
                    val l = learned.firstOrNull { it.slot == s }
                    val next = l?.let { RatioLearner.step(current, it.ratio) }
                    Column(Tile.modifier(Modifier.weight(1f))) {
                        Text(slot(s), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
                        NumberField(current, prefix = "1 : ", suffix = stringResource(R.string.logbook_unit_grams)) { v ->
                            onChange(settings.copy(carbRatio = if (v == null) settings.carbRatio - s else settings.carbRatio + (s to v)))
                        }
                        if (l != null && next != null && (current == null || abs(next - current) >= 0.5)) {
                            Row(
                                Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SageMist).padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.dose_meals_say, formatAmountLocalized(l.ratio)),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SageDeep,
                                    modifier = Modifier.weight(1f).clickable { why = l }.padding(vertical = 8.dp),
                                )
                                Text(
                                    stringResource(R.string.dose_use),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SageDeep,
                                    modifier = Modifier.clickable { onChange(settings.copy(carbRatio = settings.carbRatio + (s to next))) }.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Tile.modifier(Modifier.weight(1.3f))) {
                Text(stringResource(R.string.dose_factor_label), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
                NumberField(settings.correctionFactor, suffix = stringResource(R.string.home_unit_mgdl)) { onChange(settings.copy(correctionFactor = it)) }
            }
            Column(Tile.modifier(Modifier.weight(1f))) {
                Text(stringResource(R.string.dose_target_label), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
                NumberField(settings.target.toDouble()) { v -> v?.takeIf { it in 80.0..180.0 }?.let { onChange(settings.copy(target = it.toInt())) } }
            }
        }
        MoreSettings(settings, onChange)
        Text(stringResource(R.string.dose_empty_hint), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.padding(horizontal = 4.dp))
        if (learned.isEmpty()) Text(stringResource(R.string.dose_learning_needs), fontSize = 12.sp, color = CaptionMuted, modifier = Modifier.padding(horizontal = 4.dp))
    }
    why?.let { l ->
        WhySheet(l, settings.carbRatio[l.slot], onUse = { ratio ->
            onChange(settings.copy(carbRatio = settings.carbRatio + (l.slot to ratio)))
            why = null
        }, onDismiss = { why = null })
    }
}

private object Tile {
    @Composable
    fun modifier(base: Modifier = Modifier): Modifier = base
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, outline(), RoundedCornerShape(16.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp)
}

/** The textbook starting points from the logbook's daily totals, while a box is still empty. */
@Composable
private fun StartingPoints(settings: DoseSettings, onChange: (DoseSettings) -> Unit, startingPoints: suspend () -> Insight.Formulas?) {
    val start by produceState<Insight.Formulas?>(null) { value = runCatching { startingPoints() }.getOrNull() }
    val f = start?.takeIf { settings.carbRatio.size < MealSlot.entries.size || settings.correctionFactor == null } ?: return
    Row(Tile.modifier(Modifier.fillMaxWidth()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.dose_start_points, f.completeDays, formatAmountLocalized(f.totalDailyDose), formatAmountLocalized(f.gramsPerUnit500), formatAmountLocalized(f.mgDlPerUnit1800)),
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        PillButton(stringResource(R.string.dose_use_start)) {
            onChange(
                settings.copy(
                    carbRatio = MealSlot.entries.associateWith { settings.carbRatio[it] ?: f.gramsPerUnit500 },
                    correctionFactor = settings.correctionFactor ?: f.mgDlPerUnit1800,
                ),
            )
        }
    }
}

/** The maximum and the pen step: rarely changed, so one line until opened. */
@Composable
private fun MoreSettings(settings: DoseSettings, onChange: (DoseSettings) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val units = stringResource(R.string.logbook_unit_units)
    if (!open) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                .clickable { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.dose_more, formatAmountLocalized(settings.maxDose) + units, formatAmountLocalized(settings.step) + units),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.apps_change), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Tile.modifier(Modifier.weight(1f))) {
            Text(stringResource(R.string.dose_max_label), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
            NumberField(settings.maxDose, suffix = units) { v -> v?.takeIf { it in 1.0..50.0 }?.let { onChange(settings.copy(maxDose = it)) } }
        }
        Column(Tile.modifier(Modifier.weight(1f)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.dose_step_label), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
            ChoiceChips(listOf(0.5, 1.0), settings.step, { formatAmountLocalized(it) + units }) { onChange(settings.copy(step = it)) }
        }
    }
}

/** A big number typed in place; empty is allowed (null). Keeps what's being typed ("1.") while it parses. */
@Composable
private fun NumberField(value: Double?, prefix: String = "", suffix: String = "", onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(value?.let(::formatAmount).orEmpty()) }
    LaunchedEffect(value) { if (text.toDoubleOrNull() != value) text = value?.let(::formatAmount).orEmpty() }
    val big = TextStyle(fontFamily = HeadlineSerifFontFamily, fontWeight = FontWeight.Light, fontSize = 30.sp, color = MaterialTheme.colorScheme.onBackground)
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
        if (prefix.isNotEmpty()) Text(prefix, style = big)
        BasicTextField(
            value = text,
            onValueChange = { typed ->
                text = typed.filter { it.isDigit() || it == '.' }.take(5)
                onChange(text.toDoubleOrNull()?.takeIf { it > 0 })
            },
            textStyle = big,
            singleLine = true,
            cursorBrush = SolidColor(Sage),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(if (text.length <= 2) 48.dp else 72.dp),
            decorationBox = { field ->
                if (text.isEmpty()) Text("—", style = big.copy(color = CaptionMuted.copy(alpha = 0.5f)))
                field()
            },
        )
        Text(suffix.trim(), fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.padding(start = 2.dp, bottom = 5.dp))
    }
}

/** Design "Why 1 : 11?": the clean meals a learned ratio came from, and one step toward it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhySheet(l: RatioLearner.Learned, current: Double?, onUse: (Double) -> Unit, onDismiss: () -> Unit) {
    val meal = slot(l.slot)
    val next = RatioLearner.step(current, l.ratio)
    val day = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).withZone(ZoneId.systemDefault()) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow(stringResource(R.string.dose_why_eyebrow, meal))
            Text(stringResource(R.string.dose_why_title, formatAmountLocalized(l.ratio)), fontFamily = HeadlineSerifFontFamily, fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(
                pluralStringResource(R.plurals.dose_why_body, l.meals.size, l.meals.size, formatAmountLocalized(l.low), formatAmountLocalized(l.high)) +
                    (current?.let { " " + stringResource(R.string.dose_why_current, formatAmountLocalized(it)) } ?: ""),
                fontSize = 13.5.sp,
                color = CaptionMuted,
            )
            Column(Tile.modifier(Modifier.fillMaxWidth())) {
                l.meals.take(10).forEach { m ->
                    val change = m.change4h
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(day.format(Instant.ofEpochMilli(m.atMillis)), fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.width(78.dp))
                        Text(
                            stringResource(R.string.dose_why_row, formatAmountLocalized(m.carbs), formatAmountLocalized(m.insulin), String.format(Locale.getDefault(), "%+d", change)),
                            fontSize = 14.sp,
                            color = when {
                                change > 30 -> PillHighText
                                change < -30 -> PillLowText
                                else -> MaterialTheme.colorScheme.onBackground
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Text("1 : " + formatAmountLocalized((m.ratio * 2).let { Math.round(it) / 2.0 }), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            Text(stringResource(R.string.dose_why_rule, formatAmountLocalized(l.factor)), fontSize = 12.sp, color = CaptionMuted)
            Text(
                stringResource(R.string.dose_why_use, formatAmountLocalized(next), meal.lowercase()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Sage)
                    .clickable { onUse(next) }
                    .padding(vertical = 15.dp),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Text(stringResource(R.string.dose_why_step), fontSize = 12.sp, color = CaptionMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}
