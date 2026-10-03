package com.sukoon.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.sukoon.app.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.ui.theme.Sage

/** Single-choice chip row that wraps — the settings pickers (graph range, alarm levels, intervals). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(choices: List<T>, selected: T, label: @Composable (T) -> String, enabled: Boolean = true, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            val isSelected = choice == selected
            Text(
                label(choice),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                    isSelected -> Color.White
                    else -> MaterialTheme.colorScheme.onBackground
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isSelected && enabled) Modifier.background(Sage)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    )
                    .clickable(enabled = enabled) { onPick(choice) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

/**
 * [ChoiceChips] for numbers, plus a "Custom…" chip that opens a number field limited to [range]
 * (the safety bounds live in the caller's range, e.g. a low alarm can't go below urgent low).
 * A custom value shows as its own selected chip.
 */
@Composable
fun NumberChips(
    choices: List<Int>,
    selected: Int,
    range: IntRange,
    label: @Composable (Int) -> String,
    enabled: Boolean = true,
    onPick: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val shown: List<Int?> = (if (selected in choices) choices else (choices + selected).sorted()) + listOf(null)
    ChoiceChips(shown, selected, { value -> value?.let { label(it) } ?: stringResource(R.string.custom_value) }, enabled) { pick ->
        if (pick == null) editing = true else onPick(pick)
    }
    if (editing) {
        var text by remember { mutableStateOf(selected.toString()) }
        val value = text.trim().toIntOrNull()
        val valid = value != null && value in range
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.custom_value_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = { Text(stringResource(R.string.custom_value_range, range.first, range.last)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = { onPick(value!!); editing = false }) { Text(stringResource(R.string.custom_value_set)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.sensor_cancel)) } },
        )
    }
}
