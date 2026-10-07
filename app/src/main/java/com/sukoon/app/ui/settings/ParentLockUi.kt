package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.platform.ParentLock
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.StateLow

/**
 * The parent's PIN. [setting]: a new one, typed twice; otherwise one to check against [stored].
 * [onDone] gets the PIN once it's right (set, or matching).
 */
@Composable
internal fun PinDialog(setting: Boolean, stored: String?, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val ok = ParentLock.valid(pin) && (!setting || again == pin)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (setting) R.string.lock_set_title else R.string.lock_enter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (setting) R.string.lock_set_body else R.string.lock_enter_body), fontSize = 13.5.sp)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8); wrong = false },
                    label = { Text(stringResource(R.string.lock_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (setting) {
                    OutlinedTextField(
                        value = again,
                        onValueChange = { again = it.filter(Char::isDigit).take(8) },
                        label = { Text(stringResource(R.string.lock_pin_again)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
                if (wrong) Text(stringResource(R.string.lock_wrong), fontSize = 13.sp, color = StateLow)
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                if (setting || ParentLock.matches(pin, stored)) onDone(pin) else wrong = true
            }) { Text(stringResource(if (setting) R.string.lock_set else R.string.lock_unlock)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sensor_cancel)) } },
    )
}

/** "Parent lock · On/Off" with set, change or remove. Changing or removing asks for the current PIN first. */
@Composable
internal fun ParentLockRow(stored: String?, onChange: (String?) -> Unit) {
    var dialog by remember { mutableStateOf<String?>(null) } // "set", "check-remove", "check-change"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = CaptionMuted, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(stringResource(R.string.lock_title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(if (stored == null) R.string.lock_off_body else R.string.lock_on_body), fontSize = 12.5.sp, color = CaptionMuted)
        }
        if (stored == null) {
            Text(stringResource(R.string.lock_set), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SageDeep, modifier = Modifier.clickable { dialog = "set" }.padding(8.dp))
        } else {
            Text(stringResource(R.string.lock_remove), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SageDeep, modifier = Modifier.clickable { dialog = "check-remove" }.padding(8.dp))
        }
    }
    when (dialog) {
        "set" -> PinDialog(setting = true, stored = null, onDone = { onChange(ParentLock.encode(it)); dialog = null }, onDismiss = { dialog = null })
        "check-remove" -> PinDialog(setting = false, stored = stored, onDone = { onChange(null); dialog = null }, onDismiss = { dialog = null })
    }
}

/** What a child sees in place of the dose settings while the lock is on. */
@Composable
internal fun LockedNotice(onUnlock: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            .clickable(onClick = onUnlock)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = CaptionMuted, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.lock_locked), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.lock_unlock), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
    }
}
