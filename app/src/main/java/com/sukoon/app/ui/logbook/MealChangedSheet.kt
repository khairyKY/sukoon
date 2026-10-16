package com.sukoon.app.ui.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.sukoon.app.insulin.MealGap
import com.sukoon.app.platform.TimeFormat
import com.sukoon.app.ui.components.SukoonSheet
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowBg
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep

/**
 * Design A ("Ate less than you dosed for"): after an edit moves a meal away from its insulin —
 * planned against eaten as two bars, the insulin, where glucose may go, and what to do.
 * Ate less: eat the grams that cover it, eat the rest later, or just watch (all keep a watch on).
 * Ate more (within 2 h): the units that would cover the extra, through the usual insulin entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealChangedSheet(
    gap: MealGap,
    target: Int,
    onEatNow: (Int) -> Unit,
    onLater: () -> Unit,
    onWatch: () -> Unit,
    onTopUp: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val time = TimeFormat.of()
    SukoonSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(if (gap.ateLess) R.string.meal_changed_less else R.string.meal_changed_more),
                fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, lineHeight = 31.sp, color = MaterialTheme.colorScheme.onBackground,
            )
            val most = maxOf(gap.planned, gap.eaten).coerceAtLeast(1.0)
            Bar(stringResource(R.string.meal_changed_planned), gap.planned / most, formatAmountLocalized(gap.planned) + stringResource(R.string.logbook_unit_grams), CaptionMuted)
            Bar(stringResource(R.string.meal_changed_ate), gap.eaten / most, formatAmountLocalized(gap.eaten) + stringResource(R.string.logbook_unit_grams), SageDeep)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.meal_changed_insulin), fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.width(76.dp))
                Text(stringResource(R.string.meal_changed_insulin_line, formatAmountLocalized(gap.dose), time.format(gap.doseAt)), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
            if (gap.ateLess) {
                val ahead = gap.ahead
                val low = ahead != null && ahead < 70
                val near = ahead != null && ahead < target
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (low) PillLowBg else PillHighBg).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (ahead != null) stringResource(R.string.meal_changed_ahead, String.format(java.util.Locale.getDefault(), "%d", ahead.coerceAtLeast(40)), time.format(gap.by))
                        else stringResource(R.string.meal_changed_ahead_unknown),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (low) PillLowText else PillHighText,
                    )
                    Text(
                        stringResource(R.string.meal_changed_extra, formatAmountLocalized(gap.stillActing.coerceAtLeast(0.0).let { Math.round(it * 2) / 2.0 }), gap.coverCarbs ?: 0),
                        fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                if (low || near) {
                    Primary(stringResource(R.string.meal_changed_eat, gap.coverCarbs ?: 15)) { onEatNow(gap.coverCarbs ?: 15) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Secondary(stringResource(R.string.meal_changed_later), Modifier.weight(1f), onLater)
                        Secondary(stringResource(R.string.meal_changed_watch), Modifier.weight(1f), onWatch)
                    }
                } else {
                    Primary(stringResource(R.string.meal_changed_watch), onWatch)
                    Secondary(stringResource(R.string.meal_changed_eat, gap.coverCarbs ?: 15), Modifier.fillMaxWidth()) { onEatNow(gap.coverCarbs ?: 15) }
                }
                Text(stringResource(R.string.meal_changed_watch_note, com.sukoon.app.modes.Modes.MEAL_WATCH_LOW), fontSize = 12.sp, lineHeight = 17.sp, color = CaptionMuted)
            } else {
                val units = gap.topUpUnits
                Text(
                    if (units != null) stringResource(R.string.meal_changed_topup_line, formatAmountLocalized(units))
                    else stringResource(R.string.meal_changed_topup_late),
                    fontSize = 14.5.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onBackground,
                )
                if (units != null) {
                    Primary(stringResource(R.string.meal_changed_topup, formatAmountLocalized(units))) { onTopUp(units) }
                    Secondary(stringResource(R.string.meal_changed_not_now), Modifier.fillMaxWidth(), onDismiss)
                } else {
                    Secondary(stringResource(R.string.meal_changed_ok), Modifier.fillMaxWidth(), onDismiss)
                }
            }
        }
    }
}

/** Deleting a meal that had its insulin: did you take it? (The insulin isn't deleted with the meal unless you say no.) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TookInsulinDialog(units: Double, onKeep: () -> Unit, onDeleteBoth: () -> Unit, onDismiss: () -> Unit) {
    SukoonSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.meal_delete_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, lineHeight = 31.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.meal_delete_body, formatAmountLocalized(units)), fontSize = 14.sp, lineHeight = 20.sp, color = CaptionMuted)
            Spacer(Modifier.height(4.dp))
            Primary(stringResource(R.string.meal_delete_keep), onKeep)
            Secondary(stringResource(R.string.meal_delete_both), Modifier.fillMaxWidth(), onDeleteBoth)
        }
    }
}

@Composable
private fun Bar(label: String, share: Double, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.width(66.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(share.toFloat().coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        }
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.width(52.dp))
    }
}

@Composable
private fun Primary(label: String, onClick: () -> Unit) = Text(
    label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sage).clickable(onClick = onClick).heightIn(min = 52.dp).padding(16.dp),
)

@Composable
private fun Secondary(label: String, modifier: Modifier, onClick: () -> Unit) = Text(
    label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    modifier = modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, outline(), RoundedCornerShape(14.dp)).clickable(onClick = onClick).heightIn(min = 46.dp).padding(14.dp),
)

