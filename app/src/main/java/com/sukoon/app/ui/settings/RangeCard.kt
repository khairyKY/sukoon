package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.domain.metrics.TargetRange
import com.sukoon.app.ui.components.NumberChips
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import java.util.Locale

/** Your target range: 70 to a top you pick (180 international, 140 "tight", or your own between). Onboarding and You → Alarms. */
@Composable
fun RangeCard(high: Int, onChange: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.range_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(String.format(Locale.getDefault(), "%d", TargetRange.LOW), fontFamily = HeadlineSerifFontFamily, fontWeight = FontWeight.Light, fontSize = 38.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.ob_to), fontSize = 14.sp, color = CaptionMuted, modifier = Modifier.padding(bottom = 8.dp))
            Text(String.format(Locale.getDefault(), "%d", high), fontFamily = HeadlineSerifFontFamily, fontWeight = FontWeight.Light, fontSize = 38.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.home_unit_mgdl), fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.padding(bottom = 8.dp))
        }
        NumberChips(listOf(140, 160, 180), high, TargetRange.HIGH_RANGE, { stringResource(R.string.range_chip, it) }, onPick = onChange)
        Text(stringResource(R.string.range_body), fontSize = 12.5.sp, color = CaptionMuted)
    }
}
