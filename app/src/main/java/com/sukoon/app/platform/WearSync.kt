package com.sukoon.app.platform

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sukoon.app.data.repository.GlucoseRepository
import com.sukoon.app.domain.metrics.TargetRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Puts each new reading on the Wearable Data Layer at [PATH], where the Sukoon watch app (wear/) picks
 * it up for its tile and complication. Real readings only: no demo data, nothing from a sensor's
 * first hour. Fire and forget: a phone without Play Services or without a watch just drops it,
 * never crashes or waits.
 */
class WearSync(private val context: Context, private val glucose: GlucoseRepository, private val scope: CoroutineScope) {

    fun start() {
        scope.launch {
            glucose.latestReading.filterNotNull().filter { it.source.real && !it.warmup }.distinctUntilChanged().collect { reading ->
                runCatching {
                    val request = PutDataMapRequest.create(PATH).apply {
                        dataMap.putInt("mgDl", reading.glucoseMgDl)
                        dataMap.putString("trend", reading.trend.name)
                        dataMap.putLong("timestampMillis", reading.timestamp.toEpochMilli())
                        dataMap.putInt("high", TargetRange.high) // the watch colours a number the way the phone does
                    }.asPutDataRequest().setUrgent()
                    Wearable.getDataClient(context).putDataItem(request)
                }
            }
        }
    }

    companion object {
        const val PATH = "/sukoon/latest"
    }
}
