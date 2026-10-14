package com.sukoon.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.ai.ChatTurn
import com.sukoon.app.insights.Finding
import com.sukoon.app.insights.Insight
import com.sukoon.app.insights.LowsStory
import com.sukoon.app.platform.TimeFormat
import com.sukoon.app.ui.insights.slot
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import java.text.NumberFormat
import java.time.LocalTime

/**
 * Trends → Ask (Redesign Canvas §9): "Ask Sukoon", what the answers are based on (or a Demo data chip
 * when that's all there is), suggestions led by Insights' one thing, the conversation, and the input.
 * Without a key: one clear step to add it.
 */
@Composable
fun AskScreen(
    state: AskUiState,
    hasKey: Boolean,
    onAsk: (String) -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Insights' one thing: the first suggestion asks about it (never on demo data). */
    insightFinding: Finding? = null,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.turns.size, state.thinking) {
        val last = state.turns.size + (if (state.thinking) 1 else 0)
        if (last > 0) listState.animateScrollToItem(last)
    }
    LaunchedEffect(state.unanswered) { state.unanswered?.let { input = it } }

    if (!hasKey) {
        NoKey(onOpenSettings, modifier)
        return
    }
    val demo = state.basedOn?.demo == true
    val fromInsights = insightFinding?.takeIf { !demo }?.let { question(it) }
    val general = listOf(
        stringResource(R.string.ask_suggest_24h),
        stringResource(R.string.ask_suggest_highs),
        stringResource(R.string.ask_suggest_meal),
        stringResource(R.string.ask_suggest_week),
    )

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp).imePadding()) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ask_title), fontFamily = HeadlineSerifFontFamily, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                        if (state.turns.isNotEmpty()) TextButton(onClick = onClear) { Text(stringResource(R.string.ask_clear), color = CaptionMuted, fontSize = 12.sp) }
                    }
                    BasedOnLine(state.basedOn)
                }
            }
            if (state.turns.isEmpty() && !state.thinking) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        Text(stringResource(R.string.ask2_try).uppercase(), fontSize = 11.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
                        fromInsights?.let { InsightChip(it) { onAsk(it) } }
                        general.forEach { Chip(it) { onAsk(it) } }
                        Text(stringResource(R.string.ask2_not_advice), fontSize = 12.sp, color = CaptionMuted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            itemsIndexed(state.turns) { i, turn ->
                Bubble(turn)
                // Two more suggestions stay in reach under the last answer.
                if (!turn.fromUser && i == state.turns.lastIndex && !state.thinking) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        general.filterNot { q -> state.turns.any { it.fromUser && it.text == q } }.take(2).forEach { q -> Chip(q, small = true) { onAsk(q) } }
                    }
                }
            }
            if (state.thinking) {
                item { Text(stringResource(R.string.ask_thinking), fontSize = 12.sp, color = CaptionMuted, modifier = Modifier.padding(4.dp)) }
            }
        }
        state.error?.let { Text(it, fontSize = 12.sp, color = StateLow, modifier = Modifier.padding(vertical = 6.dp)) }
        val speak = com.sukoon.app.ui.logbook.rememberSpeech(stringResource(R.string.ask_mic)) { said -> said?.let { input = it } }
        InputRow(input, { input = it }, canSend = input.isNotBlank() && !state.thinking, onMic = { speak() }) {
            onAsk(input)
            input = ""
        }
    }
}

/** "● Your last 7 days · 1,840 readings · 12 log entries", or the Demo data chip. */
@Composable
private fun BasedOnLine(basedOn: BasedOn?) {
    basedOn ?: return
    if (basedOn.demo) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.ask2_demo_chip),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PillHighText,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(StateHigh.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Text(stringResource(R.string.ask2_demo_line), fontSize = 12.5.sp, color = CaptionMuted)
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Sage))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.ask2_based_on, basedOn.days, NumberFormat.getIntegerInstance().format(basedOn.readings), basedOn.entries),
            fontSize = 12.5.sp, color = CaptionMuted,
        )
    }
}

@Composable
private fun InsightChip(text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(SageMist).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_sparkle), null, tint = SageDeep, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.ask2_from_insights).uppercase(),
            fontSize = 9.5.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
            modifier = Modifier.border(1.dp, SageDeep.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Chip(text: String, small: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = if (small) 12.5.sp else 13.5.sp,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = if (small) 1 else 2,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (small) 8.dp else 11.dp),
    )
}

/** You at the end in sage; Sukoon at the start on the surface, with a small source caption. */
@Composable
private fun Bubble(turn: ChatTurn) {
    val user = turn.fromUser
    // The tail corner points at the speaker's side (bottom-end for you, bottom-start for the answer).
    val shape = if (user) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Text(
            turn.text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = if (user) Color.White else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(shape)
                .background(if (user) Sage else MaterialTheme.colorScheme.surface)
                .then(if (user) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), shape))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        )
        if (!user) {
            Row(Modifier.padding(top = 5.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_sparkle), null, tint = CaptionMuted, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ask2_gemini), fontSize = 11.5.sp, color = CaptionMuted)
            }
        }
    }
}

@Composable
private fun InputRow(value: String, onValue: (String) -> Unit, canSend: Boolean, onMic: () -> Unit = {}, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 13.dp),
        ) {
            if (value.isEmpty()) Text(stringResource(R.string.ask_hint), fontSize = 14.sp, color = CaptionMuted)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                maxLines = 4,
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(Sage),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        // Empty: the mic, to say the question; with text: send. (The chevron flips itself right to left.)
        if (value.isBlank()) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Sage).clickable(onClick = onMic), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.ask_mic), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        } else {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (canSend) Sage else Sage.copy(alpha = 0.4f)).clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_chevron), stringResource(R.string.ask_send), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun NoKey(onOpenSettings: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(SageMist), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_sparkle), null, tint = SageDeep, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.ask2_no_key_title), fontFamily = HeadlineSerifFontFamily, fontSize = 23.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ask2_no_key_body), fontSize = 14.sp, lineHeight = 20.sp, color = CaptionMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sage).clickable(onClick = onOpenSettings).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.ask2_add_key), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.ask2_key_path), fontSize = 12.5.sp, color = CaptionMuted)
    }
}

/** Insights' one thing, as a question worth asking (null when it doesn't make one). */
@Composable
private fun question(f: Finding): String? = when (val i = f.insight) {
    is LowsStory -> when {
        i.recurring?.overnight == true || (i.recurring == null && i.nightsWithLows >= 2) -> stringResource(R.string.ask2_q_lows_night)
        i.recurring != null -> stringResource(R.string.ask2_q_lows_hours, clock(i.recurring.fromHour), clock(i.recurring.toHour))
        else -> stringResource(R.string.ask2_q_lows)
    }
    is Insight.MealOutcomes -> stringResource(R.string.ask2_q_meal, slot(i.slot).lowercase())
    is Insight.RecurringHighs -> stringResource(R.string.ask2_q_highs, clock(i.fromHour), clock(i.toHour))
    is Insight.DawnRise -> stringResource(R.string.ask2_q_dawn)
    is Insight.Variability -> stringResource(R.string.ask2_q_swings)
    is Insight.Rebounds -> stringResource(R.string.ask2_q_rebound)
    is Insight.MeterAgreement -> stringResource(R.string.ask2_q_meter)
    is Insight.Rotation -> stringResource(R.string.ask2_q_sites)
    else -> null
}

private fun clock(hour: Int): String = LocalTime.of(hour.coerceIn(0, 23), 0).format(TimeFormat.of())
