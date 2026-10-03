package com.sukoon.app.insights

import com.sukoon.app.data.source.GlucoseReading
import kotlin.math.abs
import kotlin.math.roundToInt

/** A finger-prick (blood meter) value beside what the sensor said at that moment. */
data class MeterCheck(val meterMgDl: Int, val sensorMgDl: Int) {

    /** Sensor minus meter as a percent of the meter: + means the sensor read higher. */
    val percentDiff: Int get() = ((sensorMgDl - meterMgDl) * 100.0 / meterMgDl).roundToInt()

    /** The 20/20 band CGM accuracy studies use: within 20 mg/dL when the meter is under 100, else within 20 %. */
    val agrees: Boolean get() = abs(sensorMgDl - meterMgDl) <= if (meterMgDl < 100) 20.0 else meterMgDl * 0.2

    companion object {
        const val WINDOW_MS = 5 * 60_000L

        /** Pairs a finger-prick with the nearest sensor reading within 5 minutes; null when there's none. */
        fun of(meterMgDl: Int, atMillis: Long, readings: List<GlucoseReading>): MeterCheck? {
            if (meterMgDl <= 0) return null
            val nearest = readings.minByOrNull { abs(it.timestamp.toEpochMilli() - atMillis) } ?: return null
            return if (abs(nearest.timestamp.toEpochMilli() - atMillis) <= WINDOW_MS) MeterCheck(meterMgDl, nearest.glucoseMgDl) else null
        }
    }
}
