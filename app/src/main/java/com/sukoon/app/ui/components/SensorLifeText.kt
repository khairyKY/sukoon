package com.sukoon.app.ui.components

import android.content.Context
import com.sukoon.app.R
import com.sukoon.app.data.source.libre.SensorLife
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "Tue 14 Oct, 14:30": when a sensor stops. */
fun sensorTime(at: Instant): String = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm").withZone(ZoneId.systemDefault()).format(at)

/** "2 d 5 h", "5 h", "40 min". */
fun durationText(context: Context, minutes: Long): String = when {
    minutes >= 24 * 60 -> context.getString(R.string.life_in_days, (minutes / 1440).toInt(), ((minutes % 1440) / 60).toInt())
    minutes >= 60 -> context.getString(R.string.life_in_hours, (minutes / 60).toInt())
    else -> context.getString(R.string.life_in_minutes, minutes.coerceAtLeast(0).toInt())
}

/** The sensor card's line about where the sensor is in its life. */
fun lifeLine(context: Context, life: SensorLife, now: Instant): String = when (life) {
    is SensorLife.WarmingUp -> context.getString(R.string.sensor_life_warming, life.minutesLeft)
    is SensorLife.Running -> context.getString(R.string.sensor_life_running, sensorTime(life.endsAt), durationText(context, Duration.between(now, life.endsAt).toMinutes()))
    is SensorLife.Ended -> context.getString(R.string.sensor_life_ended, sensorTime(life.endedAt))
}
