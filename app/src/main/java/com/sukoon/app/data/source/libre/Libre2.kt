package com.sukoon.app.data.source.libre

import kotlin.math.ln
import kotlin.math.pow

/**
 * FreeStyle Libre 2 (EU) sensor protocol: FRAM + BLE decryption, the BLE streaming-unlock
 * payload, the NFC enable-streaming parameters, and factory-calibrated raw -> mg/dL.
 *
 * Ported from GlucoseDirect's SensorUtility.swift / FactoryCalibration.swift / LibreNFC.swift
 * (MIT, (c) 2023 Reimar Metzen), whose Libre 2 code descends from DiaBLE (MIT, Guido Soranzio)
 * and LibreTools (MIT, Ivan Valkou) — see THIRD_PARTY_NOTICES.md.
 *
 * Conventions: `uid` is the 8-byte sensor UID least-significant byte first, i.e. Android's
 * `Tag.getId()` as-is (ending ...07 E0). All 16-bit math is masked explicitly — Kotlin Bytes are
 * signed and Ints don't wrap at 16 bits, the two classic ways a port of this code goes wrong.
 *
 * Correctness gates, because wrong crypto would otherwise yield plausible-but-wrong glucose:
 * decrypted FRAM must pass its three section CRCs ([framCrcValid]) and every BLE packet its own
 * CRC ([decryptBle] throws). The raw -> mg/dL step has no CRC — it's validated against
 * finger-pricks on a real sensor before anything is trusted.
 */
object Libre2 {

    private val KEYS = intArrayOf(0xA0C5, 0x6860, 0x0000, 0x14C6)

    private const val SUB_ACTIVATE = 0x1B
    private const val SUB_ENABLE_STREAMING = 0x1E

    /** The constant unlock code / enable time GlucoseDirect uses for both NFC enable and BLE login. */
    const val UNLOCK_CODE = 42L

