package com.sukoon.app.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage

/**
 * Now, for a new user (Redesign Canvas §7): "Get set up", the Guide's three Start here steps, ticked
 * as they're done ([done]: sensor, alarms reach you, an emergency contact); a step opens its screen.
 * The card goes for good once all three are done (the first time) or on "Got it"; while it shows,
 * Now's setup banner waits.
 */
@Composable
fun GettingStartedCard(done: List<Boolean>, onStep: (String) -> Unit, onGuide: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val start = remember { GuideContent.load(context).start }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        StartHere(
            start, done,
            eyebrow = stringResource(R.string.start_eyebrow),
            title = stringResource(R.string.start_card_title),
            onStep = onStep,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.start_open_guide),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Sage).clickable(onClick = onGuide).padding(horizontal = 18.dp, vertical = 11.dp),
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.start_dismiss), color = CaptionMuted) }
            }
        }
    }
}
