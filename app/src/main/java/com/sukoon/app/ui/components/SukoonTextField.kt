package com.sukoon.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.LocalDarkTheme
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep

/**
 * Every text field in the app, instead of Material's outlined box: a soft filled surface, the label
 * as a caption above, a Sage edge only while focused (coral on an error).
 */
@Composable
fun SukoonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    suffix: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val onBg = MaterialTheme.colorScheme.onBackground
    val edge = if (isError) PillLowText else Sage
    val shape = RoundedCornerShape(14.dp)
    val style = LocalTextStyle.current.merge(TextStyle(fontSize = 16.sp, color = if (enabled) onBg else onBg.copy(alpha = 0.4f)))
    Column(modifier) {
        if (label != null) {
            Text(
                label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                color = when { isError -> PillLowText; focused -> SageDeep; else -> CaptionMuted },
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            textStyle = style,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            cursorBrush = SolidColor(edge),
            decorationBox = { field ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(shape)
                        .background(onBg.copy(alpha = if (LocalDarkTheme.current) 0.08f else 0.05f))
                        .then(if (focused || isError) Modifier.border(1.5.dp, edge, shape) else Modifier)
                        .padding(start = if (leadingIcon != null) 4.dp else 14.dp, end = if (trailingIcon != null) 4.dp else 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    leadingIcon?.invoke()
                    Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = style.copy(color = CaptionMuted), maxLines = 1)
                        field()
                    }
                    if (suffix != null) Text(suffix, fontSize = 14.sp, color = CaptionMuted, modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically))
                    trailingIcon?.invoke()
                }
            },
        )
        if (supportingText != null) {
            Text(supportingText, fontSize = 12.sp, color = if (isError) PillLowText else CaptionMuted, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
    }
}
