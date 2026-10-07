package com.sukoon.app.ui.sharing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukoon.app.R
import com.sukoon.app.sharing.DexcomException
import com.sukoon.app.sharing.DexcomShare
import com.sukoon.app.sharing.Followed
import com.sukoon.app.sharing.FollowerWatch
import com.sukoon.app.sharing.Sharing
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import com.sukoon.app.ui.theme.StateLow
import kotlinx.coroutines.launch

/** You → People → Dexcom: a Dexcom wearer's login (with Share on in the Dexcom app), followed like anyone else. */
@Composable
fun DexcomSection(dexcom: DexcomShare, sharing: Sharing, watch: FollowerWatch) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by dexcom.account.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf(DexcomShare.Region.OUTSIDE_US) }
    var busy by remember { mutableStateOf(false) }
    var person by remember { mutableStateOf<Followed?>(null) }
    var viewing by remember { mutableStateOf<Followed?>(null) }

    LaunchedEffect(account) { person = if (account == null) null else runCatching { dexcom.followed().firstOrNull() }.getOrNull() }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val signedIn = account
        if (signedIn == null) {
            Text(stringResource(R.string.dexcom_body), fontSize = 12.5.sp, color = CaptionMuted)
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.dexcom_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.dexcom_username)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.llu_password)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DexcomShare.Region.OUTSIDE_US to R.string.dexcom_region_world, DexcomShare.Region.US to R.string.dexcom_region_us).forEach { (r, label) ->
                    val on = r == region
                    Text(
                        stringResource(label),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (on) SageDeep else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) SageMist else MaterialTheme.colorScheme.surface)
                            .border(if (on) 1.5.dp else 1.dp, if (on) Sage else outline(), RoundedCornerShape(12.dp))
                            .clickable { region = r }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Text(
                stringResource(if (busy) R.string.llu_connecting else R.string.llu_connect),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Sage)
                    .clickable(enabled = !busy && username.isNotBlank() && password.isNotBlank()) {
                        busy = true
                        scope.launch {
                            val message = try {
                                val latest = dexcom.signIn(username, password, region, name)
                                password = ""
                                watch.refreshNow()
                                if (latest == null) context.getString(R.string.dexcom_connected_quiet) else context.getString(R.string.dexcom_connected, latest.glucoseMgDl)
                            } catch (e: DexcomException) {
                                context.getString(
                                    when {
                                        e.wrongLogin -> R.string.dexcom_wrong_login
                                        e.unreachable -> R.string.dexcom_unreachable
                                        else -> R.string.dexcom_failed
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
                Text(stringResource(R.string.sharing_signed_in_as, signedIn.username), fontSize = 12.5.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    dexcom.signOut()
                    watch.refreshNow()
                }) { Text(stringResource(R.string.sharing_sign_out), color = StateLow, fontSize = 12.sp) }
            }
            person?.let { p ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { viewing = p }.padding(vertical = 4.dp)) {
                    Text(p.name.ifBlank { "…" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(latestLine(p.latest), fontSize = 12.sp, color = latestColor(p.latest))
                }
            }
        }
    }
    viewing?.let { p -> FollowViewer(sharing, p) { viewing = null } }
}
