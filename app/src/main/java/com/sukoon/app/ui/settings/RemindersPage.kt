package com.sukoon.app.ui.settings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.insights.MealSlot
import com.sukoon.app.reminders.CheckSettings
import com.sukoon.app.reminders.Checks
import com.sukoon.app.reminders.DayKind
import com.sukoon.app.reminders.MealTimes
import com.sukoon.app.reminders.REMINDER_SLOTS
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.NeutralWarm
import com.sukoon.app.ui.theme.PillNeutralBg
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** You → Reminders: finger-prick checks (only while there's no sensor) and the missing-meal question. */
@Composable
internal fun RemindersPage() {
    val container = (LocalContext.current.applicationContext as SukoonApp).container
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var s by remember { mutableStateOf(container.settings.checks) }
    fun save(new: CheckSettings) {
        s = new
        container.settings.checks = new
        scope.launch { runCatching { Checks.refresh(context, container) } }
    }
    val learned by produceState(emptyMap<String, Int>()) {
        val now = Instant.now()
        value = MealTimes.learned(container.logbookRepository.eventsSince(now.minus(Duration.ofDays(21)).toEpochMilli()).first(), now, ZoneId.systemDefault())
    }
    var picking by remember { mutableStateOf<String?>(null) } // a meal's key, or "bed"

    Label(stringResource(R.string.checks_section_pricks))
    Card {
        ToggleRow(stringResource(R.string.checks_before_meals), null, s.beforeMeals) { save(s.copy(beforeMeals = it)) }
        Divider()
        ToggleRow(stringResource(R.string.checks_after_meals), null, s.afterMeals) { save(s.copy(afterMeals = it)) }
        Divider()
        ToggleRow(stringResource(R.string.checks_bedtime), minuteText(s.bedtimeMinute), s.bedtime, onTitle = { picking = "bed" }) { save(s.copy(bedtime = it)) }
        Divider()
        ToggleRow(stringResource(R.string.checks_after_low), stringResource(R.string.checks_after_low_body), s.afterLow) { save(s.copy(afterLow = it)) }
        Divider()
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.checks_cap), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.checks_cap_body, CheckSettings.CAP_RANGE.first, CheckSettings.CAP_RANGE.last), fontSize = 12.5.sp, color = CaptionMuted)
            }
            StepButton("−", stringResource(R.string.checks_cap_fewer), s.cap > CheckSettings.CAP_RANGE.first) { save(s.copy(cap = s.cap - 1)) }
            Text(String.format(Locale.getDefault(), "%d", s.cap), fontFamily = HeadlineSerifFontFamily, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            StepButton("+", stringResource(R.string.checks_cap_more), s.cap < CheckSettings.CAP_RANGE.last) { save(s.copy(cap = s.cap + 1)) }
        }
    }
    Text(stringResource(R.string.checks_pricks_note), fontSize = 12.5.sp, lineHeight = 17.sp, color = CaptionMuted, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))

    Spacer(Modifier.height(28.dp))
    Label(stringResource(R.string.checks_section_meals))
    var kind by rememberSaveable { mutableStateOf(DayKind.of(java.time.LocalDate.now())) }
    Card {
        ToggleRow(stringResource(R.string.checks_meal_missing), stringResource(R.string.checks_meal_missing_body), s.mealMissing) { save(s.copy(mealMissing = it)) }
        Divider()
        Row(Modifier.padding(vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(3.dp)) {
            DayKind.entries.forEach { k ->
                val on = k == kind
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable(role = Role.Tab) { kind = k },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(if (k == DayKind.WEEK) R.string.checks_week else R.string.checks_weekend), fontSize = 13.5.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) SageDeep else CaptionMuted)
                }
            }
        }
        REMINDER_SLOTS.forEach { slot ->
            val key = CheckSettings.key(kind, slot)
            val fixed = s.fixed[key]
            val time = fixed ?: learned[key]
            Row(
                Modifier.fillMaxWidth().clickable { picking = key }.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(slotName(slot)), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                when {
                    fixed != null -> Chip(stringResource(R.string.checks_set_by_you), SageMist, SageDeep, onClick = learned[key]?.let { { save(s.copy(fixed = s.fixed - key)) } })
                    time != null -> Chip(stringResource(R.string.checks_learned), PillNeutralBg, CaptionMuted)
                    else -> Chip(stringResource(R.string.checks_not_yet), PillNeutralBg, CaptionMuted)
                }
                Text(time?.let(::minuteText) ?: "–", fontFamily = HeadlineSerifFontFamily, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        Text(stringResource(R.string.checks_meals_note), fontSize = 12.5.sp, lineHeight = 17.sp, color = CaptionMuted, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
    }

    picking?.let { key ->
        val start = if (key == "bed") s.bedtimeMinute else s.fixed[key] ?: learned[key] ?: 13 * 60
        TimePickDialog(stringResource(if (key == "bed") R.string.checks_bedtime else R.string.checks_meal_time), start, onDismiss = { picking = null }) { minute ->
            picking = null
            save(if (key == "bed") s.copy(bedtimeMinute = minute) else s.copy(fixed = s.fixed + (key to minute)))
        }
    }
}

private fun slotName(slot: MealSlot) = when (slot) {
    MealSlot.BREAKFAST -> R.string.checks_breakfast
    MealSlot.LUNCH -> R.string.checks_lunch
    else -> R.string.checks_dinner
}

private fun minuteText(minute: Int) = String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) { content() }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)))

@Composable
private fun ToggleRow(title: String, body: String?, on: Boolean, onTitle: (() -> Unit)? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { if (onTitle != null) onTitle() else onChange(!on) }.heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            body?.let { Text(it, fontSize = 12.5.sp, lineHeight = 17.sp, color = CaptionMuted) }
        }
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
    }
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (enabled) SageMist else SageMist.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 18.sp, color = if (enabled) SageDeep else NeutralWarm) }
}

@Composable
private fun Chip(text: String, bg: Color, ink: Color, onClick: (() -> Unit)? = null) {
    Text(
        text, fontSize = 12.sp, color = ink,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}
