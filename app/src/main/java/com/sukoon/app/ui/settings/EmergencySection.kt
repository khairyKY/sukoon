package com.sukoon.app.ui.settings

import android.Manifest
import android.content.Intent
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.emergency.EmergencyAlerts
import com.sukoon.app.emergency.EmergencyContact
import com.sukoon.app.emergency.EmergencySettings
import com.sukoon.app.emergency.PhoneNumbers
import com.sukoon.app.ui.components.NumberChips
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateLow
import androidx.compose.ui.res.pluralStringResource

/** You → Emergency contacts: who gets texted (the first one also called) when an urgent low goes unanswered. */
@Composable
fun EmergencySection(settings: EmergencySettings, alerts: EmergencyAlerts, onChange: (EmergencySettings) -> Unit) {
    val context = LocalContext.current
    var yourName by rememberSaveable { mutableStateOf(settings.yourName) }
    var addingNumber by rememberSaveable { mutableStateOf(false) }
    var permissionChecks by remember { mutableIntStateOf(0) } // bumped after a request, to re-read the grants
    val canText = remember(permissionChecks) { alerts.canText() }
    val canCall = remember(permissionChecks) { alerts.canCall() }

    fun add(contact: EmergencyContact) {
        when {
            contact.phone.isBlank() -> context.toast(context.getString(R.string.toast_emergency_no_number))
            settings.contacts.none { it.phone == contact.phone } -> {
                onChange(settings.copy(contacts = settings.contacts + contact))
                context.toast(context.getString(R.string.toast_emergency_added, contact.name))
            }
        }
    }
    // Picking one phone number grants read access to just that entry — no contacts permission needed.
    val pickNumber = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { c ->
                if (c.moveToFirst()) EmergencyContact(c.getString(0).orEmpty(), PhoneNumbers.normalize(c.getString(1).orEmpty(), alerts.callingCode())) else null
            }
        }.getOrNull()?.let(::add)
    }
    val askTextAndCall = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionChecks++ }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val allowed = granted.values.any { it }
        onChange(settings.copy(shareLocation = allowed))
        if (!allowed) context.toast(context.getString(R.string.toast_emergency_no_location))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.emergency_body), fontSize = 12.5.sp, color = CaptionMuted)
        OutlinedTextField(
            value = yourName,
            onValueChange = {
                yourName = it
                onChange(settings.copy(yourName = it))
            },
            label = { Text(stringResource(R.string.emergency_your_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        settings.contacts.forEachIndexed { i, contact ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(contact.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        if (i == 0) "${contact.phone} · ${stringResource(R.string.emergency_first_called)}" else contact.phone,
                        fontSize = 12.sp,
                        color = CaptionMuted,
                    )
                }
                if (i > 0) {
                    TextButton(onClick = { onChange(settings.copy(contacts = listOf(contact) + (settings.contacts - contact))) }) {
                        Text(stringResource(R.string.emergency_make_first), color = Sage, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { onChange(settings.copy(contacts = settings.contacts - contact)) }) {
                    Text(stringResource(R.string.emergency_remove), color = StateLow, fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(stringResource(R.string.emergency_add_contact)) {
                runCatching { pickNumber.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)) }.onFailure { addingNumber = true }
            }
            Pill(stringResource(R.string.emergency_add_number), filled = false) { addingNumber = true }
        }

        if (settings.contacts.isNotEmpty()) {
            Text(stringResource(R.string.emergency_after).uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
            NumberChips(EmergencySettings.AFTER_CHOICES, settings.afterMinutes, EmergencySettings.AFTER_RANGE, { pluralStringResource(R.plurals.alarms_minutes, it.toInt(), it) }) {
                onChange(settings.copy(afterMinutes = it))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.emergency_location), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(stringResource(R.string.emergency_location_body), fontSize = 12.sp, color = CaptionMuted)
                }
                Switch(
                    checked = settings.shareLocation && alerts.locationAllowed(),
                    onCheckedChange = { on ->
                        when {
                            !on -> onChange(settings.copy(shareLocation = false))
                            alerts.locationAllowed() -> onChange(settings.copy(shareLocation = true))
                            else -> askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Sage),
                )
            }
            if (canText && canCall) {
                Text("✓ " + stringResource(R.string.emergency_permissions_ok), fontSize = 12.sp, color = Sage)
            } else {
                Pill(stringResource(R.string.emergency_allow)) { askTextAndCall.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(stringResource(R.string.emergency_test), filled = false) {
                    val names = alerts.textAll(alerts.testText())
                    context.toast(
                        if (names.isEmpty()) context.getString(R.string.toast_emergency_no_sms) else context.getString(R.string.toast_emergency_test_sent, names.joinToString()),
                        long = true,
                    )
                }
                Pill(stringResource(R.string.emergency_test_whatsapp), filled = false) {
                    alerts.openWhatsApp(settings.contacts.first(), alerts.testText())
                }
            }
        }
    }
    if (addingNumber) {
        AddNumberDialog(alerts.callingCode(), onAdd = { add(it); addingNumber = false }, onDismiss = { addingNumber = false })
    }
}

/** Home → "Alert my emergency contact": the user asking for help themselves — text everyone, WhatsApp, or call. */
@Composable
fun EmergencyActionsDialog(alerts: EmergencyAlerts, latest: GlucoseReading?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val first = alerts.contacts.firstOrNull() ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.emergency_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DialogAction(stringResource(R.string.emergency_dialog_text_all)) {
                    val names = alerts.textAll(alerts.helpText(latest))
                    context.toast(
                        if (names.isEmpty()) context.getString(R.string.toast_emergency_no_sms) else context.getString(R.string.toast_emergency_texted, names.joinToString()),
                        long = true,
                    )
                    onDismiss()
                }
                DialogAction(stringResource(R.string.emergency_dialog_whatsapp, first.name)) {
                    alerts.openWhatsApp(first, alerts.helpText(latest))
                    onDismiss()
                }
                DialogAction(stringResource(R.string.emergency_dialog_call, first.name)) {
                    alerts.callFirst()
                    onDismiss()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.emergency_cancel)) } },
    )
}

@Composable
private fun AddNumberDialog(callingCode: String, onAdd: (EmergencyContact) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    val international = PhoneNumbers.normalize(phone, callingCode)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.emergency_add_number)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.emergency_name)) }, singleLine = true)
                OutlinedTextField(
                    phone,
                    { phone = it },
                    label = { Text(stringResource(R.string.emergency_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                if (international.isNotEmpty() && international != phone.trim()) Text(international, fontSize = 12.sp, color = CaptionMuted)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && international.count { it.isDigit() } >= 8, onClick = { onAdd(EmergencyContact(name.trim(), international)) }) {
                Text(stringResource(R.string.emergency_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.emergency_cancel)) } },
    )
}

@Composable
private fun DialogAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Sage,
    )
}

@Composable
private fun Pill(label: String, filled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Sage else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (filled) 18.dp else 6.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (filled) Color.White else Sage, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
