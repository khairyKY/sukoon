package com.sukoon.app.ui.help

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.insulin.DoseSettings
import com.sukoon.app.ui.theme.CaptionMuted

/** What onboarding no longer asks of a wearer, in the card's order; each opens its You page ([link], MainScaffold.openLink). */
enum class FollowUp(@StringRes val title: Int, val link: String) {
    ACCOUNT(R.string.finish_account, "people"),
    EMERGENCY(R.string.finish_emergency, "people"),
    EXTRAS(R.string.finish_extras, "apps"),
    DOSE(R.string.finish_dose, "insulin"),
}

/** Each [FollowUp] done, in order: signed in, a contact, extras looked at or meals connected, dose suggestions on or a ratio set. */
internal fun followUpsDone(signedIn: Boolean, hasContact: Boolean, extras: Boolean, dose: DoseSettings): List<Boolean> =
    listOf(signedIn, hasContact, extras, dose.enabled || dose.carbRatio.isNotEmpty())

/** Only once real readings come in ([start]: Start here's ticks) and alarms reach you, never during a low, and while something is left. */
internal fun followUpsDue(offered: Boolean, start: List<Boolean>, low: Boolean, done: List<Boolean>): Boolean =
    offered && start[0] && start[1] && !low && !done.all { it }

/** Under the low alarm's line, or a low (or falling fast) alarm going. */
internal fun lowNow(glucose: Int?, lowLine: Int, active: Set<AlarmType>): Boolean =
    (glucose != null && glucose < lowLine) || active.any { it.loud }

/**
 * Now's "Finish setting up" (after onboarding's first reading): account and sharing, emergency
 * contacts, extras, dose suggestions, ticked as done. Gone for good on "Got it" ([onDismiss] also
 * ends Start here, whose last step is in it) or once all are done. True while it shows, so Now's
 * other setup card and banner wait.
 */
@Composable
internal fun FinishSetupCard(start: List<Boolean>, onStep: (String) -> Unit, onDismiss: () -> Unit): Boolean {
    val container = (LocalContext.current.applicationContext as SukoonApp).container
    val settings = container.settings
    var offered by remember { mutableStateOf(settings.followUps) }
    if (!offered) return false
    val session by container.sharing.supabase.session.collectAsStateWithLifecycle()
    val latest by container.glucoseRepository.latestReading.collectAsStateWithLifecycle(initialValue = null)
    val active by container.alarms.active.collectAsStateWithLifecycle()
    val meals by produceState(false) { value = runCatching { container.healthConnect.canReadMeals() }.getOrDefault(false) }
    var extrasSeen by remember { mutableStateOf(settings.extrasSeen) }
    val done = followUpsDone(session != null, settings.emergency.contacts.isNotEmpty(), meals || extrasSeen, settings.doseSettings)
    if (done.all { it }) {
        LaunchedEffect(Unit) { settings.followUps = false; offered = false }
        return false
    }
    if (!followUpsDue(true, start, lowNow(latest?.glucoseMgDl, settings.alarmSettings.lowMgDl, active), done)) return false
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        StartHere(
            FollowUp.entries.map { GuideStart(stringResource(it.title), it.link) }, done,
            eyebrow = stringResource(R.string.finish_eyebrow),
            title = stringResource(R.string.finish_title),
            onStep = { link ->
                if (link == FollowUp.EXTRAS.link) { settings.extrasSeen = true; extrasSeen = true }
                onStep(link)
            },
        ) {
            TextButton(onClick = { settings.followUps = false; offered = false; onDismiss() }) { Text(stringResource(R.string.start_dismiss), color = CaptionMuted) }
        }
    }
    return true
}
