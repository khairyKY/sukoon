package com.sukoon.app.ui.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
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
import com.sukoon.app.insulin.DoseAdvice
import com.sukoon.app.ui.insights.slot
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillLowBg
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.Sage
import java.util.Locale
import kotlin.math.abs

/** Beta (design "A meal, with its suggestion"): the dose and its maths, line by line. "Use" fills the amount; only Save logs. */
@Composable
internal fun DoseLine(advice: DoseAdvice, onUse: (Double) -> Unit) {
    when (advice) {
        is DoseAdvice.TreatLowFirst -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PillLowBg).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.dose_low_title), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PillLowText)
            Text(stringResource(R.string.dose_low_body, advice.glucose), fontSize = 13.5.sp, lineHeight = 19.sp, color = PillLowText)
        }
        is DoseAdvice.Suggestion -> Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, Sage, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(stringResource(R.string.dose_suggested))
                    Text(
                        formatAmountLocalized(advice.units) + stringResource(R.string.logbook_unit_units),
                        fontFamily = HeadlineSerifFontFamily,
                        fontWeight = FontWeight.Light,
                        fontSize = 34.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    stringResource(R.string.dose_use),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Sage)
                        .clickable { onUse(advice.units) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            Receipt(advice)
        }
    }
}

/** Meal, correction, what insulin still working covered, and the rounding: each with its own maths. */
@Composable
private fun Receipt(a: DoseAdvice.Suggestion) {
    fun n(x: Double) = String.format(Locale.getDefault(), "%.1f", x)
    fun signed(x: Double) = (if (x < 0) "−" else "+") + n(abs(x))
    if (a.mealUnits != null && a.carbs != null && a.ratio != null) {
        ReceiptRow(stringResource(R.string.dose_r_meal), stringResource(R.string.dose_r_meal_how, formatAmountLocalized(a.carbs), formatAmountLocalized(a.ratio), slot(a.slot).lowercase()), n(a.mealUnits))
    }
    val correction = a.correctionUnits
    if (correction != null && a.glucose != null && a.factor != null) {
        // The correction before insulin still working took its share, so the two lines add up.
        val before = correction + a.onBoardUsed
        ReceiptRow(stringResource(R.string.dose_r_corr), stringResource(R.string.dose_r_corr_how, a.glucose, a.target, formatAmountLocalized(a.factor)), signed(before))
        if (a.onBoardUsed > 0) ReceiptRow(stringResource(R.string.dose_r_iob), stringResource(R.string.dose_r_iob_how), signed(-a.onBoardUsed))
    }
    ReceiptRow(
        stringResource(if (a.capped) R.string.dose_r_capped else R.string.dose_r_round),
        stringResource(if (a.capped) R.string.dose_r_capped_how else R.string.dose_r_round_how),
        formatAmountLocalized(a.units),
        strong = true,
    )
}

@Composable
private fun ReceiptRow(label: String, how: String, units: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.width(96.dp))
        Text(how, fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
        Text(units, fontSize = 14.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onBackground)
    }
}
