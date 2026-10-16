package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.modes.Modes
import com.sukoon.app.modes.ModesKeeper
import com.sukoon.app.ui.components.ChoiceChips
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily

private val QUICK = listOf(0, 25, 50)

/**
 * Meal insulin after a workout, one setting shown in both You → Fitness & food and You → Insulin:
 * the quick choices, or Custom with a − / + stepper in 5% steps up to [Modes.AFTER_WORKOUT_CUT_MAX].
 */
@Composable
fun AfterWorkoutCut(keeper: ModesKeeper, enabled: Boolean = true) {
    var cut by remember { mutableStateOf(keeper.afterWorkoutCut) }
    var custom by remember { mutableStateOf(cut !in QUICK) }
    fun set(v: Int) {
        cut = Modes.afterWorkoutCut(v)
        keeper.afterWorkoutCut = cut
    }
    @Composable
    fun less(v: Int) = if (v == 0) stringResource(R.string.fitness_as_usual) else stringResource(R.string.fitness_less, v)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.fitness_after_meal), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted, modifier = Modifier.padding(top = 12.dp))
        ChoiceChips<Int?>(QUICK + null, if (custom) null else cut, { it?.let { v -> less(v) } ?: stringResource(R.string.after_workout_custom) }, enabled) { pick ->
            custom = pick == null
            pick?.let(::set)
        }
        if (custom) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Step(R.drawable.ic_minus, stringResource(R.string.food_less), enabled && cut > 0) { set(cut - 5) }
                Text(less(cut), fontFamily = HeadlineSerifFontFamily, fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 20.dp))
                Step(R.drawable.ic_plus, stringResource(R.string.food_more), enabled && cut < Modes.AFTER_WORKOUT_CUT_MAX) { set(cut + 5) }
            }
        }
        Text(stringResource(R.string.fitness_after_meal_body), fontSize = 13.sp, color = CaptionMuted)
    }
}

@Composable
private fun Step(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(22.dp))
    }
}
