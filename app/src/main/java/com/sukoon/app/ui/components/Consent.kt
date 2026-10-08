package com.sukoon.app.ui.components

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sukoon.app.R

/**
 * Before data leaves the phone in a way you might not expect (emergency texts with your location;
 * summaries and photos to Google's AI): what goes where, once, with an explicit "I agree". Google Play
 * asks for exactly this; not agreeing leaves the feature off. Returns `ask { … }`: runs at once once agreed.
 */
@Composable
fun rememberConsent(key: String, titleRes: Int, bodyRes: Int): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(bodyRes)) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("consent_$key", true).apply()
                    pending = null
                    action()
                }) { Text(stringResource(R.string.consent_agree)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.consent_not_now)) } },
        )
    }
    return { action -> if (prefs.getBoolean("consent_$key", false)) action() else pending = action }
}
