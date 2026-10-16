package com.sukoon.app.ui.sharing

import com.sukoon.app.sharing.InviteCode
import com.sukoon.app.ui.components.CodeBoxes
import com.sukoon.app.ui.components.PasteCodeChip
import com.sukoon.app.ui.components.durationText

import com.sukoon.app.ui.settings.LockedNotice
import com.sukoon.app.ui.theme.TextMuted
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
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
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.alarms.titleRes
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
import com.sukoon.app.sharing.FollowerWatch
import androidx.compose.ui.res.pluralStringResource

/** You → Sharing: an account, sharing your readings with people you invite, and following others. */
@Composable
fun SharingSection(sharing: Sharing, watch: FollowerWatch, locked: Boolean = false, onUnlock: () -> Unit = {}) {
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
            current == null -> SignIn(sharing, watch)
            else -> SignedIn(sharing, watch, current, locked, onUnlock)
        }
    }
}

@Composable
private fun SignIn(sharing: Sharing, watch: FollowerWatch) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // An account waiting for its email to be confirmed: the inbox card instead of the form.
    var awaiting by rememberSaveable { mutableStateOf<String?>(null) }

    Text(stringResource(R.string.sharing_body), fontSize = 12.5.sp, color = CaptionMuted)
    awaiting?.let { address ->
        CheckInbox(
            sharing.supabase, address, password,
            onSignedIn = {
                awaiting = null
                watch.refreshNow()
                context.toast(context.getString(R.string.toast_sharing_signed_in))
            },
            onOtherEmail = { awaiting = null; creating = true },
        )
        return
    }
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
                        if (session == null) awaiting = email.trim() else context.toast(context.getString(R.string.toast_sharing_signed_in))
                    } else {
                        sharing.supabase.signIn(email, password)
                        watch.refreshNow()
                        context.toast(context.getString(R.string.toast_sharing_signed_in))
                    }
                } catch (e: Exception) {
                    if (e is SupabaseException && e.message.orEmpty().contains("Email not confirmed", ignoreCase = true)) awaiting = email.trim()
                    else context.toast(readable(context, e), long = true)
                } finally {
                    busy = false
                }
            }
        }
        TextButton(onClick = { creating = !creating }) {
            Text(stringResource(if (creating) R.string.sharing_have_account else R.string.sharing_new_account), color = Sage, fontSize = 13.sp)
        }
    }
    if (!creating) ForgotPassword(sharing.supabase, email)
}

