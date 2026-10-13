package com.sukoon.wear

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Woken by Play Services when the phone puts a new reading on the Data Layer: keeps it, then asks the
 * tile and every complication showing Sukoon to redraw.
 */
class LatestListener : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val newest = events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == Latest.PATH }
            .map { DataMapItem.fromDataItem(it.dataItem).dataMap }
            .map { Latest(it.getInt(Latest.KEY_MGDL), it.getString(Latest.KEY_TREND) ?: "", it.getLong(Latest.KEY_TIME), it.getInt(Latest.KEY_HIGH, 180)) }
            .maxByOrNull { it.timestampMillis }
            ?: return
        newest.save(this)
        TileService.getUpdater(this).requestUpdate(GlucoseTile::class.java)
        ComplicationDataSourceUpdateRequester.create(this, ComponentName(this, GlucoseComplication::class.java)).requestUpdateAll()
    }
}
