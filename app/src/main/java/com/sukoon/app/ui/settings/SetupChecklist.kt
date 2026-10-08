package com.sukoon.app.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sukoon.app.R
import com.sukoon.app.platform.BatteryOptimization
import com.sukoon.app.platform.SetupCheck
import com.sukoon.app.platform.SetupItem
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateHigh
import androidx.compose.ui.res.pluralStringResource

/** Re-reads what's missing every time the app comes back to the foreground (the fixes happen in Settings). */
@Composable
fun rememberMissingSetup(): List<SetupItem> {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(SetupCheck.missing(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { missing = SetupCheck.missing(context) }
    }
    return missing
}

/** What someone who only follows needs: their phone has to be able to wake them, nothing about a sensor. */
internal val FOLLOWER_SETUP_ITEMS = setOf(SetupItem.NOTIFICATIONS, SetupItem.FULL_SCREEN, SetupItem.OVERLAY, SetupItem.BATTERY)

/** You → Setup: every OS permission/setting Sukoon relies on, each with why and a one-tap fix ([only]: a subset, as onboarding asks it). */
@Composable
fun SetupChecklist(only: Set<SetupItem>? = null) {
    val context = LocalContext.current
    val missing = rememberMissingSetup() // returning from a dialog or Settings is a resume → re-checked
    var pending by remember { mutableStateOf<SetupItem?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val item = pending
        // Denied for good (no dialog shown any more) → the settings page is the only way left.
        if (item != null && !SetupCheck.isDone(context, item)) open(context, item)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SetupCheck.applicable(context).filter { only == null || it in only }.forEach { item ->
            val done = item !in missing
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(if (done) Sage else StateHigh))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(item.titleRes), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    if (!done) Text(stringResource(item.whyRes), fontSize = 12.sp, color = CaptionMuted) // what's done needs no reason
                }
                if (!done) {
                    Spacer(Modifier.width(8.dp))
                    SmallButton(stringResource(R.string.setup_allow)) {
                        val permissions = SetupCheck.runtimePermissions(item)
                        if (permissions.isNotEmpty()) {
                            pending = item
                            request.launch(permissions.toTypedArray())
                        } else {
                            open(context, item)
                        }
                    }
                }
            }
        }
        if (SetupCheck.hasOemBackgroundSettings()) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(CaptionMuted))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.setup_oem), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(stringResource(R.string.setup_oem_why), fontSize = 12.sp, color = CaptionMuted)
                }
                Spacer(Modifier.width(8.dp))
                SmallButton(stringResource(R.string.setup_open)) { BatteryOptimization.openOemBackgroundSettings(context) }
            }
        }
    }
}

/** Home banner while anything is missing; "Fix" jumps to the checklist. Hidden for the session by "Later". */
@Composable
fun SetupBanner(missing: List<SetupItem>, onFix: () -> Unit, onLater: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(StateHigh.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pluralStringResource(R.plurals.setup_banner, missing.size, missing.size),
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.setup_later),
            fontSize = 12.5.sp,
            color = CaptionMuted,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onLater).padding(8.dp),
        )
        SmallButton(stringResource(R.string.setup_fix), onClick = onFix)
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Sage)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
    }
}

private fun open(context: android.content.Context, item: SetupItem) {
    try {
        context.startActivity(SetupCheck.settingsIntent(context, item))
    } catch (e: ActivityNotFoundException) {
        // Some OEM builds lack a specific page — the app's own settings page always exists.
        context.startActivity(SetupCheck.settingsIntent(context, SetupItem.BLUETOOTH))
    }
}
