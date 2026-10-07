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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.ai.ChatTurn
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateLow

/**
 * Trends → Ask: a chat about your own last 7 days (readings + logbook), answered by Gemini.
 * Without a key it shows where to add one instead of an input that can only fail.
 */
@Composable
fun AskScreen(
    state: AskUiState,
    hasKey: Boolean,
    onAsk: (String) -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.turns.size, state.thinking) {
        val last = state.turns.size + (if (state.thinking) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }
    LaunchedEffect(state.unanswered) { state.unanswered?.let { input = it } }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.ask_title),
                fontFamily = HeadlineSerifFontFamily,
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (state.turns.isNotEmpty()) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.ask_clear), color = CaptionMuted, fontSize = 12.sp) }
            }
        }
        Text(stringResource(R.string.ask_subtitle), fontSize = 11.5.sp, color = CaptionMuted)
        Spacer(Modifier.height(12.dp))

        if (!hasKey) {
            NoKeyCard(onOpenSettings)
            return@Column
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.turns.isEmpty() && !state.thinking) {
                item { Suggestions(onAsk) }
            }
            items(state.turns) { turn -> Bubble(turn) }
            if (state.thinking) {
                item { Text(stringResource(R.string.ask_thinking), fontSize = 12.sp, color = CaptionMuted, modifier = Modifier.padding(4.dp)) }
            }
        }
        state.error?.let { Text(it, fontSize = 12.sp, color = StateLow, modifier = Modifier.padding(vertical = 6.dp)) }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.ask_hint), fontSize = 13.sp) },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.padding(4.dp))
            val canSend = input.isNotBlank() && !state.thinking
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSend) Sage else Sage.copy(alpha = 0.4f))
                    .clickable(enabled = canSend) { onAsk(input); input = "" }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(stringResource(R.string.ask_send), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Suggestions(onAsk: (String) -> Unit) {
    val suggestions = listOf(
        stringResource(R.string.ask_suggest_24h),
        stringResource(R.string.ask_suggest_highs),
        stringResource(R.string.ask_suggest_meal),
        stringResource(R.string.ask_suggest_week),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        suggestions.forEach { suggestion ->
            Text(
                suggestion,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .clickable { onAsk(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun Bubble(turn: ChatTurn) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (turn.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            turn.text,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            color = if (turn.fromUser) Color.White else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (turn.fromUser) Sage else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun NoKeyCard(onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
    ) {
        Text(stringResource(R.string.ask_no_key_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.ask_no_key_body), fontSize = 12.5.sp, color = CaptionMuted)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Sage)
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(stringResource(R.string.ask_open_settings), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}
