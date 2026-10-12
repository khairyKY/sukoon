package com.sukoon.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import com.sukoon.app.data.source.libre.ConnectionLog
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.components.durationText

import com.sukoon.app.platform.TimeFormat
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
import androidx.compose.ui.res.pluralStringResource

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
            else pluralStringResource(R.plurals.sensor_gaps, g.gaps, g.gaps, g.longestMinutes, TIME.format(g.longestAt)),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (g.gaps > 0) Text(stringResource(R.string.sensor_gaps_why), fontSize = 12.5.sp, lineHeight = 17.sp, color = CaptionMuted, modifier = Modifier.padding(top = 4.dp))
        Drops()
    }
}

/** The link's drops in the last 24 hours, newest first: when, how long it had lasted, why, and how soon it was back. */
@Composable
private fun Drops() {
    val log = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.connectionLog
    var drops by remember { mutableStateOf<List<ConnectionLog.Drop>?>(null) }
    LaunchedEffect(Unit) { drops = runCatching { log.dropsSince(Instant.now().minus(Duration.ofHours(24))) }.getOrNull() }
    val list = drops ?: return
    var all by remember { mutableStateOf(false) }
    Text(
        if (list.isEmpty()) stringResource(R.string.conn_no_drops) else pluralStringResource(R.plurals.conn_drops, list.size, list.size),
        fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 12.dp),
    )
    val context = LocalContext.current
    (if (all) list else list.take(5)).forEach { d ->
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                stringResource(R.string.conn_drop_line, TIME.format(d.at), durationText(context, (d.lastedSeconds + 59) / 60), dropReason(d.status)),
                fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                d.backAfterSeconds?.let { stringResource(R.string.conn_back_after, durationText(context, (it + 59) / 60)) } ?: stringResource(R.string.conn_not_back),
                fontSize = 12.sp, color = CaptionMuted,
            )
        }
    }
    if (list.size > 5) Text(
        stringResource(if (all) R.string.in2_less else R.string.conn_show_all),
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
        modifier = Modifier.padding(top = 8.dp).clickable { all = !all },
    )
}

/** Android's reason for a drop, in words (the common GATT statuses; anything else by its code). */
@Composable
private fun dropReason(status: Int): String = when (status) {
    8 -> stringResource(R.string.conn_reason_timeout)
    19 -> stringResource(R.string.conn_reason_sensor)
    22 -> stringResource(R.string.conn_reason_phone)
    ConnectionLog.STATUS_WATCHDOG -> stringResource(R.string.conn_reason_stuck)
    else -> stringResource(R.string.conn_reason_code, status)
}

private val TIME: DateTimeFormatter get() = TimeFormat.of()
