package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.insights.Insight
import com.sukoon.app.insights.MealSlot
import com.sukoon.app.insulin.DoseSettings
import com.sukoon.app.insulin.RatioLearner
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.delay
import kotlin.math.abs
import com.sukoon.app.ui.components.ChoiceChips
import com.sukoon.app.ui.insights.slot
import com.sukoon.app.ui.logbook.PillButton
import com.sukoon.app.ui.logbook.formatAmount
import com.sukoon.app.ui.logbook.formatAmountLocalized
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.Sage

/**
 * You → Insulin: beta dose suggestions (docs/research/dosing-sources.md). Carb counting with a
 * ratio per meal; every box may stay empty, and that part is then left out.
 */
@Composable
fun DoseCard(
    settings: DoseSettings,
    onChange: (DoseSettings) -> Unit,
    startingPoints: suspend () -> Insight.Formulas?,
    learnRatios: suspend (Double?) -> List<RatioLearner.Learned>,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dose_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            Switch(settings.enabled, { onChange(settings.copy(enabled = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
        }
        if (!settings.enabled) {
            Text(stringResource(R.string.dose_off_body), fontSize = 12.5.sp, color = CaptionMuted)
            return@Column
        }
        Text(
            stringResource(R.string.dose_warning),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PillHighBg).padding(12.dp),
            fontSize = 13.sp,
            color = PillHighText,
        )
        Label(stringResource(R.string.dose_ratio_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealSlot.entries.forEach { s ->
                NumberBox(slot(s), settings.carbRatio[s], Modifier.weight(1f)) { v ->
                    onChange(settings.copy(carbRatio = if (v == null) settings.carbRatio - s else settings.carbRatio + (s to v)))
                }
            }
        }
        // What clean logged meals point to, one 20% step at a time, each applied only on Use.
        val learned by produceState(emptyList<RatioLearner.Learned>(), settings.correctionFactor) {
            delay(800) // the factor may still be being typed
            value = runCatching { learnRatios(settings.correctionFactor) }.getOrDefault(emptyList())
        }
        learned.forEach { l ->
            val current = settings.carbRatio[l.slot]
            val next = RatioLearner.step(current, l.ratio)
            if (current == null || abs(next - current) >= 0.5) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        pluralStringResource(R.plurals.dose_learned, l.meals, slot(l.slot), l.meals, formatAmountLocalized(l.ratio), formatAmountLocalized(l.low), formatAmountLocalized(l.high)),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(stringResource(R.string.dose_learned_use, formatAmountLocalized(next))) {
                        onChange(settings.copy(carbRatio = settings.carbRatio + (l.slot to next)))
                    }
                }
            }
        }
        if (learned.isEmpty()) Text(stringResource(R.string.dose_learning_needs), fontSize = 12.sp, color = CaptionMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberBox(stringResource(R.string.dose_factor_label), settings.correctionFactor, Modifier.weight(1.4f)) { onChange(settings.copy(correctionFactor = it)) }
            NumberBox(stringResource(R.string.dose_target_label), settings.target.toDouble(), Modifier.weight(1f)) { v ->
                v?.takeIf { it in 80.0..180.0 }?.let { onChange(settings.copy(target = it.toInt())) }
            }
        }
        Text(stringResource(R.string.dose_empty_hint), fontSize = 12.sp, color = CaptionMuted)

        val start by produceState<Insight.Formulas?>(null) { value = runCatching { startingPoints() }.getOrNull() }
        start?.takeIf { settings.carbRatio.isEmpty() || settings.correctionFactor == null }?.let { f ->
            Text(
                stringResource(R.string.dose_start_points, f.completeDays, formatAmountLocalized(f.totalDailyDose), formatAmountLocalized(f.gramsPerUnit500), formatAmountLocalized(f.mgDlPerUnit1800)),
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onBackground,
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberBox(stringResource(R.string.dose_max_label), settings.maxDose, Modifier.weight(1f)) { v ->
                v?.takeIf { it in 1.0..50.0 }?.let { onChange(settings.copy(maxDose = it)) }
            }
            Column(Modifier.weight(1f)) {
                Label(stringResource(R.string.dose_step_label))
                ChoiceChips(listOf(0.5, 1.0), settings.step, { formatAmountLocalized(it) + stringResource(R.string.logbook_unit_units) }) { onChange(settings.copy(step = it)) }
            }
        }
    }
}

/** A number box that may be left empty (null). Keeps what's being typed ("1.") while it parses. */
@Composable
private fun NumberBox(label: String, value: Double?, modifier: Modifier, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(value?.let(::formatAmount).orEmpty()) }
    LaunchedEffect(value) { if (text.toDoubleOrNull() != value) text = value?.let(::formatAmount).orEmpty() }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed.filter { it.isDigit() || it == '.' }.take(5)
            onChange(text.toDoubleOrNull()?.takeIf { it > 0 })
        },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
}
