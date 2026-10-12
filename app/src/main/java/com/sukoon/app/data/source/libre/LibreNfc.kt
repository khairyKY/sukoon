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
    /** A dropped link is reopened after this pause, if the sensor is still in the field. */
    private const val RECONNECT_MS = 100L
    /** After Activate: read the memory back up to this many times, this far apart, until it says it started. */
    private const val START_CHECKS = 5
    private const val START_WAIT_MS = 400L
    private const val MINUTES_PER_DAY = 1440

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
        val info: Libre2.SensorInfo? get() = fram?.let(Libre2::sensorInfo)

        /**
         * What a read-only check shows; null when the memory can't be trusted (not a Libre 2 EU, it
         * didn't decrypt, or a state byte no sensor writes).
         */
        fun check(): Check? {
            val f = fram?.takeIf { supported } ?: return null
            val info = Libre2.sensorInfo(f)
            val stage = when (info.state) {
                Libre2.State.NOT_STARTED -> Stage.NOT_STARTED
                Libre2.State.STARTING -> Stage.WARMING_UP
                Libre2.State.READY -> Stage.RUNNING
                Libre2.State.EXPIRED, Libre2.State.SHUTDOWN, Libre2.State.FAILURE -> Stage.ENDED
                Libre2.State.UNKNOWN -> return null
            }
            return Check(
                serial = serial,
                stage = stage,
                day = info.ageMinutes / MINUTES_PER_DAY + 1,
                days = (info.lifetimeMinutes + 60) / MINUTES_PER_DAY, // sensorInfo keeps a 60-min margin off the end
                minutesToReadings = if (stage == Stage.WARMING_UP) (LibreBleSource.WARMUP_MINUTES - info.ageMinutes).coerceAtLeast(0) else null,
                // Only a running sensor's memory holds glucose: warming up or ended, it is not to be read as a value.
                mgDl = if (stage == Stage.RUNNING) Libre2.parseFram(FactoryCalibration.fromFram(f), f).maxByOrNull { it.minute }?.mgDl else null,
            )
        }
    }

    /** Where a sensor is in its life, as the check shows it. */
    enum class Stage { NOT_STARTED, WARMING_UP, RUNNING, ENDED }

    /** A read-only check's findings, for the Sensor card to lay out. */
    data class Check(
        val serial: String,
        val stage: Stage,
        /** The day of its life, from 1, and how many days it lasts. */
        val day: Int,
        val days: Int,
        /** Warming up: minutes until the first readings. */
        val minutesToReadings: Int?,
        /** Running: the value its memory reads now. */
        val mgDl: Int?,
    )

    /**
     * [retry]: the link or one answer failed, not the sensor's verdict; holding the phone still and
     * reading again can fix it, so the Sensor card keeps listening instead of giving up.
     */
    class NfcException(message: String, val retry: Boolean = false) : IOException(message)

    /**
     * A failure worth reading again for (the phone lost the sensor for a moment: TagLost, a tag
     * Android has since found again, a closed handle), not a final answer from the sensor or Sukoon.
     */
    fun retryable(e: Throwable): Boolean = when (e) {
        is NfcException -> e.retry
        is IOException, is SecurityException, is IllegalStateException -> true
        else -> false
    }

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
     * been started (and reads it back to prove it did; one already warming up or running is just
     * paired); with [pair] (implied by [start]), then enables BLE streaming for this app (taking the
     * sensor's stream from any other reader). Blocking — call from the NFC reader callback thread.
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
                            throw NfcException("Sensor answered with an error (${response.hex()})", retry = true)
                        }
                        return response.copyOfRange(1, response.size)
                    } catch (e: NfcException) {
                        last = e
                    } catch (e: IOException) {
                        // A dropped link closes the handle: reopen it if the sensor is still there, else stop
                        // here and let Android find the tag again (the card keeps listening for it).
                        last = e
                        val back = runCatching {
                            it.close()
                            Thread.sleep(RECONNECT_MS)
                            it.connect()
                        }.isSuccess
                        if (!back) throw e
                    }
                }
                throw last ?: NfcException("No response", retry = true)
            }

            val uid = tag.id
            // Its last two bytes key the memory's encryption and can differ from one tap to the next.
            var patchInfo = send(CMD_PATCH_INFO)
            if (patchInfo.size < 6) throw NfcException("Unexpected patch info (${patchInfo.hex()})", retry = true)

            /** Raw FRAM, and the decrypted copy when its CRCs validate. */
            fun readFram(): Pair<ByteArray, ByteArray?> {
                val raw = ByteArray(FRAM_BLOCKS * 8)
                for (first in 0 until FRAM_BLOCKS step BLOCKS_PER_READ) {
                    val count = minOf(BLOCKS_PER_READ, FRAM_BLOCKS - first)
                    val blocks = send(readMultipleBlocks(first, count))
                    if (blocks.size < count * 8) throw NfcException("Short read at block $first", retry = true)
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
                // Already warming up or running (another app started it, or a tap that started it was cut
                // short before pairing): nothing to start, it is just paired below.
                if (state == Libre2.State.NOT_STARTED) {
                    val answer = try {
                        send(CMD_PATCH_INFO + Libre2.activationParameters(uid))
                    } catch (e: NfcException) {
                        throw NfcException("The sensor refused to start: ${e.message}")
                    }
                    Log.i(TAG, "Activate answered ${answer.hex("")}")
                    // The answer isn't the proof: the sensor's own memory saying it has started is.
                    var started = false
                    for (attempt in 0 until START_CHECKS) {
                        if (attempt > 0) Thread.sleep(START_WAIT_MS)
                        patchInfo = send(CMD_PATCH_INFO)
                        val again = readFram()
                        rawFram = again.first
                        fram = again.second
                        val now = fram?.let { f -> Libre2.sensorInfo(f).state }
                        Log.i(TAG, "After activate, check ${attempt + 1}: patch ${patchInfo.hex("")}, state $now")
                        if (now != null && now != Libre2.State.NOT_STARTED) {
                            started = true
                            break
                        }
                    }
                    if (!started) throw NfcException("The sensor didn't start (it answered ${answer.hex()}). Keep the phone flat on it and try again.")
                }
            }

            val read = SensorRead(uid, patchInfo, rawFram, fram)
            if (!pair && !start) return read

            if (!read.supported) throw NfcException("Only Libre 2 EU sensors can stream to Sukoon (patch info ${patchInfo.hex()})")
            if (fram == null) throw NfcException("Sensor memory didn't decrypt cleanly, so Sukoon won't take it over")
            problemWith(Libre2.sensorInfo(fram).state)?.let { throw NfcException(it) }
            val mac = try {
                send(CMD_PATCH_INFO + Libre2.enableStreamingParameters(uid, patchInfo))
            } catch (e: NfcException) {
                // The sensor said no (a lost link is retried instead): once started, say it is running.
                throw NfcException(if (start) STARTED_NOT_PAIRED else "Enable streaming failed: ${e.message}")
            }
            if (mac.size != 6) throw NfcException(if (start) STARTED_NOT_PAIRED else "Enable streaming failed (${mac.hex()})")
            // The sensor reports its MAC least-significant byte first.
            return read.copy(bleMac = mac.reversedArray().hex(":"))
        }
    }
}

fun ByteArray.hex(separator: String = " "): String = joinToString(separator) { String.format(Locale.US, "%02X", it) }
