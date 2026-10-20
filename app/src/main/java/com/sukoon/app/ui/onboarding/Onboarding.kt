package com.sukoon.app.ui.onboarding

import com.sukoon.app.sharing.InviteCode
import com.sukoon.app.ui.components.CodeBoxes
import com.sukoon.app.ui.components.PasteCodeChip

import com.sukoon.app.ui.settings.RangeCard
import com.sukoon.app.ui.components.NumberChips
import androidx.compose.runtime.mutableIntStateOf
import android.app.LocaleManager
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.sukoon.app.ui.components.SukoonTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukoon.app.R
import com.sukoon.app.data.prefs.UserRole
import com.sukoon.app.data.source.libre.SensorLife
import com.sukoon.app.di.AppContainer
import com.sukoon.app.platform.SetupItem
import com.sukoon.app.sharing.SupabaseException
import com.sukoon.app.ui.components.SukoonMark
import com.sukoon.app.ui.settings.SensorCard
import com.sukoon.app.ui.settings.SetupChecklist
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import java.util.Locale
import kotlinx.coroutines.launch

internal enum class Step { WELCOME, ROLE, ACCOUNT, SENSOR, PERMISSIONS, ALARMS, CODE, FOLLOW_ALERTS, READY }

/**
 * Each role's steps, the first number soonest: a wearer pairs and lets alarms through, the rest waits
 * for Now's Finish setting up card; a follower needs the account to find the person. "I have an
 * account" ([haveAccount]) signs in right after the role.
 */
internal fun path(role: UserRole, haveAccount: Boolean = false): List<Step> = buildList {
    add(Step.WELCOME)
    add(Step.ROLE)
    if (haveAccount) add(Step.ACCOUNT)
    if (role.wears) addAll(listOf(Step.SENSOR, Step.PERMISSIONS, Step.ALARMS))
    if (role.follows) addAll(listOfNotNull(Step.ACCOUNT.takeUnless { haveAccount }, Step.CODE, Step.FOLLOW_ALERTS))
    if (role.wears) add(Step.READY)
}

private val WEARER_SETUP = setOf(SetupItem.NOTIFICATIONS, SetupItem.BLUETOOTH, SetupItem.DND, SetupItem.FULL_SCREEN, SetupItem.OVERLAY, SetupItem.BATTERY)
private val FOLLOWER_SETUP = setOf(SetupItem.NOTIFICATIONS, SetupItem.FULL_SCREEN, SetupItem.OVERLAY, SetupItem.BATTERY)

/**
 * First run (design "Getting in", "Wearer setup", "Follower setup"): welcome with the disclaimer,
 * the role, then only what that role needs ([path]), each step saying what it needs and why. A
 * wearer's account, contacts, extras and dose wait for Now's Finish setting up card.
 */
