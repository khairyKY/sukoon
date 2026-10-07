package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.insights.Insight
import com.sukoon.app.insights.MealSlot
import com.sukoon.app.insulin.DoseSettings
import com.sukoon.app.insulin.Profile
import com.sukoon.app.insulin.Sex
import com.sukoon.app.insulin.StartSource
import com.sukoon.app.insulin.StartingPoints
import com.sukoon.app.ui.components.ChoiceChips
import com.sukoon.app.ui.logbook.PillButton
import com.sukoon.app.ui.logbook.formatAmountLocalized
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.SageDeep
import kotlin.math.roundToInt

/** "About you · 75 kg · 178 cm · 30 · male": one line in the dose card; opens to [ProfileFields]. */
@Composable
internal fun ProfileRow(profile: Profile, onChange: (Profile) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (open) {
        ProfileFields(profile, onChange)
        return
    }
    val parts = listOfNotNull(
        profile.weightKg?.let { formatAmountLocalized(it) + " " + stringResource(R.string.profile_kg) },
        profile.heightCm?.let { formatAmountLocalized(it.toDouble()) + " " + stringResource(R.string.profile_cm) },
        profile.ageYears?.let { formatAmountLocalized(it.toDouble()) },
        profile.sex?.let { stringResource(if (it == Sex.FEMALE) R.string.profile_female else R.string.profile_male).lowercase() },
    )
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
            (listOf(stringResource(R.string.profile_title)) + parts.ifEmpty { listOf(stringResource(R.string.profile_empty)) }).joinToString(" · "),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.apps_change), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
    }
}

/** Weight, height, age and sex, each optional. Weight feeds the starting ratios; age stops them under 18. */
@Composable
internal fun ProfileFields(profile: Profile, onChange: (Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(DoseTile.modifier(Modifier.weight(1f))) {
                Text(stringResource(R.string.profile_weight), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
                DoseNumberField(profile.weightKg, suffix = stringResource(R.string.profile_kg)) { v -> onChange(profile.copy(weightKg = v?.takeIf { it in 20.0..300.0 })) }
            }
            Column(DoseTile.modifier(Modifier.weight(1f))) {
                Text(stringResource(R.string.profile_height), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
                DoseNumberField(profile.heightCm?.toDouble(), suffix = stringResource(R.string.profile_cm)) { v -> onChange(profile.copy(heightCm = v?.roundToInt()?.takeIf { it in 80..250 })) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(DoseTile.modifier(Modifier.weight(1f))) {
                Text(stringResource(R.string.profile_age), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
                DoseNumberField(profile.ageYears?.toDouble()) { v -> onChange(profile.copy(ageYears = v?.roundToInt()?.takeIf { it in 1..120 })) }
            }
            Column(DoseTile.modifier(Modifier.weight(1f)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.profile_sex), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
                ChoiceChips(listOf(Sex.FEMALE, Sex.MALE), profile.sex, { stringResource(if (it == Sex.FEMALE) R.string.profile_female else R.string.profile_male) }) {
                    onChange(profile.copy(sex = if (profile.sex == it) null else it))
                }
            }
        }
        Text(stringResource(R.string.profile_why), fontSize = 12.sp, color = CaptionMuted)
    }
}

/**
 * "Suggest starting ratios": for when you don't know your numbers. Says where they came from (your
 * logbook, your weight, or the common adult start) and fills the empty boxes, or all of them.
 */
@Composable
internal fun SuggestRatios(settings: DoseSettings, profile: Profile, fromLogbook: Insight.Formulas?, onChange: (DoseSettings) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (!open) {
        PillButton(stringResource(R.string.dose_suggest)) { open = true }
        return
    }
    val start = StartingPoints.suggest(fromLogbook, profile)
    Column(DoseTile.modifier(Modifier.fillMaxWidth()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (start == null) {
            Text(stringResource(R.string.dose_suggest_young), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
            return@Column
        }
        Text(
            when (start.source) {
                StartSource.LOGBOOK -> stringResource(R.string.dose_suggest_logbook, fromLogbook?.completeDays ?: 0, formatAmountLocalized(start.dailyUnits ?: 0.0))
                StartSource.WEIGHT -> stringResource(R.string.dose_suggest_weight, formatAmountLocalized(profile.weightKg ?: 0.0), formatAmountLocalized(start.dailyUnits ?: 0.0))
                StartSource.COMMON -> stringResource(R.string.dose_suggest_common)
            },
            fontSize = 13.sp,
            color = CaptionMuted,
        )
        Text(
            stringResource(R.string.dose_suggest_values, formatAmountLocalized(start.ratio), formatAmountLocalized(start.factor)),
            fontFamily = HeadlineSerifFontFamily,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(stringResource(R.string.dose_suggest_check), fontSize = 12.5.sp, color = CaptionMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.dose_suggest_empty)) {
                onChange(settings.copy(carbRatio = MealSlot.entries.associateWith { settings.carbRatio[it] ?: start.ratio }, correctionFactor = settings.correctionFactor ?: start.factor))
                open = false
            }
            PillButton(stringResource(R.string.dose_suggest_all)) {
                onChange(settings.copy(carbRatio = MealSlot.entries.associateWith { start.ratio }, correctionFactor = start.factor))
                open = false
            }
        }
    }
}
