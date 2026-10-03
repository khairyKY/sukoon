package com.sukoon.app.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage

/**
 * Home, for a new user: the two things worth doing first (connect the sensor, add someone to
 * alert) and the guide. Steps tick themselves off; the card goes once both are done or dismissed.
 * Permissions have their own banner above it.
 */
@Composable
fun GettingStartedCard(sensorConnected: Boolean, hasEmergencyContact: Boolean, onSetUp: () -> Unit, onGuide: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.start_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Step(stringResource(R.string.start_sensor), sensorConnected, onSetUp)
        Step(stringResource(R.string.start_emergency), hasEmergencyContact, onSetUp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onGuide) { Text(stringResource(R.string.start_guide), color = Sage, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.start_dismiss), color = CaptionMuted) }
        }
    }
}

@Composable
private fun Step(label: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = !done, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (done) "✓" else "○", fontSize = 16.sp, color = if (done) Sage else CaptionMuted, modifier = Modifier.padding(end = 12.dp))
        Text(label, fontSize = 14.sp, color = if (done) CaptionMuted else MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        if (!done) Text("→", fontSize = 14.sp, color = Sage)
    }
}