    private fun u(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    /** Swift `UInt16(high, low)`. */
    private fun word(high: Int, low: Int) = ((high and 0xFF) shl 8) or (low and 0xFF)

    // --- core cipher ---------------------------------------------------------------------------

    internal fun processCrypto(input: IntArray): IntArray {
        fun op(value: Int): Int {
            var res = value ushr 2
            if (value and 1 != 0) res = res xor KEYS[1]
            if (value and 2 != 0) res = res xor KEYS[0]
            return res and 0xFFFF
        }
        val r0 = op(input[0]) xor input[3]
        val r1 = op(r0) xor input[2]
        val r2 = op(r1) xor input[1]
        val r3 = op(r2) xor input[0]
        val r4 = op(r3)
        val r5 = op(r4 xor r0)
        val r6 = op(r5 xor r1)
        val r7 = op(r6 xor r2)
        return intArrayOf(r3 xor r7, r2 xor r6, r1 xor r5, r0 xor r4).also { out -> out.indices.forEach { out[it] = out[it] and 0xFFFF } }
    }

    private fun prepareVariables(uid: ByteArray, x: Int, y: Int) = intArrayOf(
        (word(u(uid, 5), u(uid, 4)) + x + y) and 0xFFFF,
        (word(u(uid, 3), u(uid, 2)) + KEYS[2]) and 0xFFFF,
        (word(u(uid, 1), u(uid, 0)) + x * 2) and 0xFFFF,
        0x241A xor KEYS[3],
    )

    private fun prepareVariables(uid: ByteArray, i1: Int, i2: Int, i3: Int, i4: Int) = intArrayOf(
        (word(u(uid, 5), u(uid, 4)) + i1) and 0xFFFF,
        (word(u(uid, 3), u(uid, 2)) + i2) and 0xFFFF,
        (word(u(uid, 1), u(uid, 0)) + i3 + KEYS[2]) and 0xFFFF,
        (i4 + KEYS[3]) and 0xFFFF,
    )

    internal fun usefulFunction(uid: ByteArray, x: Int, y: Int): ByteArray {
        val blockKey = processCrypto(prepareVariables(uid, x, y))
        val r1 = blockKey[0] xor 0x4163
        val r2 = blockKey[1] xor 0x4344
        return byteArrayOf(r1.toByte(), (r1 ushr 8).toByte(), r2.toByte(), (r2 ushr 8).toByte())
    }

    private fun wordsLe(words: IntArray) = ByteArray(words.size * 2) { i ->
        (if (i % 2 == 0) words[i / 2] else words[i / 2] ushr 8).toByte()
    }

    // --- CRC -------------------------------------------------------------------------------------

    /**
     * GlucoseDirect's `crc16`: the CRC-16/MCRF4XX register (reflected poly 0x8408, init 0xFFFF),
     * bit-reversed, then byte-swapped. Libre stores its section/packet CRCs so that this equals
     * `stored[0] << 8 | stored[1]`.
     */
    internal fun crc16(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0xFFFF
        for (i in from until to) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0x8408 else crc ushr 1 }
        }
        val reversed = Integer.reverse(crc) ushr 16
        return ((reversed and 0xFF) shl 8) or (reversed ushr 8)
    }

    private fun crcMatches(data: ByteArray, from: Int, to: Int, storedAt: Int) =
        crc16(data, from, to) == word(u(data, storedAt), u(data, storedAt + 1))

    /** Header (blocks 0-2), body (3-39) and footer (40-42) each carry a CRC over the rest of the section. */
    fun framCrcValid(fram: ByteArray): Boolean = fram.size >= 344 &&
        crcMatches(fram, 2, 24, 0) && crcMatches(fram, 26, 320, 24) && crcMatches(fram, 322, 344, 320)

    // --- FRAM ------------------------------------------------------------------------------------

    /** Decrypts a 344-byte Libre 2 FRAM read over NFC. Returns null for wrong input sizes or Libre 1 (unencrypted). */
    fun decryptFram(uid: ByteArray, patchInfo: ByteArray, fram: ByteArray): ByteArray? {
        if (uid.size != 8 || patchInfo.size < 6 || fram.size != 344 || isLibre1(patchInfo)) return null
        val result = ByteArray(344)
        for (i in 0 until 43) {
            val s1 = if (u(patchInfo, 0) == 0xE5) {
                val y = if (i < 3 || i >= 40) 0xCADC else word(u(patchInfo, 5), u(patchInfo, 4))
                (word(u(uid, 5), u(uid, 4)) + y + i) and 0xFFFF
            } else {
                (word(u(uid, 5), u(uid, 4)) + (word(u(patchInfo, 5), u(patchInfo, 4)) xor 0x44) + i) and 0xFFFF
            }
            val s2 = (word(u(uid, 3), u(uid, 2)) + KEYS[2]) and 0xFFFF
            val s3 = (word(u(uid, 1), u(uid, 0)) + (i shl 1)) and 0xFFFF
            val s4 = 0x241A xor KEYS[3]
            val key = wordsLe(processCrypto(intArrayOf(s1, s2, s3, s4)))
            for (k in 0 until 8) result[i * 8 + k] = (fram[i * 8 + k].toInt() xor key[k].toInt()).toByte()
        }
        return result
    }

    // --- BLE -------------------------------------------------------------------------------------

    class BleDecryptException : Exception("BLE packet failed its CRC — wrong sensor/keys or a corrupted packet")

    /** Decrypts one 46-byte BLE notification (the 20 + 18 + 8 byte chunks joined) into 44 bytes; throws on CRC mismatch. */
    fun decryptBle(uid: ByteArray, data: ByteArray): ByteArray {
        require(data.size == 46) { "BLE packet must be 46 bytes, was ${data.size}" }
        val d = usefulFunction(uid, SUB_ACTIVATE, 0x1B6A)
        val x = (word(u(d, 1), u(d, 0)) xor word(u(d, 3), u(d, 2))) or 0x63
        val y = word(u(data, 1), u(data, 0)) xor 0x63

        val key = ByteArray(64)
        var block = processCrypto(prepareVariables(uid, x, y))
        for (r in 0 until 8) {
            wordsLe(block).copyInto(key, r * 8)
            block = processCrypto(block)
        }
        val result = ByteArray(44) { i -> (data[i + 2].toInt() xor key[i].toInt()).toByte() }
        if (!crcMatches(result, 0, 42, 42)) throw BleDecryptException()
        return result
    }

    /** 12-byte login payload written to characteristic F001 on every BLE connection; [unlockCount] must increase each time. */
    fun streamingUnlockPayload(uid: ByteArray, patchInfo: ByteArray, enableTime: Long, unlockCount: Int): ByteArray {
        val time = (enableTime + unlockCount) and 0xFFFFFFFFL
        val b = ByteArray(4) { (time ushr (8 * it)).toByte() }

        val ad = usefulFunction(uid, SUB_ACTIVATE, 0x1B6A)
        val ed = usefulFunction(uid, SUB_ENABLE_STREAMING, (enableTime and 0xFFFF).toInt() xor word(u(patchInfo, 5), u(patchInfo, 4)))

        val t11 = word(u(ed, 1), u(ed, 0)) xor word(u(b, 3), u(b, 2))
        val t12 = word(u(ad, 1), u(ad, 0))
        val t13 = word(u(ed, 3), u(ed, 2)) xor word(u(b, 1), u(b, 0))
        val t14 = word(u(ad, 3), u(ad, 2))
        val t2 = wordsLe(processCrypto(prepareVariables(uid, t11, t12, t13, t14)))

        // Swift applies .byteSwapped to crc16's (already byte-swapped) result: undo it.
        fun crc(vararg bytes: Byte): Int = crc16(bytes).let { ((it and 0xFF) shl 8) or (it ushr 8) }
        val salt = byteArrayOf(0xC1.toByte(), 0xC4.toByte(), 0xC3.toByte(), 0xC0.toByte(), 0xD4.toByte(), 0xE1.toByte(), 0xE7.toByte(), 0xBA.toByte())
        val t31 = crc(*salt, t2[0], t2[1])
        val t32 = crc(t2[2], t2[3], t2[4], t2[5], t2[6], t2[7])
        val t33 = crc(ad[0], ad[1], ad[2], ad[3], ed[0], ed[1])
        val t34 = crc(ed[2], ed[3], b[0], b[1], b[2], b[3])

        val t4 = wordsLe(processCrypto(prepareVariables(uid, t31, t32, t33, t34)))
        return b + t4
    }

    /**
     * Parameters after `02 A1 07` for the NFC Enable-Streaming command (sub-command 0x1E). This is the
     * one command that changes sensor state: the sensor re-keys BLE to this app (taking it from any
     * other reader) and answers with its 6-byte BLE MAC.
     */
    fun enableStreamingParameters(uid: ByteArray, patchInfo: ByteArray, unlockCode: Long = UNLOCK_CODE): ByteArray {
        val b = ByteArray(4) { (unlockCode ushr (8 * it)).toByte() }
        val y = word(u(patchInfo, 5), u(patchInfo, 4)) xor word(u(b, 1), u(b, 0))
        return byteArrayOf(SUB_ENABLE_STREAMING.toByte()) + b + usefulFunction(uid, SUB_ENABLE_STREAMING, y)
    }

    // --- parsing ---------------------------------------------------------------------------------

    /** Little-endian bit reader over the sensor's packed records (port of GlucoseDirect's readBits). */
    internal fun readBits(buffer: ByteArray, byteOffset: Int, bitOffset: Int, bitCount: Int): Int {
        var res = 0
        for (i in 0 until bitCount) {
            val total = byteOffset * 8 + bitOffset + i
            val byte = total / 8
            if (byte in buffer.indices && (u(buffer, byte) ushr (total % 8)) and 1 == 1) res = res or (1 shl i)
        }
        return res
    }

    /** One raw record → mg/dL via factory calibration, or null for an error/implausible record. */
    private fun record(calibration: FactoryCalibration, raw: Int, rawTemperature: Int, rawTemperatureAdjustment: Int): Int? {
        if (raw == 0) return null // a zero raw value carries an error code instead of a reading
        return calibration.calibrate(raw.toDouble(), rawTemperature.toDouble(), rawTemperatureAdjustment.toDouble())
    }

    /** A decoded reading: minutes since sensor start (deterministic, so re-reads dedupe) and mg/dL. */
    data class Point(val minute: Int, val mgDl: Int)

    /** Decrypted BLE packet → sensor age (minutes) and up to 10 readings (7 recent + 3 fifteen-minute history). */
    fun parseBle(calibration: FactoryCalibration, data: ByteArray): Pair<Int, List<Point>> {
        val age = word(u(data, 41), u(data, 40))
        val points = (0 until 10).mapNotNull { i ->
            val raw = readBits(data, i * 4, 0, 0xE)
            val rawTemperature = readBits(data, i * 4, 0xE, 0xC) shl 2
            var adjustment = readBits(data, i * 4, 0x1A, 0x5) shl 2
            if (readBits(data, i * 4, 0x1F, 0x1) != 0) adjustment = -adjustment
            val minute = if (i < 7) age - BLE_TREND_OFFSETS[i] else ((age - 2) / 15) * 15 - 15 * (i - 7)
            record(calibration, raw, rawTemperature, adjustment)?.let { Point(minute, it) }
        }
        return age to points.filter { it.minute >= 0 }.sortedBy { it.minute }
    }

    private val BLE_TREND_OFFSETS = intArrayOf(0, 2, 4, 6, 7, 12, 15)

    /** Decrypted FRAM → 16 one-minute trend + 32 fifteen-minute history readings (the last ~8 h). */
    fun parseFram(calibration: FactoryCalibration, fram: ByteArray): List<Point> {
        val info = sensorInfo(fram)
        val age = info.ageMinutes
        fun at(offset: Int): Int? {
            val raw = readBits(fram, offset, 0, 0xE)
            val rawTemperature = readBits(fram, offset, 0x1A, 0xC) shl 2
            var adjustment = readBits(fram, offset, 0x26, 0x9) shl 2
            if (readBits(fram, offset, 0x2F, 0x1) != 0) adjustment = -adjustment
            return record(calibration, raw, rawTemperature, adjustment)
        }
        val trendIndex = u(fram, 26)
        val trend = (0..15).mapNotNull { i ->
            val j = (trendIndex - 1 - i).mod(16)
            at(28 + j * 6)?.let { Point(age - i, it) }
        }
        val historyIndex = u(fram, 27)
        val delay = (age - 3) % 15 + 3
        val oldestTrend = trend.minOfOrNull { it.minute } ?: age
        val history = (0..31).mapNotNull { i ->
            val j = (historyIndex - 1 - i).mod(32)
            val minute = age - delay - i * 15
            if (minute < 0 || minute >= oldestTrend) null else at(124 + j * 6)?.let { Point(minute, it) }
        }
        return (history + trend).sortedBy { it.minute }
    }

    // --- sensor info -----------------------------------------------------------------------------

    fun isLibre1(patchInfo: ByteArray) = patchInfo.isNotEmpty() && (u(patchInfo, 0) == 0xDF || u(patchInfo, 0) == 0xA2)

    /** GlucoseDirect's Libre 2 EU set: 9D (Libre 2), C5/C6/7F (later EU Libre 2 / 2 Plus firmware). */
    fun isLibre2Eu(patchInfo: ByteArray) = patchInfo.size >= 6 && u(patchInfo, 0) in setOf(0x9D, 0xC5, 0xC6, 0x7F)

    enum class State { NOT_STARTED, STARTING, READY, EXPIRED, SHUTDOWN, FAILURE, UNKNOWN }

    data class SensorInfo(val state: State, val ageMinutes: Int, val lifetimeMinutes: Int)

    /** From decrypted FRAM: state (byte 4), age (316-317), lifetime (326-327, minus GlucoseDirect's 60-min safety margin). */
    fun sensorInfo(fram: ByteArray) = SensorInfo(
        state = when (u(fram, 4)) {
            1 -> State.NOT_STARTED
            2 -> State.STARTING
            3 -> State.READY
            4 -> State.EXPIRED
            5 -> State.SHUTDOWN
            6 -> State.FAILURE
            else -> State.UNKNOWN
        },
        ageMinutes = word(u(fram, 317), u(fram, 316)),
        lifetimeMinutes = word(u(fram, 327), u(fram, 326)) - 60,
    )

    /** Printed serial (e.g. "3MH0…") — the family digit + 10 base-32 characters from the UID; the BLE name is "abbott" + this. */
    fun serial(uid: ByteArray, patchInfo: ByteArray): String {
        val alphabet = "0123456789ACDEFGHJKLMNPQRTUVWXYZ"
        val b = IntArray(6) { u(uid, 5 - it) } // UID MSB-first, skipping the E0 07 manufacturer prefix
        val fives = intArrayOf(
            b[0] ushr 3, (b[0] shl 2) + (b[1] ushr 6), b[1] ushr 1, (b[1] shl 4) + (b[2] ushr 4),
            (b[2] shl 1) + (b[3] ushr 7), b[3] ushr 2, (b[3] shl 3) + (b[4] ushr 5), b[4],
            b[5] ushr 3, b[5] shl 2,
        )
        return (u(patchInfo, 2) ushr 4).toString() + fives.joinToString("") { alphabet[it and 0x1F].toString() }
    }
}

