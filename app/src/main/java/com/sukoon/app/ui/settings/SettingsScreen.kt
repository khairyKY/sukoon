package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.BuildConfig
import com.sukoon.app.R
import com.sukoon.app.data.source.libre.LibreNfc
import com.sukoon.app.data.source.libre.SensorPairing
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.SourceStatus
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage

/**
 * You tab (A10, MVP slice): the paired Libre sensor (Track B), the data source (sensor or demo),
 * the AI key, and the about/disclaimer line. Units, theme, sharing and the rest of A10 land later.
 */
@Composable
fun SettingsScreen(
    sourceKind: SourceKind,
    status: SourceStatus,
    pairing: SensorPairing?,
    geminiKey: String,
    onSelectSource: (SourceKind) -> Unit,
    onPaired: (LibreNfc.SensorRead, Long) -> Unit,
    onForgetSensor: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyInput by rememberSaveable(geminiKey) { mutableStateOf(geminiKey) }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.home_nav_you), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.sensor_title))
        SensorCard(
            pairing = pairing,
            sensorSelected = sourceKind == SourceKind.LIBRE_BLE,
            status = status,
            onPaired = onPaired,
            onForget = onForgetSensor,
        )

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(R.string.settings_source_title))
        if (pairing != null) {
            SourceOption(SourceKind.LIBRE_BLE, sourceKind, R.string.settings_source_sensor, R.string.settings_source_sensor_body) {
                onSelectSource(SourceKind.LIBRE_BLE)
            }
        }
        SourceOption(SourceKind.SIMULATED, sourceKind, R.string.settings_source_demo, R.string.settings_source_demo_body) {
            onSelectSource(SourceKind.SIMULATED)
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(R.string.settings_ai_title))
        Text(stringResource(R.string.settings_ai_body), fontSize = 12.5.sp, color = CaptionMuted)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text(stringResource(R.string.settings_ai_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(stringResource(if (geminiKey.isBlank()) R.string.settings_save else R.string.settings_update)) { onSaveGeminiKey(keyInput) }
        if (geminiKey.isNotBlank()) {
            Text(stringResource(R.string.settings_ai_saved), fontSize = 11.5.sp, color = Sage, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(R.string.settings_about_title))
        Text(stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME), fontSize = 12.5.sp, color = CaptionMuted)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 10.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SourceOption(kind: SourceKind, selected: SourceKind, titleRes: Int, bodyRes: Int, onSelect: () -> Unit) {
    val isSelected = kind == selected
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isSelected, onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(16.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (isSelected) Sage else CaptionMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) Box(Modifier.size(8.dp).clip(CircleShape).background(Sage))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(titleRes), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(bodyRes), fontSize = 12.sp, color = CaptionMuted)
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Sage)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