@Composable
fun Onboarding(container: AppContainer, onDone: (UserRole) -> Unit) {
    var role by rememberSaveable { mutableStateOf(UserRole.WEARER) }
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var signIn by rememberSaveable { mutableStateOf(false) }
    var followed by rememberSaveable { mutableStateOf("") }
    val steps = path(role, signIn)
    fun finish() {
        container.settings.role = role
        container.role.value = role
        container.settings.followUps = role.wears
        container.settings.onboarded = true
        onDone(role)
    }
    fun next() {
        val i = steps.indexOf(step)
        if (i == steps.lastIndex) finish() else step = steps[i + 1]
    }
    fun back() {
        val i = steps.indexOf(step)
        if (i > 0) step = steps[i - 1]
    }
    BackHandler(enabled = step != Step.WELCOME, onBack = ::back)
    val progress = (steps.indexOf(step)) to (steps.size - 1)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        when (step) {
            Step.WELCOME -> Welcome(
                onStart = { signIn = false; step = Step.ROLE },
                onHaveAccount = { signIn = true; step = Step.ROLE },
            )
            Step.ROLE -> RoleStep(role, progress, onBack = ::back, onPick = { role = it }, onContinue = ::next)
            Step.ACCOUNT -> AccountStep(container, role, signIn, progress, onBack = ::back, onDone = ::next)
            Step.SENSOR -> SensorStep(container, progress, onBack = ::back, onDone = ::next)
            Step.PERMISSIONS -> Page(
                progress = progress,
                onBack = ::back,
                title = stringResource(R.string.ob_permissions_title),
                body = stringResource(R.string.ob_permissions_body),
                primary = stringResource(R.string.ob_continue),
                onPrimary = ::next,
            ) { SetupChecklist(only = WEARER_SETUP) }
            Step.ALARMS -> AlarmsStep(container, progress, onBack = ::back, onDone = ::next)
            Step.CODE -> CodeStep(container, progress, onBack = ::back, onFollowed = { followed = it; next() })
            Step.FOLLOW_ALERTS -> FollowAlertsStep(container, followed, progress, onDone = ::next)
            Step.READY -> ReadyStep(container, onDone = ::finish)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Welcome · role · account
// ---------------------------------------------------------------------------------------------

@Composable
private fun Welcome(onStart: () -> Unit, onHaveAccount: () -> Unit) {
    val context = LocalContext.current
    var understood by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        if (Build.VERSION.SDK_INT >= 33) {
            val arabic = Locale.getDefault().language == "ar"
            Row(Modifier.align(Alignment.End).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)).padding(3.dp)) {
                listOf(false to "English", true to "عربي").forEach { (ar, label) ->
                    val on = ar == arabic
                    Box(
                        Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable(enabled = !on) {
                                context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(if (ar) "ar" else "en")
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) MaterialTheme.colorScheme.onBackground else CaptionMuted)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            SukoonMark(markSize = 76.dp)
            Text(stringResource(R.string.app_name), fontFamily = HeadlineSerifFontFamily, fontSize = 46.sp, lineHeight = 55.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 22.dp))
            Text(stringResource(R.string.ob_tagline), fontFamily = HeadlineSerifFontFamily, fontSize = 22.sp, lineHeight = 26.sp, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 22.dp))
            Text(stringResource(R.string.ob_welcome_body), fontSize = 15.sp, lineHeight = 23.sp, color = CaptionMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, outline(), RoundedCornerShape(14.dp))
                .clickable { understood = !understood }
                .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = understood, onCheckedChange = { understood = it }, colors = CheckboxDefaults.colors(checkedColor = Sage))
            Text(stringResource(R.string.ob_disclaimer), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(stringResource(R.string.ob_get_started), enabled = understood, onClick = onStart)
        TextButton(onClick = onHaveAccount, enabled = understood, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.ob_have_account), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (understood) SageDeep else CaptionMuted)
        }
    }
}

@Composable
private fun RoleStep(role: UserRole, progress: Pair<Int, Int>, onBack: () -> Unit, onPick: (UserRole) -> Unit, onContinue: () -> Unit) {
    Page(
        progress = progress,
        onBack = onBack,
        title = stringResource(R.string.ob_role_title),
        body = stringResource(R.string.ob_role_body),
        primary = stringResource(
            when (role) {
                UserRole.WEARER -> R.string.ob_role_continue_wearer
                UserRole.FOLLOWER -> R.string.ob_role_continue_follower
                UserRole.BOTH -> R.string.ob_role_continue_both
            },
        ),
        onPrimary = onContinue,
    ) {
        RoleCard(UserRole.WEARER, role, R.drawable.ic_sensor, R.string.ob_role_wearer, R.string.ob_role_wearer_body, R.string.ob_role_wearer_time,
            listOf(R.string.ob_need_sensor, R.string.ob_need_phone, R.string.ob_need_minutes), onPick)
        RoleCard(UserRole.FOLLOWER, role, R.drawable.ic_eye, R.string.ob_role_follower, R.string.ob_role_follower_body, R.string.ob_role_follower_time,
            listOf(R.string.ob_need_code, R.string.ob_need_account), onPick)
        RoleCard(UserRole.BOTH, role, R.drawable.ic_both, R.string.ob_role_both, R.string.ob_role_both_body, R.string.ob_role_both_time,
            listOf(R.string.ob_need_wearer, R.string.ob_need_code, R.string.ob_need_account), onPick)
    }
}

