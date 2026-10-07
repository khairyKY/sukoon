package com.sukoon.app.sharing

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.GlucoseSource
import com.sukoon.app.data.source.SourceStatus
import com.sukoon.app.ui.home.HomeUiStateMapper
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * This phone's glucose from a cloud service: a LibreLinkUp connection (someone's Libre on Abbott's
 * app, a Libre 3 say) or a Dexcom Share account. Polls once a minute; the first poll brings in what
 * the service keeps (12 h LibreLinkUp, 24 h Dexcom). Readings arrive a little after the sensor's,
 * and only while the internet works. [fetch] returns null when nothing is set up yet.
 */
class CloudSource(
    private val fetch: suspend () -> List<GlucoseReading>?,
    private val notSetUp: String,
    private val scope: CoroutineScope,
) : GlucoseSource {

    private val _readings = MutableSharedFlow<GlucoseReading>(extraBufferCapacity = 1000)
    override val readings: Flow<GlucoseReading> = _readings.asSharedFlow()

    private val _status = MutableStateFlow<SourceStatus>(SourceStatus.Disconnected)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    private var job: Job? = null
    private var newest: Instant = Instant.EPOCH

    override suspend fun connect() {
        if (job?.isActive == true) return
        _status.value = SourceStatus.Connecting
        job = scope.launch {
            while (isActive) {
                poll()
                delay(POLL.toMillis())
            }
        }
    }

    override suspend fun disconnect() {
        job?.cancel()
        job = null
        _status.value = SourceStatus.Disconnected
    }

    private suspend fun poll() {
        try {
            val all = fetch()
            if (all == null) {
                _status.value = SourceStatus.Error(notSetUp)
                return
            }
            val fresh = all.filter { it.timestamp > newest }
            fresh.forEach { _readings.emit(it) }
            fresh.lastOrNull()?.let { newest = it.timestamp }
            _status.value = if (Duration.between(newest, Instant.now()) <= HomeUiStateMapper.STALE_AFTER) SourceStatus.Connected else SourceStatus.Stale
        } catch (e: Exception) {
            _status.value = SourceStatus.Error(e.message ?: "unreachable")
        }
    }

    private companion object {
        val POLL: Duration = Duration.ofSeconds(60)
    }
}
