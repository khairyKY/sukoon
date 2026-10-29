package com.sukoon.app.meter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.sukoon.app.R
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.repository.LogbookRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A paired meter as You → Glucose meter shows it. */
data class MeterState(
    val address: String? = null,
    val name: String? = null,
    val phase: Phase = Phase.IDLE,
    /** Meters seen while searching. */
    val found: List<Pair<String, String>> = emptyList(),
    /** The standard glucose service: null until the meter has been reached once. */
    val hasService: Boolean? = null,
    val onMeter: Int? = null,
    val lastSync: Instant? = null,
    /** Tests brought in by the last sync. */
    val imported: Int = 0,
    val error: Int? = null,
) {
    enum class Phase { IDLE, SEARCHING, PAIRING, WAITING, SYNCING }
}

/**
 * A Bluetooth glucose meter (CareSens S Fit BT and any other with the standard Glucose Profile):
 * pair once with the PIN on its screen, then each time it wakes and shows SYNC, the tests it
 * stored since the last sync come in as finger-pricks. Android keeps a background connection
 * waiting for it (autoConnect), so nothing needs opening.
 */
@SuppressLint("MissingPermission")
class MeterSync(private val context: Context, private val logbook: LogbookRepository, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("sukoon_meter", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val _state = MutableStateFlow(
        MeterState(
            address = prefs.getString(K_ADDRESS, null), name = prefs.getString(K_NAME, null),
            hasService = if (prefs.contains(K_SERVICE)) prefs.getBoolean(K_SERVICE, false) else null,
            onMeter = prefs.getInt(K_COUNT, -1).takeIf { it >= 0 },
            lastSync = prefs.getLong(K_SYNC, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        ),
    )
    val state: StateFlow<MeterState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private val ops = ArrayDeque<(BluetoothGatt) -> Unit>()
    private val tests = sortedMapOf<Int, GlucoseProfile.Measurement>()
    private val meals = mutableMapOf<Int, GlucoseProfile.Meal>()

    fun allowed() = Build.VERSION.SDK_INT < 31 ||
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Called at start: waits for the paired meter in the background. */
    fun start() {
        if (_state.value.address != null && allowed()) main.post { connect() }
    }

    // --- finding and pairing -----------------------------------------------------------------

    private val scan = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            val glucose = result.scanRecord?.serviceUuids?.any { it.uuid == GlucoseProfile.SERVICE } == true
            if (!glucose && !name.contains("CareSens", ignoreCase = true) && !name.contains("meter", ignoreCase = true)) return
            _state.update { s -> if (s.found.any { it.first == result.device.address }) s else s.copy(found = s.found + (result.device.address to name)) }
        }
    }

    /** "Find meter": 20 s of looking for meters in pairing mode (hold the button until bT shows). */
    fun search() {
        val scanner = adapter?.bluetoothLeScanner ?: return fail(R.string.meter_err_bluetooth)
        if (!allowed()) return fail(R.string.meter_err_permission)
        _state.update { it.copy(phase = MeterState.Phase.SEARCHING, found = emptyList(), error = null) }
        runCatching { scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scan) }
            .onFailure { return fail(R.string.meter_err_bluetooth) }
        main.postDelayed({ stopSearch() }, SEARCH_MS)
    }

    fun stopSearch() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scan) }
        _state.update { if (it.phase == MeterState.Phase.SEARCHING) it.copy(phase = MeterState.Phase.IDLE) else it }
    }

    private val bonding = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            if (device?.address != _state.value.address) return
            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                BluetoothDevice.BOND_BONDED -> { runCatching { context.unregisterReceiver(this) }; connect() }
                BluetoothDevice.BOND_NONE -> { runCatching { context.unregisterReceiver(this) }; fail(R.string.meter_err_pin) }
            }
        }
    }

    /** Pairs with a meter found by [search]: Android asks for the 6 digits on the meter's screen. */
    fun pair(address: String, name: String) {
        stopSearch()
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return fail(R.string.meter_err_bluetooth)
        forget(keepError = true)
        prefs.edit().putString(K_ADDRESS, address).putString(K_NAME, name).apply()
        _state.value = MeterState(address = address, name = name, phase = MeterState.Phase.PAIRING)
        if (device.bondState == BluetoothDevice.BOND_BONDED) return connect()
        ContextCompat.registerReceiver(context, bonding, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        if (!device.createBond()) fail(R.string.meter_err_pin)
    }

    /** Forgets the meter here (its pairing in Android's Bluetooth settings stays). */
    fun forget(keepError: Boolean = false) {
        runCatching { gatt?.close() }
        gatt = null
        prefs.edit().clear().apply()
        _state.value = MeterState(error = if (keepError) _state.value.error else null)
    }

    /** "Sync now": the background connection is already waiting; this re-arms it after an error. */
    fun syncNow() {
        if (_state.value.address == null) return
        runCatching { gatt?.close() }
        gatt = null
        connect()
    }

    private fun fail(message: Int) {
        _state.update { it.copy(phase = MeterState.Phase.IDLE, error = message) }
    }

    // --- syncing -----------------------------------------------------------------------------

    private fun connect() {
        val address = _state.value.address ?: return
        if (!allowed()) return fail(R.string.meter_err_permission)
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return fail(R.string.meter_err_bluetooth)
        runCatching { gatt?.close() }
        _state.update { it.copy(phase = MeterState.Phase.WAITING, error = null) }
        // Background connect: Android links up whenever the meter wakes, however long that takes.
        gatt = device.connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun next(g: BluetoothGatt) {
        ops.removeFirstOrNull()?.invoke(g)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _state.update { it.copy(phase = MeterState.Phase.SYNCING) }
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // The meter turns itself off after sending; the background connection waits for next time.
                    ops.clear()
                    _state.update { if (it.phase == MeterState.Phase.SYNCING) it.copy(phase = MeterState.Phase.WAITING) else it }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                val service = g.getService(GlucoseProfile.SERVICE)
                prefs.edit().putBoolean(K_SERVICE, service != null).apply()
                _state.update { it.copy(hasService = service != null) }
                if (service == null) {
                    fail(R.string.meter_err_no_service)
                    return@post
                }
                tests.clear()
                meals.clear()
                ops.clear()
                listOf(GlucoseProfile.MEASUREMENT to NOTIFY, GlucoseProfile.CONTEXT to NOTIFY, GlucoseProfile.RACP to INDICATE).forEach { (uuid, value) ->
                    service.getCharacteristic(uuid)?.let { c -> ops += { gg -> gg.setCharacteristicNotification(c, true); c.getDescriptor(GlucoseProfile.CCCD)?.let { writeDescriptor(gg, it, value) } ?: next(gg) } }
                }
                val racp = service.getCharacteristic(GlucoseProfile.RACP)
                if (racp == null) {
                    fail(R.string.meter_err_no_service)
                    return@post
                }
                ops += { gg -> writeCharacteristic(gg, racp, GlucoseProfile.requestCount()) }
                next(g)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            main.post { next(g) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.copyOf()
            main.post { onValue(g, characteristic.uuid, copy) }
        }

        @Deprecated("Pre-Android 13 delivery path")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            @Suppress("DEPRECATION")
            val copy = characteristic.value?.copyOf() ?: return
            main.post { onValue(g, characteristic.uuid, copy) }
        }
    }

    private fun onValue(g: BluetoothGatt, uuid: java.util.UUID, value: ByteArray) {
        when (uuid) {
            GlucoseProfile.MEASUREMENT -> GlucoseProfile.measurement(value)?.let { tests[it.sequence] = it }
            GlucoseProfile.CONTEXT -> GlucoseProfile.context(value)?.let { c -> c.meal?.let { meals[c.sequence] = it } }
            GlucoseProfile.RACP -> when (val reply = GlucoseProfile.racpReply(value)) {
                is GlucoseProfile.RacpReply.Count -> {
                    prefs.edit().putInt(K_COUNT, reply.records).apply()
                    _state.update { it.copy(onMeter = reply.records) }
                    val racp = g.getService(GlucoseProfile.SERVICE)?.getCharacteristic(GlucoseProfile.RACP) ?: return
                    val last = prefs.getInt(K_LAST_SEQ, -1)
                    writeCharacteristic(g, racp, if (last >= 0) GlucoseProfile.requestAfter(last) else GlucoseProfile.requestAll())
                }
                is GlucoseProfile.RacpReply.Done -> if (reply.ok) save() else fail(R.string.meter_err_sync)
                null -> Unit
            }
        }
    }

    /** The tests received, into the logbook as finger-pricks (one already logged by hand isn't doubled). */
    private fun save() {
        val received = tests.values.toList()
        val mealOf = meals.toMap()
        scope.launch {
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val existing = logbook.eventsSince(now.minus(Duration.ofDays(120)).toEpochMilli()).first().filter { it.type == LogEventType.FINGERSTICK.name }
            var added = 0
            received.forEach { m ->
                val mgDl = m.mgDl ?: return@forEach
                val at = m.at.atZone(zone).toInstant()
                if (at.isAfter(now.plus(Duration.ofMinutes(5)))) return@forEach // the meter's clock is ahead: skip, don't misplace
                val twin = existing.any { kotlin.math.abs(it.timestampMillis - at.toEpochMilli()) <= TWIN_MS && kotlin.math.abs((it.value ?: 0.0) - mgDl) < 1 }
                if (twin) return@forEach
                logbook.insert(EventEntity(timestampMillis = at.toEpochMilli(), type = LogEventType.FINGERSTICK.name, value = mgDl.toDouble(), note = mealOf[m.sequence]?.let(::mealNote)))
                added++
            }
            val lastSeq = (received.maxOfOrNull { it.sequence } ?: -1).coerceAtLeast(prefs.getInt(K_LAST_SEQ, -1))
            prefs.edit().putInt(K_LAST_SEQ, lastSeq).putLong(K_SYNC, now.toEpochMilli()).apply()
            _state.update { it.copy(lastSync = now, imported = added, error = null) }
        }
    }

    private fun mealNote(meal: GlucoseProfile.Meal) = context.getString(
        when (meal) {
            GlucoseProfile.Meal.BEFORE -> R.string.meter_before_meal
            GlucoseProfile.Meal.AFTER -> R.string.meter_after_meal
            GlucoseProfile.Meal.FASTING -> R.string.meter_fasting
            GlucoseProfile.Meal.BEDTIME -> R.string.meter_bedtime
            GlucoseProfile.Meal.CASUAL -> R.string.meter_casual
        },
    )

    @Suppress("DEPRECATION")
    private fun writeDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, value) else { d.value = value; g.writeDescriptor(d) }
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, value, type) else { c.writeType = type; c.value = value; g.writeCharacteristic(c) }
    }

    private companion object {
        val NOTIFY = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val INDICATE = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        const val SEARCH_MS = 20_000L
        /** A prick typed in by hand within this of a meter's test, same value: the same test. */
        const val TWIN_MS = 3 * 60_000L
        const val K_ADDRESS = "address"
        const val K_NAME = "name"
        const val K_SERVICE = "has_service"
        const val K_COUNT = "on_meter"
        const val K_SYNC = "last_sync"
        const val K_LAST_SEQ = "last_seq"
    }
}