@Composable
private fun RoleCard(
    which: UserRole,
    picked: UserRole,
    @DrawableRes icon: Int,
    title: Int,
    body: Int,
    time: Int,
    needs: List<Int>,
    onPick: (UserRole) -> Unit,
) {
    val on = which == picked
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) Sage.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .border(if (on) 2.dp else 1.dp, if (on) Sage else outline(), RoundedCornerShape(18.dp))
            .clickable { onPick(which) }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Sage else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = if (on) Color.White else MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(body), fontSize = 13.5.sp, lineHeight = 19.sp, color = CaptionMuted, modifier = Modifier.padding(top = 4.dp))
            }
            Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (on) Sage else CaptionMuted.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(Sage))
            }
        }
        if (on) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Eyebrow(stringResource(R.string.ob_you_need))
                    Text(stringResource(time), fontSize = 12.5.sp, color = CaptionMuted)
                }
                needs.forEach { need ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Sage, modifier = Modifier.size(18.dp))
                        Text(stringResource(need), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}

/** An account: asked of a wearer only on "I have an account" (then optional), required to follow someone. */
@Composable
private fun AccountStep(container: AppContainer, role: UserRole, startWithSignIn: Boolean, progress: Pair<Int, Int>, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supabase = container.sharing.supabase
    val session by supabase.session.collectAsStateWithLifecycle()
    var signIn by rememberSaveable { mutableStateOf(startWithSignIn) }
    var name by rememberSaveable { mutableStateOf(container.settings.emergency.yourName) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    // Created, waiting for the email to be confirmed. Its link, tapped on this phone, signs in and moves on.
    var awaiting by rememberSaveable { mutableStateOf<String?>(null) }
    val optional = role == UserRole.WEARER
    val ready = !busy && email.contains('@') && password.length >= 6 && (signIn || name.isNotBlank())
    val current = session
    LaunchedEffect(current) { if (awaiting != null && current != null) { container.followerWatch.refreshNow(); onDone() } }

    fun submit() {
        busy = true
        problem = null
        scope.launch {
            try {
                if (signIn) {
                    supabase.signIn(email.trim(), password)
                } else {
                    container.settings.emergency = container.settings.emergency.copy(yourName = name.trim())
                    if (supabase.signUp(email.trim(), password, name.trim()) == null) {
                        awaiting = email.trim()
                        return@launch
                    }
                }
                container.followerWatch.refreshNow()
                onDone()
            } catch (e: Exception) {
                if (e is SupabaseException && e.message.orEmpty().contains("Email not confirmed", ignoreCase = true)) {
                    awaiting = email.trim()
                    return@launch
                }
                problem = when {
                    e is SupabaseException && e.message.orEmpty().contains("Invalid login", ignoreCase = true) -> context.getString(R.string.sharing_wrong_login)
                    e is SupabaseException -> e.message.orEmpty()
                    else -> context.getString(R.string.sharing_offline, e.message ?: e.javaClass.simpleName)
                }
            } finally {
                busy = false
            }
        }
    }

    Page(
        progress = progress,
        onBack = onBack,
        trailing = if (optional) stringResource(R.string.ob_not_now) to onDone else null,
        title = stringResource(R.string.ob_account_title),
        body = stringResource(if (optional) R.string.ob_account_body_optional else R.string.ob_account_body_required),
        primary = when {
            current != null || !supabase.configured -> stringResource(R.string.ob_continue)
            busy -> stringResource(R.string.sharing_working)
            signIn -> stringResource(R.string.sharing_sign_in)
            else -> stringResource(R.string.sharing_create)
        },
        primaryEnabled = current != null || !supabase.configured || (awaiting == null && ready),
        onPrimary = { if (current != null || !supabase.configured) onDone() else submit() },
    ) {
        val waitingFor = awaiting
        when {
            !supabase.configured -> Text(stringResource(R.string.sharing_not_configured), fontSize = 14.sp, color = CaptionMuted)
            current != null -> Text(stringResource(R.string.sharing_signed_in_as, current.email), fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            waitingFor != null -> com.sukoon.app.ui.sharing.CheckInbox(
                supabase, waitingFor, password,
                onSignedIn = {},
                onOtherEmail = { awaiting = null; signIn = false },
            )
            else -> {
                Segmented(listOf(stringResource(R.string.ob_new_account), stringResource(R.string.sharing_sign_in)), if (signIn) 1 else 0) { signIn = it == 1 }
                if (!signIn) {
                    Field(name, { name = it }, stringResource(R.string.sharing_your_name), helper = stringResource(R.string.ob_name_helper))
                }
                Field(email, { email = it }, stringResource(R.string.sharing_email), keyboard = KeyboardType.Email)
                Field(
                    password, { password = it }, stringResource(R.string.ob_password),
                    keyboard = KeyboardType.Password,
                    helper = stringResource(R.string.ob_password_helper),
                    hidden = !showPassword,
                    trailing = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(stringResource(if (showPassword) R.string.ob_hide else R.string.ob_show), color = SageDeep, fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
                problem?.let { Text(it, fontSize = 13.5.sp, color = com.sukoon.app.ui.theme.PillLowText) }
                if (signIn) com.sukoon.app.ui.sharing.ForgotPassword(supabase, email)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = SageDeep, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ob_account_privacy), fontSize = 13.sp, lineHeight = 19.sp, color = CaptionMuted)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Wearer: sensor · permissions · alarms · ready
// ---------------------------------------------------------------------------------------------

@Composable
private fun SensorStep(container: AppContainer, progress: Pair<Int, Int>, onBack: () -> Unit, onDone: () -> Unit) {
    val status by container.glucoseRepository.status.collectAsStateWithLifecycle()
    var pairing by remember { mutableStateOf(container.pairingStore.load()) }
    Page(
        progress = progress,
        onBack = onBack,
        title = stringResource(R.string.ob_sensor_title),
        body = stringResource(R.string.ob_sensor_body),
        primary = stringResource(if (pairing != null) R.string.ob_continue else R.string.ob_later),
        onPrimary = onDone,
        primaryFilled = pairing != null,
    ) {
        Card {
            listOf(R.string.ob_sensor_step1, R.string.ob_sensor_step2, R.string.ob_sensor_step3).forEachIndexed { i, text ->
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
                        Text(String.format(Locale.getDefault(), "%d", i + 1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(stringResource(text), fontSize = 14.5.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        Text(
            stringResource(R.string.ob_sensor_other_app),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PillHighBg).padding(horizontal = 14.dp, vertical = 12.dp),
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            color = PillHighText,
        )
        SensorCard(
            pairing = pairing,
            sensorSelected = pairing != null,
            status = status,
            onPaired = { read, at ->
                container.pairSensor(read, at)
                pairing = container.pairingStore.load()
            },
            onForget = {
                container.pairingStore.clear()
                pairing = null
            },
        )
    }
}

@Composable
private fun AlarmsStep(container: AppContainer, progress: Pair<Int, Int>, onBack: () -> Unit, onDone: () -> Unit) {
    var s by remember { mutableStateOf(container.settings.alarmSettings) }
    fun update(changed: com.sukoon.app.alarms.AlarmSettings) {
        container.settings.alarmSettings = changed.sanitized()
        s = container.settings.alarmSettings
    }
    val quiet = s.quietHighsFrom in 0..23 && s.quietHighsTo in 0..23 && s.quietHighsFrom != s.quietHighsTo
    Page(
        progress = progress,
        onBack = onBack,
        title = stringResource(R.string.ob_alarms_title),
        body = stringResource(R.string.ob_alarms_body),
        primary = stringResource(R.string.ob_looks_good),
        onPrimary = onDone,
    ) {
        var high by remember { mutableIntStateOf(container.settings.targetHigh) }
        RangeCard(high) { top ->
            // The high alarm follows the top of the range while it sits there.
            if (s.highMgDl == high) update(s.copy(highMgDl = top))
            container.settings.targetHigh = top
            high = container.settings.targetHigh
        }
        Card {
            AlarmRow(StateLow, stringResource(R.string.alarm_urgent_title), stringResource(R.string.ob_urgent_always), checked = null) {}
            AlarmRow(StateLow, stringResource(R.string.alarm_low_title), stringResource(R.string.ob_below, s.lowMgDl), s.lowEnabled, values = {
                NumberChips(listOf(65, 70, 75, 80), s.lowMgDl, 60..110, { stringResource(R.string.alarms_below, it) }) { update(s.copy(lowMgDl = it)) }
            }) { update(s.copy(lowEnabled = it)) }
            AlarmRow(StateLow.copy(alpha = 0.5f), stringResource(R.string.alarm_going_low_title), stringResource(R.string.ob_going_low_when), s.goingLowEnabled) { update(s.copy(goingLowEnabled = it)) }
            AlarmRow(StateHigh, stringResource(R.string.alarm_high_title), stringResource(R.string.ob_above, s.highMgDl), s.highEnabled, values = {
                NumberChips(listOf(140, 180, 250), s.highMgDl, 120..400, { stringResource(R.string.alarms_above, it) }) { update(s.copy(highMgDl = it)) }
            }) { update(s.copy(highEnabled = it)) }
            AlarmRow(CaptionMuted, stringResource(R.string.alarm_signal_title), stringResource(R.string.ob_after_min, s.signalLossMinutes), s.signalLossEnabled, values = {
                NumberChips(listOf(15, 20, 30), s.signalLossMinutes, 10..120, { stringResource(R.string.ob_after_min, it) }) { update(s.copy(signalLossMinutes = it)) }
            }) { update(s.copy(signalLossEnabled = it)) }
            AlarmRow(null, stringResource(R.string.ob_quiet_title), stringResource(R.string.ob_quiet_body), quiet) {
                update(if (it) s.copy(quietHighsFrom = 23, quietHighsTo = 7) else s.copy(quietHighsFrom = -1, quietHighsTo = -1))
            }
        }
    }
}

/** An alarm's switch; with [values], tapping the row opens its line (a low, a high, minutes) to change now. */
@Composable
private fun AlarmRow(dot: Color?, title: String, body: String, checked: Boolean?, values: (@Composable () -> Unit)? = null, onChange: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (values != null && checked == true) Modifier.clickable { open = !open } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot ?: Color.Transparent))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                if (values != null && checked == true) body + " · " + stringResource(if (open) R.string.ob_alarm_done else R.string.apps_change) else body,
                fontSize = 13.sp,
                color = CaptionMuted,
            )
        }
        if (checked == null) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = stringResource(R.string.ob_urgent_always), tint = CaptionMuted, modifier = Modifier.size(18.dp))
        } else {
            Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Sage))
        }
    }
    if (open && checked == true && values != null) Column(Modifier.padding(start = 22.dp, bottom = 10.dp)) { values() }
}

@Composable
private fun ReadyStep(container: AppContainer, onDone: () -> Unit) {
    val life = remember { container.sensorLife() }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (life is SensorLife.WarmingUp) {
                Box(Modifier.size(184.dp).clip(CircleShape).border(10.dp, SageMist, CircleShape), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format(Locale.getDefault(), "%d", life.minutesLeft), fontFamily = HeadlineSerifFontFamily, fontWeight = FontWeight.Light, fontSize = 58.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text(stringResource(R.string.ob_warmup_left), fontSize = 13.5.sp, color = CaptionMuted)
                    }
                }
            } else {
                SukoonMark(markSize = 76.dp)
            }
            Text(stringResource(R.string.ob_ready_title), fontFamily = HeadlineSerifFontFamily, fontSize = 34.sp, lineHeight = 41.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 24.dp))
            Text(
                stringResource(
                    when (life) {
                        is SensorLife.WarmingUp -> R.string.ob_ready_warming
                        is SensorLife.Running -> R.string.ob_ready_live
                        else -> R.string.ob_ready_no_sensor
                    },
                ),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = CaptionMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(24.dp))
            Card {
                Done(life != null && life !is SensorLife.Ended, stringResource(R.string.ob_done_sensor))
                Done(com.sukoon.app.ui.help.startDone()[1], stringResource(R.string.ob_done_alarms)) // the same check as Now's card
            }
            Text(stringResource(R.string.ob_ready_later), fontSize = 14.sp, lineHeight = 20.sp, color = CaptionMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
        }
        PrimaryButton(stringResource(R.string.ob_go_home), onClick = onDone)
    }
}

