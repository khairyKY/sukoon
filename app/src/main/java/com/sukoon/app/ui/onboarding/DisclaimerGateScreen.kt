package com.sukoon.app.ui.onboarding

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.ui.components.SukoonMark
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.NeutralWarm
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import com.sukoon.app.ui.theme.SukoonTheme
import com.sukoon.app.ui.theme.UiFontFamily

/**
 * First-launch, must-accept safety gate (docs/PLAN.md §8). Copy, layout, and the crescent brand
 * mark are pulled from the shipped design (Sukoon Brand Directions.dc.html, screen 8a), in EN
 * and AR via string resources (res/values-ar). [onAccept] only fires once the user has both
 * ticked the acknowledgement and tapped the CTA — a real gate, not a decorative checkbox.
 */
@Composable
fun DisclaimerGateScreen(onAccept: () -> Unit) {
    var acknowledged by remember { mutableStateOf(false) }
    val subtleBorder = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
    ) {
        // Scrollable top section — protects against clipping on short screens or large font
        // scale. This is a genuinely important screen to never let overflow silently.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 8.dp),
        ) {
            SukoonMark()

            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.disclaimer_eyebrow),
                color = NeutralWarm,
                fontFamily = UiFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
            )

            Spacer(Modifier.height(11.dp))
            Text(
                text = stringResource(R.string.disclaimer_headline),
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = HeadlineSerifFontFamily,
                fontSize = 26.sp,
                lineHeight = 32.sp,
            )

            Spacer(Modifier.height(22.dp))
            DisclaimerBullet(dotColor = Sage, text = stringResource(R.string.disclaimer_bullet_1))
            Spacer(Modifier.height(15.dp))
            DisclaimerBullet(dotColor = StateHigh, text = stringResource(R.string.disclaimer_bullet_2))
            Spacer(Modifier.height(15.dp))
            DisclaimerBullet(dotColor = StateLow, text = stringResource(R.string.disclaimer_bullet_3))
        }

        // Fixed bottom block — always visible, mirrors the design's margin-top:auto anchoring
        // without the scroll/weight conflict a single scrollable Column would hit.
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, subtleBorder, RoundedCornerShape(12.dp))
                    .clickable { acknowledged = !acknowledged }
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (acknowledged) Sage else Color.Transparent)
                        .border(1.dp, if (acknowledged) Sage else subtleBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (acknowledged) {
                        Text(text = "✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(11.dp))
                Text(
                    text = stringResource(R.string.disclaimer_checkbox_label),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                )
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (acknowledged) Sage else Sage.copy(alpha = 0.35f))
                    .clickable(enabled = acknowledged, onClick = onAccept)
                    .padding(15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.disclaimer_cta),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun DisclaimerBullet(dotColor: Color, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DisclaimerGateLightPreview() {
    SukoonTheme(darkTheme = false) { DisclaimerGateScreen(onAccept = {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DisclaimerGateDarkPreview() {
    SukoonTheme(darkTheme = true) { DisclaimerGateScreen(onAccept = {}) }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun DisclaimerGateArabicPreview() {
    SukoonTheme(darkTheme = false) { DisclaimerGateScreen(onAccept = {}) }
}
