package com.sukoon.app.ui.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.insulin.DoseAdvice
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillLowBg
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.Sage
import java.util.Locale
import kotlin.math.abs

/** Beta: the suggested rapid dose with its maths; "Use" fills the amount, only Save logs it. */
@Composable
internal fun DoseLine(advice: DoseAdvice, onUse: (Double) -> Unit) {
    when (advice) {
        DoseAdvice.TreatLowFirst -> Text(
            stringResource(R.string.dose_treat_low),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PillLowBg).padding(12.dp),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PillLowText,
        )
        is DoseAdvice.Suggestion -> Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Sage.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(stringResource(R.string.dose_suggested))
                    Text(
                        formatAmountLocalized(advice.units) + stringResource(R.string.logbook_unit_units),
                        fontFamily = HeadlineSerifFontFamily,
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                PillButton(stringResource(R.string.dose_use)) { onUse(advice.units) }
            }
            Text(maths(advice), fontSize = 12.5.sp, lineHeight = 17.sp, color = CaptionMuted)
        }
    }
}

/** "45 g ÷ 12 = 3.8 · +1.0 for 160 (target 110) · rounded down". */
@Composable
private fun maths(a: DoseAdvice.Suggestion): String {
    fun n(x: Double) = String.format(Locale.getDefault(), "%.1f", x)
    val parts = mutableListOf<String>()
    if (a.mealUnits != null && a.carbs != null && a.ratio != null) {
        parts += stringResource(R.string.dose_meal, formatAmountLocalized(a.carbs), formatAmountLocalized(a.ratio), n(a.mealUnits))
    }
    val c = a.correctionUnits
    if (c != null && a.glucose != null) {
        val signed = (if (c < 0) "−" else "+") + n(abs(c))
        parts += stringResource(R.string.dose_correction, signed, a.glucose)
        if (a.onBoardUsed > 0) parts += stringResource(R.string.dose_on_board, n(a.onBoardUsed))
    }
    parts += if (a.capped) stringResource(R.string.dose_capped) else stringResource(R.string.dose_rounded)
    return parts.joinToString(" · ")
}
