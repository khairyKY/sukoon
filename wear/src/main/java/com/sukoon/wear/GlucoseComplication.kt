package com.sukoon.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.Instant

/**
 * Sukoon on a watch face: the number and arrow (short text), or the number on a 40–400 arc (ranged
 * value). The watch face picks the colours; the minutes since the reading count up on their own, and
 * a reading older than 10 minutes shows as "---" (PLAN §5).
 */
class GlucoseComplication : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val latest = Latest.read(this) ?: return NoDataComplicationData()
        return data(request.complicationType, latest, System.currentTimeMillis())
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        data(type, Latest(mgDl = 112, trend = "STEADY", timestampMillis = System.currentTimeMillis()), System.currentTimeMillis())

    private fun data(type: ComplicationType, latest: Latest, now: Long): ComplicationData? {
        val stale = latest.stale(now)
        val shown = if (stale) "---" else "${latest.mgDl}${latest.arrow}"
        val text = PlainComplicationText.Builder(shown).build()
        val description = PlainComplicationText.Builder(
            if (stale) getString(R.string.no_current_reading) else getString(R.string.glucose_description, latest.mgDl),
        ).build()
        val ago = TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.SHORT_SINGLE_UNIT, CountUpTimeReference(Instant.ofEpochMilli(latest.timestampMillis))).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text, description).setTitle(ago).build()
            // An arc can't say "old": a stale reading leaves it empty rather than at a number that isn't current.
            ComplicationType.RANGED_VALUE -> if (stale) NoDataComplicationData() else
                RangedValueComplicationData.Builder(latest.mgDl.toFloat().coerceIn(MIN, MAX), MIN, MAX, description)
                    .setText(text)
                    .build()
            else -> null
        }
    }

    private companion object {
        const val MIN = 40f
        const val MAX = 400f
    }
}
