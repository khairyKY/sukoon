package com.sukoon.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.sukoon.app.R
import com.sukoon.app.health.HealthConnectSync
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

private val GROUPS = listOf(
    R.string.stats_food to listOf(HomeStat.KCAL, HomeStat.CARBS, HomeStat.PROTEIN, HomeStat.FAT),
    R.string.stats_insulin to listOf(HomeStat.RAPID, HomeStat.LONG),
    R.string.stats_glucose to listOf(HomeStat.TIR, HomeStat.AVERAGE, HomeStat.LOWS),
    R.string.stats_health to listOf(HomeStat.STEPS, HomeStat.WATER),
)

/**
 * Which numbers Home shows (design "Choose your stats"): up to four, saved as they're ticked.
 * Steps and water ask Health Connect for access the first time they're picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsPicker(chosen: List<HomeStat>, health: HealthConnectSync, onChange: (List<HomeStat>) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(chosen) }
    val askHealth = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {}
    fun toggle(stat: HomeStat) {
        val next = if (stat in picked) picked - stat else picked + stat
        if (next.size > MAX_HOME_STATS) return
        picked = next
        onChange(next)
        if (stat in next && stat.fromHealthConnect && health.available) {
            askHealth.launch(setOf(if (stat == HomeStat.STEPS) health.readSteps else health.readWater))
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.stats_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(
                stringResource(if (picked.size >= MAX_HOME_STATS) R.string.stats_full else R.string.stats_body),
                fontSize = 14.sp,
                color = CaptionMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
            GROUPS.forEach { (title, stats) ->
                Text(stringResource(title).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = CaptionMuted, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                ) {
                    stats.forEach { stat ->
                        val on = stat in picked
                        val enabled = on || picked.size < MAX_HOME_STATS
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(enabled = enabled) { toggle(stat) }
                                .alpha(if (enabled) 1f else 0.45f)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(statLabel(stat), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                                statHint(stat)?.let { Text(it, fontSize = 12.5.sp, color = CaptionMuted) }
                            }
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .then(if (on) Modifier.background(Sage) else Modifier.border(2.dp, CaptionMuted.copy(alpha = 0.5f), RoundedCornerShape(6.dp))),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Sage)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.stats_done, picked.size), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
internal fun statLabel(stat: HomeStat): String = stringResource(
    when (stat) {
        HomeStat.KCAL -> R.string.stat_kcal
        HomeStat.CARBS -> R.string.stat_carbs
        HomeStat.PROTEIN -> R.string.stat_protein
        HomeStat.FAT -> R.string.stat_fat
        HomeStat.RAPID -> R.string.stat_rapid
        HomeStat.LONG -> R.string.stat_long
        HomeStat.TIR -> R.string.stat_tir
        HomeStat.AVERAGE -> R.string.stat_avg
        HomeStat.LOWS -> R.string.stat_lows
        HomeStat.STEPS -> R.string.stat_steps
        HomeStat.WATER -> R.string.stat_water
    },
)

@Composable
internal fun statUnit(stat: HomeStat): String = stringResource(
    when (stat) {
        HomeStat.KCAL -> R.string.entry_unit_kcal
        HomeStat.CARBS, HomeStat.PROTEIN, HomeStat.FAT -> R.string.logbook_unit_grams
        HomeStat.RAPID -> R.string.stat_unit_rapid
        HomeStat.LONG -> R.string.stat_unit_long
        HomeStat.TIR -> R.string.stat_unit_tir
        HomeStat.AVERAGE -> R.string.home_unit_mgdl
        HomeStat.LOWS -> R.string.stat_unit_lows
        HomeStat.STEPS -> R.string.stat_unit_steps
        HomeStat.WATER -> R.string.stat_unit_water
    },
)

@Composable
private fun statHint(stat: HomeStat): String? = when (stat) {
    HomeStat.KCAL -> stringResource(R.string.stats_hint_kcal)
    HomeStat.CARBS -> stringResource(R.string.stats_hint_carbs)
    HomeStat.RAPID -> stringResource(R.string.stats_hint_rapid)
    HomeStat.TIR -> stringResource(R.string.stats_hint_tir)
    HomeStat.AVERAGE -> stringResource(R.string.stats_hint_avg)
    HomeStat.LOWS -> stringResource(R.string.stats_hint_lows)
    HomeStat.STEPS, HomeStat.WATER -> stringResource(R.string.stats_hint_health)
    else -> null
}

/** "1,240", "4.5", "1.6": whole numbers grouped as the locale writes them; insulin and water keep one decimal. */
internal fun statValue(stat: HomeStat, value: Double): String = when (stat) {
    HomeStat.RAPID, HomeStat.LONG, HomeStat.WATER ->
        if (value == value.roundToLong().toDouble()) NumberFormat.getIntegerInstance().format(value.roundToLong()) else String.format(Locale.getDefault(), "%.1f", value)
    else -> NumberFormat.getIntegerInstance().format(value.roundToLong())
}
