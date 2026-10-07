package com.sukoon.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.data.source.libre.SensorLife
import com.sukoon.app.ui.components.durationText
import com.sukoon.app.ui.components.sensorTime
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import java.time.Duration
import java.time.Instant

/** Home, in the sensor's last day: when it stops, so a new one is ready. Tap → You → Your sensor. */
@Composable
fun SensorEndingBanner(life: SensorLife.Running, onClick: () -> Unit) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.banner_sensor_ending, sensorTime(life.endsAt), durationText(context, Duration.between(Instant.now(), life.endsAt).toMinutes())),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PillHighBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = PillHighText,
    )
}
