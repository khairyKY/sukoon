package com.sukoon.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sukoon.app.MainActivity
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.domain.metrics.TargetRange
import com.sukoon.app.insights.InsightDigestBuilder
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.ui.components.durationText
import com.sukoon.app.ui.insights.findingWords
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** You → Alarms → Gentle checks: yesterday in one notification at [minuteOfDay]. Off until turned on. */
data class MorningSummarySettings(val enabled: Boolean = false, val minuteOfDay: Int = 8 * 60)

/** Yesterday in numbers: [inRange] % (70–180), low episodes and the lowest of them, minutes above 180. */
data class Yesterday(val inRange: Int, val lows: Int, val lowest: Int?, val highMinutes: Long)

/**
 * The morning summary: once a day at the chosen time, yesterday (midnight to midnight) from this
 * phone's readings, plus the Insights screen's one thing when there is one. A quiet notification;
 * tapping it opens Trends › Insights. Nothing comes when yesterday has under 6 hours of readings.
 */
class MorningSummary : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SukoonApp).container
        val done = goAsync()
        container.appScope.launch {
            try {
                val settings = container.settings.morningSummary
                schedule(context, settings) // tomorrow's
                if (!settings.enabled) return@launch
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val since = today.minusDays(InsightEngine.WINDOW_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
                val readings = container.glucoseRepository.readingsSince(since).first()
                val y = yesterday(readings, today.minusDays(1).atStartOfDay(zone).toInstant(), today.atStartOfDay(zone).toInstant()) ?: return@launch
                val events = container.logbookRepository.eventsSince(since).first()
                val insight = withContext(Dispatchers.Default) {
                    val now = Instant.now()
                    val digest = InsightDigestBuilder.build(InsightEngine.analyze(readings, events, now, zone, TargetRange.high), events, now, zone)
                    digest.oneThing?.let { findingWords(context, it).first }?.takeIf { it.isNotBlank() }
                }
                val (title, body) = words(y, durationText(context, y.highMinutes), insight) { id, args -> context.getString(id, *args) }
                notify(context, title, body)
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        private const val CHANNEL = "morning_summary"
        private const val NOTIFICATION_ID = 3100
        private val MAX_GAP: Duration = Duration.ofMinutes(15)
        private val MIN_COVERED: Duration = Duration.ofHours(6)

        /** Sets (or, when off, cancels) tomorrow's summary. Safe to call any time: it replaces itself. */
        fun schedule(context: Context, settings: MorningSummarySettings) {
            val wake = PendingIntent.getBroadcast(context, 0, Intent(context, MorningSummary::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (settings.enabled) BasalReminder.at(context, BasalReminder.nextAt(ZonedDateTime.now(), settings.minuteOfDay).toInstant(), wake)
            else context.getSystemService(AlarmManager::class.java).cancel(wake)
        }

        /** [from]–[to] in numbers; null when under 6 hours of it has readings (each counts until the next, up to 15 min). */
        internal fun yesterday(all: List<GlucoseReading>, from: Instant, to: Instant): Yesterday? {
            val r = all.filter { it.timestamp >= from && it.timestamp < to }.sortedBy { it.timestamp }
            val seconds = r.indices.map { i -> minOf(Duration.between(r[i].timestamp, r.getOrNull(i + 1)?.timestamp ?: to), MAX_GAP).seconds }
            if (seconds.sum() < MIN_COVERED.seconds) return null
            val lows = InsightEngine.lowEpisodes(r)
            return Yesterday(
                inRange = InsightEngine.summary(r, to)!!.inRange.roundToInt(),
                lows = lows.size,
                lowest = lows.minOfOrNull { it.nadir },
                highMinutes = r.indices.filter { r[it].glucoseMgDl > 180 }.sumOf { seconds[it] } / 60,
            )
        }

        /** Title and body; [s] reads a string resource (Context.getString, a fake in tests). Under 5 minutes high is "no highs". */
        internal fun words(y: Yesterday, highFor: String, insight: String?, s: (Int, Array<out Any>) -> String): Pair<String, String> {
            val lows = if (y.lows == 0) s(R.string.morning_no_lows, emptyArray()) else s(R.string.morning_lows, arrayOf(y.lows, y.lowest ?: 0))
            val highs = if (y.highMinutes < 5) s(R.string.morning_no_highs, emptyArray()) else s(R.string.morning_highs, arrayOf(highFor))
            val body = listOfNotNull("$lows · $highs", insight?.let { s(R.string.morning_insight, arrayOf(it)) }).joinToString("\n")
            return s(R.string.morning_title, arrayOf(y.inRange)) to body
        }

        private fun notify(context: Context, title: String, body: String) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.morning_channel), NotificationManager.IMPORTANCE_LOW))
            }
            val open = PendingIntent.getActivity(
                context, 11,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_LINK, "trends_insights")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_sukoon)
                .setColor(0xFF3E7A63.toInt())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, n) }
        }
    }
}
