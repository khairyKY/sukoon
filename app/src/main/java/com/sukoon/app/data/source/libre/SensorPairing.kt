package com.sukoon.app.data.source.libre

import android.content.Context

/** The sensor this phone streams from — everything the BLE login and decoding need after the NFC tap. */
class SensorPairing(
    val uid: ByteArray,
    val patchInfo: ByteArray,
    /** Decrypted, CRC-checked FRAM from the pairing tap: calibration + lifetime live here. */
    val fram: ByteArray,
    /** Wall-clock time of sensor minute 0. Readings are stamped start + minute, so re-reads dedupe. */
    val startMillis: Long,
) {
    val calibration: FactoryCalibration get() = FactoryCalibration.fromFram(fram)
    val lifetimeMinutes: Int get() = Libre2.sensorInfo(fram).lifetimeMinutes
    val serial: String get() = Libre2.serial(uid, patchInfo)
}

/**
 * Persists the paired sensor and its BLE unlock counter (SharedPreferences, same file as the
 * other settings). The counter must strictly increase across connections for the sensor to
 * accept a login, so it's bumped and saved *before* each login is sent.
 */
class SensorPairingStore(context: Context) {
    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)

    fun load(): SensorPairing? {
        val uid = prefs.getString(KEY_UID, null)?.unhex() ?: return null
        val patch = prefs.getString(KEY_PATCH, null)?.unhex() ?: return null
        val fram = prefs.getString(KEY_FRAM, null)?.unhex() ?: return null
        return SensorPairing(uid, patch, fram, prefs.getLong(KEY_START, 0L))
    }

    fun save(pairing: SensorPairing) {
        prefs.edit()
            .putString(KEY_UID, pairing.uid.hex(""))
            .putString(KEY_PATCH, pairing.patchInfo.hex(""))
            .putString(KEY_FRAM, pairing.fram.hex(""))
            .putLong(KEY_START, pairing.startMillis)
            .putInt(KEY_UNLOCK_COUNT, 0) // a fresh Enable Streaming resets the sensor's login counter
            .commit()
    }

    /** Re-anchors minute 0 when the sensor's clock has drifted from the phone's (see LibreBleSource). */
    fun saveStart(startMillis: Long) {
        prefs.edit().putLong(KEY_START, startMillis).apply()
    }

    /** The sensor's BLE address (from Enable Streaming, or the first scan match) — lets us connect without scanning. */
    var bleAddress: String?
        get() = prefs.getString(KEY_MAC, null)
        set(value) = prefs.edit().putString(KEY_MAC, value).apply()

    fun nextUnlockCount(): Int {
        val next = prefs.getInt(KEY_UNLOCK_COUNT, 0) + 1
        prefs.edit().putInt(KEY_UNLOCK_COUNT, next).commit()
        return next
    }

    fun clear() {
        prefs.edit().remove(KEY_UID).remove(KEY_PATCH).remove(KEY_FRAM).remove(KEY_START).remove(KEY_UNLOCK_COUNT).remove(KEY_MAC).apply()
    }

    private fun String.unhex(): ByteArray? =
        if (length % 2 != 0) null else runCatching { ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()

    private companion object {
        const val KEY_UID = "libre_uid"
        const val KEY_PATCH = "libre_patch_info"
        const val KEY_FRAM = "libre_fram"
        const val KEY_START = "libre_start_millis"
        const val KEY_UNLOCK_COUNT = "libre_unlock_count"
        const val KEY_MAC = "libre_ble_address"
    }
}
