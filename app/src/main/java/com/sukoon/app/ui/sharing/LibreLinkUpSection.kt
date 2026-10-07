package com.sukoon.app.ui.sharing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukoon.app.R
import com.sukoon.app.sharing.Followed
import com.sukoon.app.sharing.FollowerWatch
import com.sukoon.app.sharing.FollowVia
import com.sukoon.app.sharing.LibreLinkUp
import com.sukoon.app.sharing.LluException
import com.sukoon.app.sharing.LluProblem
import com.sukoon.app.sharing.Sharing
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateLow
import kotlinx.coroutines.launch

/**
 * You → People → LibreLinkUp: sign in with a LibreLinkUp account, and everyone sharing with it
 * (from Abbott's Libre app, any Libre including 3) is followed here like a Sukoon follow.
 */
@Composable
fun LibreLinkUpSection(llu: LibreLinkUp, sharing: Sharing, watch: FollowerWatch) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by llu.account.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<List<Followed>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var viewing by remember { mutableStateOf<Followed?>(null) }

    LaunchedEffect(account, refresh) {
        people = if (account == null) null else runCatching { llu.followed() }.getOrNull()
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val signedIn = account
        if (signedIn == null) {
            Text(stringResource(R.string.llu_body), fontSize = 12.5.sp, color = CaptionMuted)
            OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.sharing_email)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.llu_password)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(if (busy) R.string.llu_connecting else R.string.llu_connect),
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Sage)
                    .clickable(enabled = !busy && email.isNotBlank() && password.isNotBlank()) {
                        busy = true
                        scope.launch {
                            val message = try {
                                val found = llu.signIn(email, password)
                                password = ""
                                watch.refreshNow()
                                context.getString(R.string.llu_connected, found.size)
                            } catch (e: LluException) {
                                context.getString(
                                    when (e.problem) {
                                        LluProblem.WRONG_LOGIN -> R.string.llu_wrong_login
                                        LluProblem.ACCEPT_TERMS -> R.string.llu_accept_terms
                                        LluProblem.UNREACHABLE -> R.string.llu_unreachable
                                        LluProblem.OTHER -> R.string.llu_failed
                                    },
                                )
                            }
                            busy = false
                            context.toast(message, long = true)
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sharing_signed_in_as, signedIn.email), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    llu.signOut()
                    watch.refreshNow()
                }) { Text(stringResource(R.string.sharing_sign_out), color = StateLow, fontSize = 12.sp) }
            }
            val list = people
            when {
                list == null -> Unit
                list.isEmpty() -> Text(stringResource(R.string.llu_nobody), fontSize = 12.5.sp, color = CaptionMuted)
                else -> list.forEach { person ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { viewing = person }.padding(vertical = 4.dp)) {
                        Text(person.name.ifBlank { "…" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(latestLine(person.latest), fontSize = 12.sp, color = latestColor(person.latest))
                    }
                }
            }
        }
    }
    viewing?.let { person -> FollowViewer(sharing, person) { viewing = null; refresh++ } }
}

/** True for people followed through LibreLinkUp or Dexcom: they're managed in those apps or their own card, not unfollowed here. */
internal val Followed.viaLibreLinkUp: Boolean get() = via != FollowVia.SUKOON
