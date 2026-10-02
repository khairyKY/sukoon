package com.sukoon.app.data.source.libre

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.GlucoseSource
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.SourceStatus
import com.sukoon.app.domain.metrics.GlucoseMetrics
import com.sukoon.app.domain.metrics.GlucoseSample
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live glucose straight from a paired Libre 2 EU sensor over Bluetooth LE (Track B, B4–B6).
 *
 * Flow per connection (as GlucoseDirect does it): scan for service FDE3 → match the advertised
 * manufacturer data to the paired UID → connect → enable notifications on F002 → write the
 * 12-byte login (Libre2.streamingUnlockPayload, counter bumped every time) to F001 → the sensor
 * then sends one 46-byte packet a minute in three notifications (20 + 18 + 8 bytes) →
 * [Libre2.decryptBle] (CRC-checked) → [Libre2.parseBle] → readings.
 *
 * All callback work is posted to the main looper so state is only touched on one thread.
 * Permissions (BLUETOOTH_SCAN/CONNECT, or location on Android ≤ 11) are checked by the UI before
 * this source is selected; a SecurityException still surfaces as an Error status, never a crash.
 */
@SuppressLint("MissingPermission")
class LibreBleSource(
    private val context: Context,
    private val store: SensorPairingStore,
) : GlucoseSource {

    private val _readings = MutableSharedFlow<GlucoseReading>(extraBufferCapacity = 64)
    override val readings: Flow<GlucoseReading> = _readings.asSharedFlow()

    private val _status = MutableStateFlow<SourceStatus>(SourceStatus.Disconnected)
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val bluetooth get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private var pairing: SensorPairing? = null
    private var active = false
    private var scanning = false
    private var gatt: BluetoothGatt? = null
    private val chunks = HashMap<Int, ByteArray>()
    private var badPackets = 0

    override suspend fun connect() {
        main.post {
            pairing = store.load()
            if (pairing == null) {
                _status.value = SourceStatus.Error("No sensor paired — scan it in You → Your sensor")
                return@post
            }
            active = true
            startScan()
        }
    }

    override suspend fun disconnect() {
        main.post {
            active = false
            main.removeCallbacksAndMessages(null)
            stopScan()
            gatt?.close()
            gatt = null
            _status.value = SourceStatus.Disconnected
        }
    }

    // --- scanning ----------------------------------------------------------------------------

    private fun startScan() {
        if (!active || scanning || gatt != null) return
        val adapter = bluetooth
        if (adapter == null || !adapter.isEnabled) {
            _status.value = SourceStatus.Error("Bluetooth is off")
            main.postDelayed(::startScan, RETRY_MS)
            return
        }
        try {
            adapter.bluetoothLeScanner?.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback,
            ) ?: return
            scanning = true
            _status.value = SourceStatus.Connecting
            // Android quietly stops long-running scans; restart periodically while still looking.
            main.postDelayed({ if (scanning) { stopScan(); startScan() } }, SCAN_RESTART_MS)
        } catch (e: SecurityException) {
            _status.value = SourceStatus.Error("Bluetooth permission needed")
        }
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            bluetooth?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            main.post {
                val p = pairing ?: return@post
                if (!scanning || !isPairedSensor(result, p)) return@post
                Log.i(TAG, "Found paired sensor ${result.device.address}")
                stopScan()
                gatt = result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            main.post {
                scanning = false
                _status.value = SourceStatus.Error("Bluetooth scan failed ($errorCode)")
                main.postDelayed(::startScan, RETRY_MS)
            }
        }
    }

    /** Advertised manufacturer data carries UID bytes 0-5; bytes 6-7 are the fixed 07 E0 (or 7A E0). */
    private fun isPairedSensor(result: ScanResult, p: SensorPairing): Boolean {
        val data = result.scanRecord?.manufacturerSpecificData ?: return false
        for (i in 0 until data.size()) {
            val base = data.valueAt(i)
            if (base.size == 6 && SUFFIXES.any { (base + it).contentEquals(p.uid) }) return true
        }
        return result.scanRecord?.deviceName.equals("abbott${p.serial}", ignoreCase = true)
    }

    // --- GATT --------------------------------------------------------------------------------

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected, discovering services")
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected (status $status)")
                    g.close()
                    if (gatt === g) gatt = null
                    chunks.clear()
                    if (active) {
                        _status.value = SourceStatus.Connecting
                        main.postDelayed(::startScan, RECONNECT_MS)
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                val notify = g.getService(SERVICE)?.getCharacteristic(NOTIFY)
                val cccd = notify?.getDescriptor(CCCD)
                if (notify == null || cccd == null) {
                    Log.w(TAG, "Libre service/characteristics missing")
                    g.disconnect()
                    return@post
                }
                g.setCharacteristicNotification(notify, true)
                writeDescriptor(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            main.post {
                val p = pairing ?: return@post
                val write = g.getService(SERVICE)?.getCharacteristic(WRITE) ?: return@post
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Enabling notifications failed ($status)")
                    g.disconnect()
                    return@post
                }
                val count = store.nextUnlockCount()
                Log.i(TAG, "Logging in, unlock count $count")
                writeCharacteristic(g, write, Libre2.streamingUnlockPayload(p.uid, p.patchInfo, Libre2.UNLOCK_CODE, count))
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.i(TAG, "Login write status $status")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.copyOf()
            main.post { onChunk(copy) }
        }

        @Deprecated("Pre-Android 13 delivery path")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            @Suppress("DEPRECATION")
            val copy = characteristic.value?.copyOf() ?: return
            main.post { onChunk(copy) }
        }
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, value) else { d.value = value; g.writeDescriptor(d) }
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, value, type) else { c.writeType = type; c.value = value; g.writeCharacteristic(c) }
    }

    // --- packets -----------------------------------------------------------------------------

    private fun onChunk(value: ByteArray) {
        if (value.size !in CHUNK_SIZES) {
            Log.w(TAG, "Unexpected ${value.size}-byte notification")
            return
        }
        chunks[value.size] = value
        if (chunks.size < CHUNK_SIZES.size) return
        val packet = CHUNK_SIZES.fold(ByteArray(0)) { acc, size -> acc + chunks.getValue(size) }
        chunks.clear()
        onPacket(packet)
    }

    private fun onPacket(packet: ByteArray) {
        val p = pairing ?: return
        val decrypted = try {
            Libre2.decryptBle(p.uid, packet)
        } catch (e: Libre2.BleDecryptException) {
            badPackets++
            Log.w(TAG, "Dropped packet that failed CRC (${packet.hex("")})")
            if (badPackets >= 3) _status.value = SourceStatus.Error("Sensor data won't decrypt — re-pair the sensor")
            return
        }
        badPackets = 0
        val (age, points) = Libre2.parseBle(p.calibration, decrypted)
        val now = System.currentTimeMillis()
        Log.i(TAG, "Packet: age $age min, ${points.joinToString { "${it.minute}:${it.mgDl}" }}")

        // The sensor counts minutes on its own oscillator. If its minute 0 has drifted from the
        // phone's clock by more than the tolerance, re-anchor (one-off duplicate timestamps
        // around the jump are the price; without it, readings would slowly slide in time).
        val impliedStart = now - age * MINUTE_MS
        val anchored = if (abs(impliedStart - p.startMillis) > DRIFT_TOLERANCE_MS) {
            store.saveStart(impliedStart)
            SensorPairing(p.uid, p.patchInfo, p.fram, impliedStart).also { pairing = it }
        } else {
            p
        }

        _status.value = when {
            age < WARMUP_MINUTES -> SourceStatus.WarmingUp
            age >= p.lifetimeMinutes -> SourceStatus.Error("Sensor has expired")
            else -> SourceStatus.Connected
        }
        if (_status.value != SourceStatus.Connected) return // warm-up and expired values aren't glucose

        val plausible = points.filter { it.mgDl in PLAUSIBLE_MG_DL }
        val trend = GlucoseMetrics.trendFor(plausible.map { GlucoseSample(Instant.ofEpochMilli(anchored.startMillis + it.minute * MINUTE_MS), it.mgDl) })
        for (point in plausible) {
            _readings.tryEmit(
                GlucoseReading(Instant.ofEpochMilli(anchored.startMillis + point.minute * MINUTE_MS), point.mgDl, trend, SourceKind.LIBRE_BLE),
            )
        }
    }

    companion object {
        private const val TAG = "LibreBle"
        private fun uuid16(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805F9B34FB")
        val SERVICE: UUID = uuid16("FDE3")
        private val WRITE = uuid16("F001")
        private val NOTIFY = uuid16("F002")
        private val CCCD = uuid16("2902")

        private val SUFFIXES = listOf(byteArrayOf(0x07, 0xE0.toByte()), byteArrayOf(0x7A, 0xE0.toByte()))
        private val CHUNK_SIZES = listOf(20, 18, 8)

        const val MINUTE_MS = 60_000L
        private const val WARMUP_MINUTES = 60
        private const val RETRY_MS = 15_000L
        private const val RECONNECT_MS = 2_000L
        private const val SCAN_RESTART_MS = 10 * 60_000L
        // ponytail: fixed tolerance; tune if real sensors drift faster than ~2 min over a session.
        private const val DRIFT_TOLERANCE_MS = 2 * 60_000L
        /** Sensor-reported values outside this are calibration nonsense, not glucose (Libre itself shows LO < 40, HI > 500). */
        val PLAUSIBLE_MG_DL = 20..600
    }
}
