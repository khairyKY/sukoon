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

/** You → Nightscout: Sukoon uploads readings + logbook (the job DiaBox used to do). */
@Composable
fun NightscoutSection(config: NightscoutConfig, status: UploadStatus, onSave: (NightscoutConfig) -> Unit, onUploadNow: () -> Unit) {
    var url by rememberSaveable(config.url) { mutableStateOf(config.url) }
    var secret by rememberSaveable(config.secret) { mutableStateOf(config.secret) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ns_upload), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.ns_upload_body), fontSize = 12.sp, color = CaptionMuted)
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = { onSave(NightscoutConfig(it, url, secret)) },
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
            Pill(stringResource(R.string.settings_save)) { onSave(NightscoutConfig(config.enabled, normalize(url), secret)) }
            if (config.enabled) Pill(stringResource(R.string.ns_upload_now), filled = false, onClick = onUploadNow)
        }
        val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        when (status) {
            UploadStatus.Off -> Unit
            is UploadStatus.Ok -> Text(stringResource(R.string.ns_status_ok, time.format(status.at), status.count), fontSize = 12.sp, color = Sage)
            is UploadStatus.Failed -> Text(stringResource(R.string.ns_status_failed, time.format(status.at), status.message), fontSize = 12.sp, color = StateLow)
        }
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
            result = runCatching {
                val (csv, rows) = buildCsv(days)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } }
                context.getString(R.string.export_done, rows)
            }.getOrElse { context.getString(R.string.export_failed, it.message ?: it.javaClass.simpleName) }
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
private fun SectionCard(content: @Composable () -> Unit) {
    Column(
        Modifier
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
