package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How steady the sensor's readings were over a day: the gaps, and the longest one. */
internal data class ReadingGaps(val gaps: Int, val longestMinutes: Long, val longestAt: Instant?) {
    companion object {
        /**
         * [readings] oldest first. A gap is two readings (or the newest and [now]) further apart than the
         * save interval plus 5 minutes, so a sparser save setting isn't mistaken for drops.
         */
        fun of(readings: List<GlucoseReading>, now: Instant, saveEveryMinutes: Int): ReadingGaps {
            val limit = Duration.ofMinutes(saveEveryMinutes.coerceAtLeast(1) + 5L)
            val spans = (readings.map { it.timestamp } + now).zipWithNext().filter { (a, b) -> Duration.between(a, b) > limit }
            val longest = spans.maxByOrNull { (a, b) -> Duration.between(a, b) }
            return ReadingGaps(spans.size, longest?.let { (a, b) -> Duration.between(a, b).toMinutes() } ?: 0, longest?.first)
        }
    }
}

/** You → Sensor: the last 24 hours' gaps, and what usually causes them. */
@Composable
fun ConnectionHealth(load: suspend () -> List<GlucoseReading>, saveEveryMinutes: Int) {
    var gaps by remember { mutableStateOf<ReadingGaps?>(null) }
    LaunchedEffect(saveEveryMinutes) { gaps = runCatching { ReadingGaps.of(load(), Instant.now(), saveEveryMinutes) }.getOrNull() }
    val g = gaps ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text(
            if (g.gaps == 0) stringResource(R.string.sensor_gaps_none)
            else stringResource(R.string.sensor_gaps, g.gaps, g.longestMinutes, TIME.format(g.longestAt)),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (g.gaps > 0) Text(stringResource(R.string.sensor_gaps_why), fontSize = 12.5.sp, lineHeight = 17.sp, color = CaptionMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