@Composable
private fun SignedIn(sharing: Sharing, watch: FollowerWatch, session: Session, locked: Boolean, onUnlock: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareOn by remember { mutableStateOf(sharing.shareOn) }
    var name by rememberSaveable { mutableStateOf("") }
    var followers by remember { mutableStateOf<List<Person>?>(null) }
    var following by remember { mutableStateOf<List<Followed>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var invite by rememberSaveable { mutableStateOf<String?>(null) }
    var waiting by remember { mutableStateOf<List<Pair<String, Instant>>>(emptyList()) }
    var enteringCode by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Followed?>(null) }
    var alertsOn by remember { mutableStateOf(watch.alertsOn) }

    LaunchedEffect(session.userId, refresh) {
        try {
            if (name.isBlank()) name = sharing.myName()
            followers = sharing.followers()
            following = sharing.following()
            waiting = runCatching { sharing.openInvites() }.getOrDefault(emptyList())
            problem = null
        } catch (e: Exception) {
            problem = readable(context, e)
        }
    }
    /** Behind the parent lock: anything that would stop a follower (a parent) getting this child's readings or alarms. */
    fun guard(change: () -> Unit) = if (locked) onUnlock() else change()
    var sharedAlarms by remember { mutableStateOf(sharing.sharedAlarms) }
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
            guard {
                sharing.supabase.signOut()
                watch.refreshNow()
                context.toast(context.getString(R.string.toast_sharing_signed_out))
            }
        }) { Text(stringResource(R.string.sharing_sign_out), color = com.sukoon.app.ui.theme.PillLowText, fontSize = 12.sp) }
    }
    // Deleting the account: it, its readings and every follow, both ways, gone for good (Play requires it in the app).
    var deleting by remember { mutableStateOf(false) }
    TextButton(onClick = { guard { deleting = true } }) { Text(stringResource(R.string.account_delete), color = com.sukoon.app.ui.theme.PillLowText, fontSize = 12.sp) }
    if (deleting) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    act {
                        sharing.supabase.rpc("delete_my_account", org.json.JSONObject())
                        sharing.supabase.signOut()
                        watch.refreshNow()
                        context.getString(R.string.account_deleted)
                    }
                }) { Text(stringResource(R.string.account_delete_go), color = com.sukoon.app.ui.theme.PillLowText) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.sensor_cancel)) } },
        )
    }
    problem?.let { Text(it, fontSize = 12.sp, color = com.sukoon.app.ui.theme.PillLowText) }
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
                guard {
                    shareOn = it
                    sharing.shareOn = it
                    if (it) act { sharing.upload(); null }
                }
            },
            colors = SwitchDefaults.colors(checkedTrackColor = Sage),
        )
    }
    // Which alarms followers' phones sound for you; urgent low always does.
    Label(stringResource(R.string.sharing_alarms_title))
    Text(stringResource(R.string.sharing_alarms_body), fontSize = 12.sp, color = CaptionMuted)
    if (locked) LockedNotice(onUnlock)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
    ) {
        AlarmType.entries.forEachIndexed { i, type ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
            val always = type == AlarmType.URGENT_LOW
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The alarm's colour, as on Now: lows red, high amber, signal loss quiet.
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (type.loud) StateLow else if (type == AlarmType.HIGH) StateHigh else TextMuted))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(type.titleRes), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    if (always) Text(stringResource(R.string.sharing_alarm_always), fontSize = 12.sp, color = CaptionMuted)
                }
                Switch(
                    checked = type in sharedAlarms,
                    enabled = !always,
                    onCheckedChange = { on ->
                        guard {
                            sharedAlarms = if (on) sharedAlarms + type else sharedAlarms - type
                            sharing.sharedAlarms = sharedAlarms
                            act {
                                sharing.publishSharedAlarms()
                                context.getString(R.string.toast_sharing_alarms_saved)
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Sage),
                )
            }
        }
    }
    var shareModes by remember { mutableStateOf(sharing.shareModes) }
    Row(Modifier.padding(top = 12.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sharing_modes), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.sharing_modes_body), fontSize = 12.sp, color = CaptionMuted)
        }
        Switch(checked = shareModes, onCheckedChange = { shareModes = it; sharing.shareModes = it }, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
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
                    guard {
                        act {
                            sharing.removeFollower(person.id)
                            refresh++
                            context.getString(R.string.toast_sharing_follower_removed, person.name)
                        }
                    }
                }) { Text(stringResource(R.string.emergency_remove), color = com.sukoon.app.ui.theme.PillLowText, fontSize = 12.sp) }
            }
        }
    }
    Pill(stringResource(R.string.sharing_invite)) {
        act {
            invite = sharing.createInvite()
            refresh++
            null
        }
    }
    // Invites nobody has used yet: show one again (to scan or resend), or cancel it.
    if (waiting.isNotEmpty()) {
        Text(stringResource(R.string.invites_waiting), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
        waiting.forEach { (code, until) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.invite_left, InviteCode.format(code), durationText(context, Duration.between(Instant.now(), until).toMinutes())),
                    fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { invite = code }) { Text(stringResource(R.string.invite_show), color = Sage, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = {
                    act {
                        sharing.cancelInvite(code)
                        refresh++
                        context.getString(R.string.toast_invite_cancelled)
                    }
                }) { Text(stringResource(R.string.invite_cancel), color = com.sukoon.app.ui.theme.PillLowText, fontSize = 12.sp) }
            }
        }
    }
    invite?.let { code ->
        InviteSheet(code, waiting.firstOrNull { it.first == code }?.second, from = name.ifBlank { session.email }, onDismiss = { invite = null })
    }

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
                if (!person.viaLibreLinkUp) TextButton(onClick = {
                    act {
                        sharing.stopFollowing(person.id)
                        watch.refreshNow()
                        refresh++
                        context.getString(R.string.toast_sharing_unfollowed, person.name)
                    }
                }) { Text(stringResource(R.string.sharing_stop_following), color = com.sukoon.app.ui.theme.PillLowText, fontSize = 12.sp) }
            }
        }
    }
    if (!followed.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sharing_alerts), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.sharing_alerts_body), fontSize = 12.sp, color = CaptionMuted)
            }
            Switch(
                checked = alertsOn,
                onCheckedChange = {
                    alertsOn = it
                    watch.alertsOn = it
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Sage),
            )
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
                    watch.refreshNow()
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
internal fun FollowViewer(sharing: Sharing, person: Followed, onClose: () -> Unit) {
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
internal fun CodeDialog(onRedeem: (String) -> Unit, onDismiss: () -> Unit, initial: String = "") {
    var code by rememberSaveable { mutableStateOf(initial) }
    var problem by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val ready = code.length == InviteCode.LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sharing_enter_code)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.sharing_enter_code_body), fontSize = 13.sp, color = CaptionMuted)
                CodeBoxes(code, { code = it; problem = null }, onDone = { onRedeem(code) }, autoFocus = initial.isEmpty())
                if (!ready) PasteCodeChip(onCode = { code = it }, onNone = { problem = context.getString(R.string.ob_paste_none) })
                problem?.let { Text(it, fontSize = 13.sp, color = com.sukoon.app.ui.theme.PillLowText) }
            }
        },
        confirmButton = { TextButton(enabled = ready, onClick = { onRedeem(code) }) { Text(stringResource(R.string.sharing_follow)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.emergency_cancel)) } },
    )
}

@Composable
internal fun latestLine(r: GlucoseReading?): String {
    if (r == null) return stringResource(R.string.sharing_no_readings)
    val minutes = Duration.between(r.timestamp, Instant.now()).toMinutes().coerceAtLeast(0)
    val age = if (minutes < 1) stringResource(R.string.graph_just_now) else pluralStringResource(R.plurals.graph_min_ago, minutes.toInt(), minutes.toInt())
    return "${String.format(Locale.getDefault(), "%d", r.glucoseMgDl)} ${r.trend.arrow} · $age"
}

@Composable
internal fun latestColor(r: GlucoseReading?): Color = when {
    r == null || Duration.between(r.timestamp, Instant.now()).toMinutes() > 10 -> CaptionMuted
    r.glucoseMgDl < 70 -> StateLow
    r.glucoseMgDl > 180 -> StateHigh
    else -> Sage
}

/** Server errors in words a person can act on. */
internal fun readable(context: android.content.Context, e: Exception): String = when {
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
internal fun Pill(label: String, filled: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
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
