package com.sukoon.app.data.source.libre

import android.nfc.Tag
import android.nfc.tech.NfcV
import android.util.Log
import java.io.IOException
import java.util.Locale

/**
 * NFC (ISO 15693 / Android NfcV) access to a Libre sensor.
 *
 * Frames follow Android's NfcV contract — flags + command + parameters, no CRC (the controller
 * appends it). Unaddressed (flags 0x02, high data rate). Libre custom commands are `02 A1 07`
 * (+ sub-command): bare, it's the read-only Get Patch Info; with sub-command 0x1B it's Activate
 * (starts a new sensor) and with 0x1E Enable Streaming. Both change the sensor, so [readAndPair]
 * sends them only after the FRAM decrypted with valid CRCs (a sensor we can't decode must never be
 * started or taken over), and Activate only to a sensor whose memory says it has never started.
 */
object LibreNfc {

    private const val TAG = "LibreNfc"
    private const val FLAGS: Byte = 0x02
    val CMD_PATCH_INFO = byteArrayOf(FLAGS, 0xA1.toByte(), 0x07)

    private const val FRAM_BLOCKS = 43
    private const val BLOCKS_PER_READ = 3 // some phones truncate longer multi-block responses
    private const val RETRIES = 3
    /** After Activate: read the memory back up to this many times, this far apart, until it says it started. */
    private const val START_CHECKS = 5
    private const val START_WAIT_MS = 400L

    const val ALREADY_STARTED = "This sensor has already been started, so there's nothing to start. Tap Pair again to connect to it."
    const val STARTED_NOT_PAIRED = "The sensor has started and is warming up, but pairing didn't finish. Tap Pair again while it warms up."

    fun readMultipleBlocks(first: Int, count: Int) = byteArrayOf(FLAGS, 0x23, first.toByte(), (count - 1).toByte())

    /** Everything one tap yields. [fram] is decrypted (Libre 2) and only set when its CRCs validate. */
    data class SensorRead(
        val uid: ByteArray,
        val patchInfo: ByteArray,
        val rawFram: ByteArray,
        val fram: ByteArray?,
        /** The sensor's BLE MAC from Enable Streaming, when pairing was requested and succeeded. */
        val bleMac: String? = null,
    ) {
        val supported: Boolean get() = Libre2.isLibre2Eu(patchInfo)
        val serial: String get() = Libre2.serial(uid, patchInfo)
    }

    class NfcException(message: String) : IOException(message)

    /**
     * Why a sensor in [state] can't be taken over (null = it can). A sensor nobody has scanned to
     * start hasn't begun warm-up and doesn't talk Bluetooth, and its memory holds noise, not glucose.
     */
    fun problemWith(state: Libre2.State): String? = when (state) {
        Libre2.State.NOT_STARTED -> "This sensor hasn't been started yet. Tap Start a new sensor to start it."
        Libre2.State.EXPIRED, Libre2.State.SHUTDOWN, Libre2.State.FAILURE -> "This sensor has ended or failed. Put a new one on."
        else -> null
    }

    /**
     * Reads patch info + FRAM and decrypts it; with [start], first starts a sensor that has never
     * been started (and reads it back to prove it did); with [pair] (implied by [start]), then
     * enables BLE streaming for this app (taking the sensor's stream from any other reader).
     * Blocking — call from the NFC reader callback thread.
     */
    fun readAndPair(tag: Tag, pair: Boolean, start: Boolean = false): SensorRead {
        val nfcV = NfcV.get(tag) ?: throw NfcException("Not an ISO 15693 tag — is this a Libre sensor?")
        nfcV.use {
            it.connect()
            fun send(command: ByteArray): ByteArray {
                var last: IOException? = null
                repeat(RETRIES) { _ ->
                    try {
                        val response = it.transceive(command)
                        if (response.isEmpty() || response[0].toInt() and 0x01 != 0) {
                            throw NfcException("Sensor answered with an error (${response.hex()})")
                        }
                        return response.copyOfRange(1, response.size)
                    } catch (e: IOException) {
                        last = e
                    }
                }
                throw last ?: NfcException("No response")
            }

            val uid = tag.id
            val patchInfo = send(CMD_PATCH_INFO)
            if (patchInfo.size < 6) throw NfcException("Unexpected patch info (${patchInfo.hex()})")

            /** Raw FRAM, and the decrypted copy when its CRCs validate. */
            fun readFram(): Pair<ByteArray, ByteArray?> {
                val raw = ByteArray(FRAM_BLOCKS * 8)
                for (first in 0 until FRAM_BLOCKS step BLOCKS_PER_READ) {
                    val count = minOf(BLOCKS_PER_READ, FRAM_BLOCKS - first)
                    val blocks = send(readMultipleBlocks(first, count))
                    if (blocks.size < count * 8) throw NfcException("Short read at block $first")
                    blocks.copyInto(raw, first * 8, 0, count * 8)
                }
                val decrypted = if (Libre2.isLibre1(patchInfo)) raw else Libre2.decryptFram(uid, patchInfo, raw)
                return raw to decrypted?.takeIf { f -> Libre2.framCrcValid(f) }
            }

            var (rawFram, fram) = readFram()

            if (start) {
                if (!Libre2.isLibre2Eu(patchInfo)) throw NfcException("Sukoon can only start Libre 2 EU sensors (patch info ${patchInfo.hex()})")
                val state = fram?.let { f -> Libre2.sensorInfo(f).state }
                    ?: throw NfcException("Sensor memory didn't decrypt cleanly, so Sukoon won't start it")
                if (state != Libre2.State.NOT_STARTED) throw NfcException(ALREADY_STARTED)
                val answer = send(CMD_PATCH_INFO + Libre2.activationParameters(uid))
                Log.i(TAG, "Activate answered ${answer.hex("")}")
                // The answer isn't the proof: the sensor's own memory saying it has started is.
                var started = false
                for (attempt in 0 until START_CHECKS) {
                    if (attempt > 0) Thread.sleep(START_WAIT_MS)
                    val again = readFram()
                    rawFram = again.first
                    fram = again.second
                    val now = fram?.let { f -> Libre2.sensorInfo(f).state }
                    Log.i(TAG, "After activate, check ${attempt + 1}: state $now")
                    if (now != null && now != Libre2.State.NOT_STARTED) {
                        started = true
                        break
                    }
                }
                if (!started) throw NfcException("The sensor didn't start (it answered ${answer.hex()}). Keep the phone flat on it and try again.")
            }

            val read = SensorRead(uid, patchInfo, rawFram, fram)
            if (!pair && !start) return read

            if (!read.supported) throw NfcException("Only Libre 2 EU sensors can stream to Sukoon (patch info ${patchInfo.hex()})")
            if (fram == null) throw NfcException("Sensor memory didn't decrypt cleanly, so Sukoon won't take it over")
            problemWith(Libre2.sensorInfo(fram).state)?.let { throw NfcException(it) }
            // Once started, a failure here must say so: the sensor's clock is already running.
            val mac = try {
                send(CMD_PATCH_INFO + Libre2.enableStreamingParameters(uid, patchInfo))
            } catch (e: IOException) {
                if (start) throw NfcException(STARTED_NOT_PAIRED) else throw e
            }
            if (mac.size != 6) throw NfcException(if (start) STARTED_NOT_PAIRED else "Enable streaming failed (${mac.hex()})")
            // The sensor reports its MAC least-significant byte first.
            return read.copy(bleMac = mac.reversedArray().hex(":"))
        }
    }
}

fun ByteArray.hex(separator: String = " "): String = joinToString(separator) { String.format(Locale.US, "%02X", it) }