@Composable
private fun Done(done: Boolean, text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).then(if (done) Modifier.background(SageMist) else Modifier.border(2.dp, CaptionMuted.copy(alpha = 0.5f), CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = SageDeep, modifier = Modifier.size(15.dp))
        }
        Text(text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ---------------------------------------------------------------------------------------------
// Follower: invite code · alerts
// ---------------------------------------------------------------------------------------------

@Composable
private fun CodeStep(container: AppContainer, progress: Pair<Int, Int>, onBack: () -> Unit, onFollowed: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A tapped invite link (sukoon://follow) fills it in.
    var code by rememberSaveable { mutableStateOf(container.pendingInvite.value.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    Page(
        progress = progress,
        onBack = onBack,
        title = stringResource(R.string.ob_code_title),
        body = stringResource(R.string.ob_code_body),
        primary = stringResource(if (busy) R.string.sharing_working else R.string.sharing_follow),
        primaryEnabled = !busy && code.length == InviteCode.LENGTH,
        onPrimary = {
            busy = true
            problem = null
            scope.launch {
                try {
                    val owner = container.sharing.redeem(code)
                    container.pendingInvite.value = null
                    container.followerWatch.refreshNow()
                    onFollowed(owner)
                } catch (e: Exception) {
                    problem = if (e is SupabaseException) e.message.orEmpty() else context.getString(R.string.sharing_offline, e.message ?: e.javaClass.simpleName)
                } finally {
                    busy = false
                }
            }
        },
    ) {
        CodeBoxes(code, { code = it; problem = null })
        if (code.length < InviteCode.LENGTH) PasteCodeChip(onCode = { code = it }, onNone = { problem = context.getString(R.string.ob_paste_none) })
        problem?.let { Text(it, fontSize = 13.5.sp, color = com.sukoon.app.ui.theme.PillLowText) }
        Card {
            Text(stringResource(R.string.ob_code_no_app), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.ob_code_no_app_body), fontSize = 13.5.sp, lineHeight = 19.sp, color = CaptionMuted)
            TextButton(onClick = {
                val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, context.getString(R.string.ob_send_app_text))
                context.startActivity(Intent.createChooser(share, null))
            }) { Text(stringResource(R.string.ob_send_app), color = SageDeep, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun FollowAlertsStep(container: AppContainer, name: String, progress: Pair<Int, Int>, onDone: () -> Unit) {
    var alertsOn by remember { mutableStateOf(container.followerWatch.alertsOn) }
    val who = name.ifBlank { "…" }
    Page(
        progress = progress,
        onBack = null,
        title = stringResource(R.string.ob_following_title, who),
        body = null,
        primary = stringResource(R.string.ob_done),
        onPrimary = onDone,
    ) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ob_alert_me, who), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(stringResource(R.string.ob_alert_me_body, who), fontSize = 13.sp, lineHeight = 19.sp, color = CaptionMuted)
                }
                Switch(
                    checked = alertsOn,
                    onCheckedChange = {
                        container.followerWatch.alertsOn = it
                        alertsOn = it
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Sage),
                )
            }
        }
        Eyebrow(stringResource(R.string.ob_reach_you))
        SetupChecklist(only = FOLLOWER_SETUP)
    }
}

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

/** One onboarding page: back + progress, title and why, the content, and the next step's button. */
@Composable
private fun Page(
    progress: Pair<Int, Int>,
    onBack: (() -> Unit)?,
    title: String,
    body: String?,
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    primaryFilled: Boolean = true,
    /** A text action at the top right ("Not now", "Skip"). */
    trailing: Pair<String, () -> Unit>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.heightIn(min = 44.dp)) {
            if (onBack != null) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.ob_back), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                (1..progress.second).forEach { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= progress.first) Sage else outline()))
                }
            }
            trailing?.let { (label, onClick) ->
                TextButton(onClick = onClick) { Text(label, color = SageDeep, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, fontFamily = HeadlineSerifFontFamily, fontSize = 31.sp, lineHeight = 36.sp, color = MaterialTheme.colorScheme.onBackground)
            body?.let { Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = CaptionMuted, modifier = Modifier.padding(bottom = 4.dp)) }
            content()
            Spacer(Modifier.height(8.dp))
        }
        PrimaryButton(primary, enabled = primaryEnabled, filled = primaryFilled, onClick = onPrimary)
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, filled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (filled) Modifier.background(if (enabled) Sage else Sage.copy(alpha = 0.4f))
                else Modifier.border(1.5.dp, Sage, RoundedCornerShape(14.dp)),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Color.White else SageDeep)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) MaterialTheme.colorScheme.onBackground else CaptionMuted)
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    helper: String? = null,
    hidden: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    SukoonTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = helper,
        trailingIcon = trailing,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = CaptionMuted)
}

@Composable
private fun outline(): Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)
