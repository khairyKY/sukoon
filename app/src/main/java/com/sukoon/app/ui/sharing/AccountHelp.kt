package com.sukoon.app.ui.sharing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.sukoon.app.ui.components.SukoonTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.sharing.Supabase
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import com.sukoon.app.ui.theme.StateLow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Forgot password?": emails a link to a page where a new one is chosen. */
@Composable
fun ForgotPassword(supabase: Supabase, email: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.auth_forgot), color = SageDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    if (!open) return
    var address by rememberSaveable { mutableStateOf(email.trim()) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.auth_reset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.auth_reset_body), fontSize = 13.5.sp, lineHeight = 19.sp, color = CaptionMuted)
                SukoonTextField(
                    address, { address = it.trim() },
                    label = stringResource(R.string.sharing_email),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                problem?.let { Text(it, fontSize = 13.sp, color = com.sukoon.app.ui.theme.PillLowText) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && address.contains('@'), onClick = {
                busy = true
                problem = null
                scope.launch {
                    try {
                        supabase.sendPasswordReset(address)
                        open = false
                        context.toast(context.getString(R.string.toast_reset_sent, address), long = true)
                    } catch (e: Exception) {
                        problem = readable(context, e)
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(if (busy) R.string.sharing_working else R.string.auth_reset_send)) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.sensor_cancel)) } },
    )
}

/**
 * After creating an account: the confirmation email is on its way. Tapping its link on this phone
 * signs in by itself (sukoon://auth); "I've confirmed it" signs in with the password just typed,
 * for a link opened on another device. Resend waits a minute between sends.
 */
@Composable
fun CheckInbox(supabase: Supabase, email: String, password: String, onSignedIn: () -> Unit, onOtherEmail: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var wait by remember { mutableIntStateOf(60) }
    LaunchedEffect(wait) { if (wait > 0) { delay(1000); wait-- } }
    var problem by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SageMist).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.auth_inbox_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.auth_inbox_body, email.trim()), fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onBackground)
        problem?.let { Text(it, fontSize = 13.sp, color = com.sukoon.app.ui.theme.PillLowText) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (password.isNotEmpty()) Pill(stringResource(R.string.auth_confirmed_sign_in)) {
                scope.launch {
                    try {
                        supabase.signIn(email, password)
                        onSignedIn()
                    } catch (e: Exception) {
                        problem = readable(context, e)
                    }
                }
            }
            Pill(if (wait > 0) stringResource(R.string.auth_resend_in, wait) else stringResource(R.string.auth_resend), filled = false, enabled = wait == 0) {
                scope.launch {
                    try {
                        supabase.resendConfirmation(email)
                        wait = 60
                        problem = null
                        context.toast(context.getString(R.string.toast_resent, email.trim()))
                    } catch (e: Exception) {
                        problem = readable(context, e)
                    }
                }
            }
        }
        TextButton(onClick = onOtherEmail) { Text(stringResource(R.string.auth_other_email), color = SageDeep, fontSize = 13.sp) }
    }
}