/** Per-sensor factory calibration parameters, read from decrypted FRAM. */
data class FactoryCalibration(val i1: Int, val i2: Int, val i3: Double, val i4: Double, val i5: Double, val i6: Double) {

    val valid: Boolean get() = i2 in 1..CALIBRATION_T1.size && i4 != i3 && i6 > 0

    /** Raw sensor value + thermistor readings → mg/dL (temperature-compensated), or null if out of model. */
    fun calibrate(rawValue: Double, rawTemperature: Double, rawTemperatureAdjustment: Double): Int? {
        if (!valid) return null
        val r = rawTemperature * 72_500.0 / (rawTemperatureAdjustment + i6) - 1000.0
        if (r <= 0) return null
        val logR = ln(r)
        val d = logR.pow(3) * 0.00000005283566 + logR.pow(2) * 0.0000007061775 + logR * 0.0001964561 + 0.0009180023
        val temperature = 1 / d - 273.15
        val g = 65.0 * (rawValue - i3) / (i4 - i3) * 1.045.pow(32.5 - temperature)
        val mgDl = (g - CALIBRATION_T1[i2 - 1]) / CALIBRATION_T2[i2 - 1]
        return if (mgDl.isFinite()) Math.round(mgDl).toInt() else null
    }

    companion object {
        /** Libre 1/2 layout: i1/i2 in the header (byte 2), the rest in the footer at 0x150. */
        fun fromFram(fram: ByteArray): FactoryCalibration {
            val r = Libre2::readBits
            var i3 = r(fram, 0x150, 0, 8).toDouble()
            if (r(fram, 0x150, 0x21, 1) != 0) i3 = -i3
            return FactoryCalibration(
                i1 = r(fram, 2, 0, 3),
                i2 = r(fram, 2, 3, 0xA),
                i3 = i3,
                i4 = r(fram, 0x150, 8, 0xE).toDouble(),
                i5 = (r(fram, 0x150, 0x28, 0xC) shl 2).toDouble(),
                i6 = (r(fram, 0x150, 0x34, 0xC) shl 2).toDouble(),
            )
        }
    }
}
