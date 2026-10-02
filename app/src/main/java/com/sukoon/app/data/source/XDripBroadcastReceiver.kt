package com.sukoon.app.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sukoon.app.SukoonApp
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Receives the xDrip+-compatible `BgEstimate` broadcast that DiaBox sends when
 * *Settings → Integration → Share data with other apps* is on (the same feed AndroidAPS reads;
 * Juggluco and xDrip+ can send it too). Phone-local, no server needed.
 *
 * Registered twice: in the manifest (for senders that address receivers explicitly, works even
 * when Sukoon isn't running) and at runtime by AppContainer (for implicit broadcasts, which
 * Android 8+ only delivers to live receivers). A broadcast that hits both is deduped by the
 * readings table's unique timestamp index.
 *
 * Any app can send this action — the same trust model AAPS has with it. Readings outside
 * [PLAUSIBLE_MG_DL] or from the future are dropped.
 */
class XDripBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SukoonApp).container
        if (container.sourceConfig.value.kind != SourceKind.BROADCAST) return
        val extras = intent.extras ?: return
        val reading = readingFromXDrip(
            mgDl = extras.getDouble(EXTRA_BG, -1.0),
            timeMillis = extras.getLong(EXTRA_TIME, 0L),
            slopeName = extras.getString(EXTRA_SLOPE_NAME),
            nowMillis = System.currentTimeMillis(),
        ) ?: return
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.glucoseRepository.ingest(reading)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.eveningoutpost.dexdrip.BgEstimate"
        private const val EXTRA_BG = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
        private const val EXTRA_TIME = "com.eveningoutpost.dexdrip.Extras.Time"
        private const val EXTRA_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"

        /** Pure parse of the broadcast extras (Bundle-free so it runs in JVM tests). */
        fun readingFromXDrip(mgDl: Double, timeMillis: Long, slopeName: String?, nowMillis: Long): GlucoseReading? {
            val value = Math.round(mgDl).toInt()
            if (value !in PLAUSIBLE_MG_DL) return null
            if (timeMillis <= 0L || timeMillis > nowMillis + 5 * 60_000L) return null
            return GlucoseReading(Instant.ofEpochMilli(timeMillis), value, trendFromDirection(slopeName), SourceKind.BROADCAST)
        }
    }
}

/**
 * The [GlucoseSource] for broadcast mode. Readings arrive via [XDripBroadcastReceiver] straight
 * into the repository (the receiver can fire with no collector alive), so this only reports
 * "listening"; Home derives freshness from the newest reading's age, not from this status.
 */
object BroadcastSource : GlucoseSource {
    override val readings: Flow<GlucoseReading> = emptyFlow()
    override val status: StateFlow<SourceStatus> = MutableStateFlow(SourceStatus.Connected)
    override suspend fun connect() = Unit
    override suspend fun disconnect() = Unit
}
