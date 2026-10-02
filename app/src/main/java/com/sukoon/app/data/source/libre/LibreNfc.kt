package com.sukoon.app.data.source.libre

import android.nfc.Tag
import android.nfc.tech.NfcV
import java.io.IOException
import java.util.Locale

/**
 * NFC (ISO 15693 / Android NfcV) access to a Libre sensor.
 *
 * Frames follow Android's NfcV contract — flags + command + parameters, no CRC (the controller
 * appends it). Unaddressed (flags 0x02, high data rate). Libre custom commands are `02 A1 07`
 * (+ sub-command): bare, it's the read-only Get Patch Info; with sub-command 0x1E it's Enable
 * Streaming — the one state-changing command, sent only by [readAndPair] and only after the FRAM
 * decrypted with valid CRCs (a sensor we can't decode must never be taken over).
 */
object LibreNfc {

    private const val FLAGS: Byte = 0x02
    val CMD_PATCH_INFO = byteArrayOf(FLAGS, 0xA1.toByte(), 0x07)

    private const val FRAM_BLOCKS = 43
    private const val BLOCKS_PER_READ = 3 // some phones truncate longer multi-block responses
    private const val RETRIES = 3

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
     * Reads patch info + FRAM and decrypts it; with [pair], then enables BLE streaming for this app
     * (taking the sensor's stream from any other reader). Blocking — call from the NFC reader
     * callback thread.
     */
    fun readAndPair(tag: Tag, pair: Boolean): SensorRead {
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

            val rawFram = ByteArray(FRAM_BLOCKS * 8)
            for (first in 0 until FRAM_BLOCKS step BLOCKS_PER_READ) {
                val count = minOf(BLOCKS_PER_READ, FRAM_BLOCKS - first)
                val blocks = send(readMultipleBlocks(first, count))
                if (blocks.size < count * 8) throw NfcException("Short read at block $first")
                blocks.copyInto(rawFram, first * 8, 0, count * 8)
            }

            val decrypted = if (Libre2.isLibre1(patchInfo)) rawFram else Libre2.decryptFram(uid, patchInfo, rawFram)
            val fram = decrypted?.takeIf { f -> Libre2.framCrcValid(f) }
            val read = SensorRead(uid, patchInfo, rawFram, fram)
            if (!pair) return read

            if (!read.supported) throw NfcException("Only Libre 2 EU sensors can stream to Sukoon (patch info ${patchInfo.hex()})")
            if (fram == null) throw NfcException("Sensor memory didn't decrypt cleanly, so Sukoon won't take it over")
            val mac = send(CMD_PATCH_INFO + Libre2.enableStreamingParameters(uid, patchInfo))
            if (mac.size != 6) throw NfcException("Enable streaming failed (${mac.hex()})")
            // The sensor reports its MAC least-significant byte first.
            return read.copy(bleMac = mac.reversedArray().hex(":"))
        }
    }
}

fun ByteArray.hex(separator: String = " "): String = joinToString(separator) { String.format(Locale.US, "%02X", it) }
