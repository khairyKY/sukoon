package com.sukoon.app.ui.sharing

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukoon.app.R
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.sharing.Followed
import com.sukoon.app.sharing.Person
import com.sukoon.app.sharing.Session
import com.sukoon.app.sharing.Sharing
import com.sukoon.app.sharing.SupabaseException
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.graph.GraphRange
import com.sukoon.app.ui.graph.GraphScreen
import com.sukoon.app.ui.graph.GraphUiState
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import com.sukoon.app.ui.widget.arrow
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** You → Sharing: an account, sharing your readings with people you invite, and following others. */
@Composable
fun SharingSection(sharing: Sharing) {
    val session by sharing.supabase.session.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val current = session
        when {
            !sharing.supabase.configured -> Text(stringResource(R.string.sharing_not_configured), fontSize = 12.5.sp, color = CaptionMuted)
            current == null -> SignIn(sharing)
            else -> SignedIn(sharing, current)
        }
    }
}

@Composable
private fun SignIn(sharing: Sharing) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Text(stringResource(R.string.sharing_body), fontSize = 12.5.sp, color = CaptionMuted)
    if (creating) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.sharing_your_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    OutlinedTextField(
        email, { email = it },
        label = { Text(stringResource(R.string.sharing_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        password, { password = it },
        label = { Text(stringResource(R.string.sharing_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    val ready = !busy && email.contains('@') && password.length >= 6 && (!creating || name.isNotBlank())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill(stringResource(if (busy) R.string.sharing_working else if (creating) R.string.sharing_create else R.string.sharing_sign_in), enabled = ready) {
            busy = true
            scope.launch {
                try {
                    if (creating) {
                        val session = sharing.supabase.signUp(email, password, name)
                        context.toast(
                            if (session == null) context.getString(R.string.toast_sharing_confirm_email, email.trim()) else context.getString(R.string.toast_sharing_signed_in),
                            long = true,
                        )
                        if (session == null) creating = false
                    } else {
                        sharing.supabase.signIn(email, password)
                        context.toast(context.getString(R.string.toast_sharing_signed_in))
                    }
                } catch (e: Exception) {
                    context.toast(readable(context, e), long = true)
                } finally {
                    busy = false
                }
            }
        }
        TextButton(onClick = { creating = !creating }) {
            Text(stringResource(if (creating) R.string.sharing_have_account else R.string.sharing_new_account), color = Sage, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SignedIn(sharing: Sharing, session: Session) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareOn by remember { mutableStateOf(sharing.shareOn) }
    var name by rememberSaveable { mutableStateOf("") }
    var followers by remember { mutableStateOf<List<Person>?>(null) }
    var following by remember { mutableStateOf<List<Followed>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var invite by rememberSaveable { mutableStateOf<String?>(null) }
    var enteringCode by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Followed?>(null) }

    LaunchedEffect(session.userId, refresh) {
        try {
            if (name.isBlank()) name = sharing.myName()
            followers = sharing.followers()
            following = sharing.following()
            problem = null
        } catch (e: Exception) {
            problem = readable(context, e)
        }
    }
    fun act(block: suspend () -> String?) {
        scope.launch {
            try {
                block()?.let { context.toast(it, long = true) }
            } catch (e: Exception) {
                context.toast(readable(context, e), long = true)
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.sharing_signed_in_as, session.email), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            sharing.supabase.signOut()
            context.toast(context.getString(R.string.toast_sharing_signed_out))
        }) { Text(stringResource(R.string.sharing_sign_out), color = StateLow, fontSize = 12.sp) }
    }
    problem?.let { Text(it, fontSize = 12.sp, color = StateLow) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.sharing_your_name)) }, singleLine = true, modifier = Modifier.weight(1f))
        Pill(stringResource(R.string.settings_save), filled = false) {
            act {
                sharing.setName(name)
                context.getString(R.string.toast_sharing_name_saved)
            }
        }
    }

    // Sharing: my readings, my followers, invites.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sharing_share), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.sharing_share_body), fontSize = 12.sp, color = CaptionMuted)
        }
        Switch(
            checked = shareOn,
            onCheckedChange = {
                shareOn = it
                sharing.shareOn = it
                if (it) act { sharing.upload(); null }
            },
            colors = SwitchDefaults.colors(checkedTrackColor = Sage),
        )
    }
    Label(stringResource(R.string.sharing_followers))
    val people = followers
    when {
        people == null -> Unit
        people.isEmpty() -> Text(stringResource(R.string.sharing_no_followers), fontSize = 12.sp, color = CaptionMuted)
        else -> people.forEach { person ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(person.name.ifBlank { "…" }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    act {
                        sharing.removeFollower(person.id)
                        refresh++
                        context.getString(R.string.toast_sharing_follower_removed, person.name)
                    }
                }) { Text(stringResource(R.string.emergency_remove), color = StateLow, fontSize = 12.sp) }
            }
        }
    }
    Pill(stringResource(R.string.sharing_invite)) {
        act {
            val code = sharing.createInvite()
            invite = code
            val text = context.getString(R.string.sharing_invite_message, name.ifBlank { session.email }, Sharing.formatCode(code))
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
            null
        }
    }
    invite?.let { Text(stringResource(R.string.sharing_invite_code, Sharing.formatCode(it)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Sage) }

    // Following: people who invited me.
    Label(stringResource(R.string.sharing_following))
    val followed = following
    when {
        followed == null -> Unit
        followed.isEmpty() -> Text(stringResource(R.string.sharing_not_following), fontSize = 12.sp, color = CaptionMuted)
        else -> followed.forEach { person ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { viewing = person }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(person.name.ifBlank { "…" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(latestLine(person.latest), fontSize = 12.sp, color = latestColor(person.latest))
                }
                TextButton(onClick = {
                    act {
                        sharing.stopFollowing(person.id)
                        refresh++
                        context.getString(R.string.toast_sharing_unfollowed, person.name)
                    }
                }) { Text(stringResource(R.string.sharing_stop_following), color = StateLow, fontSize = 12.sp) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pill(stringResource(R.string.sharing_enter_code), filled = false) { enteringCode = true }
        Pill(stringResource(R.string.sharing_refresh), filled = false) { refresh++ }
    }

    if (enteringCode) {
        CodeDialog(
            onRedeem = { code ->
                enteringCode = false
                act {
                    val owner = sharing.redeem(code)
                    refresh++
                    context.getString(R.string.toast_sharing_now_following, owner.ifBlank { "…" })
                }
            },
            onDismiss = { enteringCode = false },
        )
    }
    viewing?.let { person -> FollowViewer(sharing, person, onClose = { viewing = null }) }
}

/** Someone you follow, full screen: their current value and trend, chart, time in range and readings, refreshed every minute. */
@Composable
private fun FollowViewer(sharing: Sharing, person: Followed, onClose: () -> Unit) {
    val context = LocalContext.current
    var range by remember { mutableStateOf(GraphRange.H6) }
    var readings by remember { mutableStateOf<List<GlucoseReading>>(emptyList()) }
    LaunchedEffect(person.id, range) {
        while (true) {
            try {
                readings = sharing.readingsOf(person.id, System.currentTimeMillis() - range.millis)
            } catch (e: Exception) {
                context.toast(readable(context, e))
            }
            delay(60_000)
        }
    }
    val summary = remember(readings) { InsightEngine.summary(readings, Instant.now()) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("✕", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground) }
                }
                GraphScreen(
                    state = GraphUiState(range, readings, emptyList(), summary),
                    onSelectRange = { range = it },
                    modifier = Modifier.weight(1f),
                    title = person.name.ifBlank { stringResource(R.string.sharing_following) },
                )
            }
        }
    }
}

