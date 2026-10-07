package com.sukoon.app.ui.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.sukoon.app.R
import com.sukoon.app.data.export.NightscoutConfig
import com.sukoon.app.data.export.UploadStatus
import com.sukoon.app.ui.components.NumberChips
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateLow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import com.sukoon.app.data.export.ConnectionTest
import com.sukoon.app.ui.components.toast

/** You → Nightscout: Sukoon uploads readings + logbook (the job DiaBox used to do). Every save checks the connection and says how it went. */
@Composable
fun NightscoutSection(
    config: NightscoutConfig,
    status: UploadStatus,
    onSave: suspend (NightscoutConfig) -> ConnectionTest?,
    onUploadNow: suspend () -> UploadStatus,
    /** Inside another panel: no card of its own. */
    plain: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable(config.url) { mutableStateOf(config.url) }
    var secret by rememberSaveable(config.secret) { mutableStateOf(config.secret) }
    var busy by remember { mutableStateOf(false) }
    fun save(enabled: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val test = onSave(NightscoutConfig(enabled, normalize(url), secret))
            busy = false
            val message = when (test) {
                null -> context.getString(R.string.toast_ns_off)
                ConnectionTest.CanUpload -> context.getString(R.string.toast_ns_connected)
                ConnectionTest.SecretRejected -> context.getString(R.string.toast_ns_secret)
                ConnectionTest.Incomplete -> context.getString(R.string.toast_ns_incomplete)
                is ConnectionTest.Unreachable -> context.getString(R.string.toast_ns_unreachable, test.message)
            }
            context.toast(message, long = test != null && test != ConnectionTest.CanUpload)
        }
    }
    SectionCard(plain) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ns_upload), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.ns_upload_body), fontSize = 12.sp, color = CaptionMuted)
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = { save(it) },
                enabled = !busy,
                colors = SwitchDefaults.colors(checkedTrackColor = Sage),
            )
        }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.ns_url)) },
            placeholder = { Text("http://100.x.y.z:1337") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            label = { Text(stringResource(R.string.ns_secret)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(stringResource(if (busy) R.string.ns_checking else R.string.settings_save)) { save(config.enabled) }
            if (config.enabled) {
                Pill(stringResource(R.string.ns_upload_now), filled = false) {
                    scope.launch { context.toast(uploadText(context, onUploadNow()) ?: context.getString(R.string.toast_ns_incomplete)) }
                }
            }
        }
        uploadText(context, status)?.let { Text(it, fontSize = 12.sp, color = if (status is UploadStatus.Failed) StateLow else Sage) }
    }
}

/** The last upload as one line (null when uploads are off): shown under the card and toasted after Upload now. */
private fun uploadText(context: Context, status: UploadStatus): String? {
    val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    return when (status) {
        UploadStatus.Off -> null
        is UploadStatus.Ok -> context.getString(R.string.ns_status_ok, time.format(status.at), status.count)
        is UploadStatus.Failed -> context.getString(R.string.ns_status_failed, time.format(status.at), status.message)
    }
}

/** You → Your data: everything recorded, as a CSV you save wherever you like. */
@Composable
fun ExportSection(buildCsv: suspend (days: Int) -> Pair<String, Int>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by rememberSaveable { mutableIntStateOf(30) }
    var result by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching {
                val (csv, rows) = buildCsv(days)
                withContext(Dispatchers.IO) {
                    (context.contentResolver.openOutputStream(uri) ?: error("can't open the file")).use { it.write(csv.toByteArray()) }
                }
                context.getString(R.string.export_done, rows)
            }.getOrElse { context.getString(R.string.export_failed, it.message ?: it.javaClass.simpleName) }
            result = message
            context.toast(message)
        }
    }
    SectionCard {
        Text(stringResource(R.string.export_body), fontSize = 12.5.sp, color = CaptionMuted)
        NumberChips(listOf(1, 7, 30, 90, 0), days, 0..3650, { if (it == 0) stringResource(R.string.export_all) else stringResource(R.string.export_days, it) }) { days = it }
        Pill(stringResource(R.string.export_csv)) { save.launch("sukoon-${LocalDate.now()}.csv") }
        result?.let { Text(it, fontSize = 12.sp, color = Sage) }
    }
}

private fun normalize(raw: String): String {
    val url = raw.trim().trimEnd('/')
    return if (url.isEmpty() || url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
}

@Composable
private fun SectionCard(plain: Boolean = false, content: @Composable () -> Unit) {
    Column(
        if (plain) Modifier.fillMaxWidth() else Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
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
