package com.sukoon.app.ui.components

import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.sharing.InviteCode
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist

/**
 * An invite code as eight boxes (ABCD–EFGH). The keyboard opens in capitals, anything typed becomes
 * capitals, and characters a code never has (O, 0, I, 1, symbols) are dropped. Pasting a whole
 * invite message or link into it keeps just the code.
 */
@Composable
fun CodeBoxes(code: String, onChange: (String) -> Unit, onDone: () -> Unit = {}, autoFocus: Boolean = true) {
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val label = stringResource(R.string.invite_code_label)
    BasicTextField(
        value = code,
        onValueChange = { typed -> onChange(InviteCode.find(typed) ?: InviteCode.normalize(typed)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (code.length == InviteCode.LENGTH) onDone() }),
        textStyle = TextStyle(color = Color.Transparent),
        modifier = Modifier.focusRequester(focus).semantics { contentDescription = label },
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    (0 until InviteCode.LENGTH).forEach { i ->
                        if (i == InviteCode.LENGTH / 2) Text("–", fontSize = 20.sp, color = CaptionMuted)
                        val focused = i == code.length
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(if (focused) 2.dp else 1.dp, if (focused) Sage else outline(), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(code.getOrNull(i)?.toString().orEmpty(), fontFamily = FontFamily.Monospace, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
                Box(Modifier.size(1.dp)) { inner() }
            }
        },
    )
}

/** "Paste": the code from the clipboard, out of a link or a whole message too. [onNone] when there isn't one. */
@Composable
fun PasteCodeChip(onCode: (String) -> Unit, onNone: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(SageMist)
            .clickable {
                val text = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                text?.let(InviteCode::find)?.let(onCode) ?: onNone()
            }
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_paste), contentDescription = null, tint = SageDeep, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.ob_paste), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
    }
}