@Composable
private fun CodeDialog(onRedeem: (String) -> Unit, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val clean = code.filter { it.isLetterOrDigit() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sharing_enter_code)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sharing_enter_code_body), fontSize = 13.sp, color = CaptionMuted)
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("ABCD-EFGH") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = clean.length == 8, onClick = { onRedeem(clean) }) { Text(stringResource(R.string.sharing_follow)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.emergency_cancel)) } },
    )
}

@Composable
private fun latestLine(r: GlucoseReading?): String {
    if (r == null) return stringResource(R.string.sharing_no_readings)
    val minutes = Duration.between(r.timestamp, Instant.now()).toMinutes().coerceAtLeast(0)
    val age = if (minutes < 1) stringResource(R.string.graph_just_now) else stringResource(R.string.graph_min_ago, minutes.toInt())
    return "${String.format(Locale.getDefault(), "%d", r.glucoseMgDl)} ${r.trend.arrow} · $age"
}

private fun latestColor(r: GlucoseReading?): Color = when {
    r == null || Duration.between(r.timestamp, Instant.now()).toMinutes() > 10 -> CaptionMuted
    r.glucoseMgDl < 70 -> StateLow
    r.glucoseMgDl > 180 -> StateHigh
    else -> Sage
}

/** Server errors in words a person can act on. */
private fun readable(context: android.content.Context, e: Exception): String = when {
    e is SupabaseException && (e.status == 404 || e.message.orEmpty().contains("does not exist")) -> context.getString(R.string.sharing_server_not_ready)
    e is SupabaseException && e.message.orEmpty().contains("Email not confirmed", ignoreCase = true) -> context.getString(R.string.sharing_confirm_first)
    e is SupabaseException && e.message.orEmpty().contains("Invalid login", ignoreCase = true) -> context.getString(R.string.sharing_wrong_login)
    e is SupabaseException -> e.message.orEmpty()
    else -> context.getString(R.string.sharing_offline, e.message ?: e.javaClass.simpleName)
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
}

@Composable
private fun Pill(label: String, filled: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Sage.copy(alpha = if (enabled) 1f else 0.4f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (filled) 18.dp else 6.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (filled) Color.White else Sage, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
