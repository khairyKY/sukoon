package com.sukoon.app.data.source.libre

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibreNfcTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `frames follow the NfcV contract - flags, command, params, no CRC`() {
        assertArrayEquals(bytes(0x02, 0x23, 0x03, 0x02), LibreNfc.readMultipleBlocks(first = 3, count = 3)) // count - 1 on the wire
        // Bare patch info is read-only; anything after 07 would be a Libre 2 sub-command.
        assertArrayEquals(bytes(0x02, 0xA1, 0x07), LibreNfc.CMD_PATCH_INFO)
    }

    @Test
    fun `enable streaming is patch-info + sub-command 1E + unlock code + 4-byte proof`() {
        val uid = bytes(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x07, 0xE0)
        val params = Libre2.enableStreamingParameters(uid, bytes(0x9D, 0x08, 0x30, 0x01, 0x76, 0x25))
        assertEquals(9, params.size)
        assertEquals(0x1E.toByte(), params[0])
        assertArrayEquals(bytes(42, 0, 0, 0), params.copyOfRange(1, 5)) // unlock code 42, little-endian
    }

    @Test
    fun `a sensor nobody started is refused, a warming or running one is taken`() {
        assertEquals(com.sukoon.app.R.string.nfc_not_started, LibreNfc.problemWith(Libre2.State.NOT_STARTED))
        listOf(Libre2.State.EXPIRED, Libre2.State.SHUTDOWN, Libre2.State.FAILURE).forEach { assertTrue(LibreNfc.problemWith(it) != null) }
        listOf(Libre2.State.STARTING, Libre2.State.READY, Libre2.State.UNKNOWN).forEach { assertEquals(null, LibreNfc.problemWith(it)) }
    }

    @Test
    fun `activate is patch-info + sub-command 1B + the 4-byte proof the BLE key is built from`() {
        val uid = bytes(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x07, 0xE0)
        val params = Libre2.activationParameters(uid)
        assertEquals(5, params.size)
        assertEquals(0x1B.toByte(), params[0])
        // usefulFunction(uid, 0x1B, 0x1B6A) is what decryptBle keys on; Libre2Test checks it against a real packet.
        assertArrayEquals(Libre2.usefulFunction(uid, 0x1B, 0x1B6A), params.copyOfRange(1, 5))
        assertArrayEquals(bytes(0x02, 0xA1, 0x07, 0x1B), LibreNfc.CMD_PATCH_INFO + params.copyOfRange(0, 1))
    }

    @Test
    fun `a lost link is read again, the sensor's or Sukoon's refusal is final`() {
        assertTrue(LibreNfc.retryable(java.io.IOException("Tag was lost.")))
        assertTrue(LibreNfc.retryable(SecurityException("Tag is out of date")))
        assertFalse(LibreNfc.retryable(LibreNfc.NfcException(com.sukoon.app.R.string.nfc_sensor_error, "Sensor answered with an error (01 0F)"))) // still there: Android won't hand it back
        assertFalse(LibreNfc.retryable(LibreNfc.NfcException(com.sukoon.app.R.string.nfc_ended)))
        assertFalse(LibreNfc.retryable(LibreNfc.NfcException(com.sukoon.app.R.string.nfc_started_not_paired)))
    }

    /** Decrypted memory with just the fields the check reads: state (byte 4), age (316-317), lifetime (326-327). */
    private fun read(state: Int, ageMinutes: Int, patch: Int = 0x7F, lifetime: Int = 22320) = LibreNfc.SensorRead(
        uid = bytes(0x62, 0x4F, 0x50, 0x88, 0x79, 0x00, 0x7A, 0xE0),
        patchInfo = bytes(patch, 0x0E, 0x31, 0x01, 0xE4, 0x19),
        rawFram = ByteArray(344),
        fram = ByteArray(344).also {
            it[4] = state.toByte()
            it[316] = ageMinutes.toByte(); it[317] = (ageMinutes shr 8).toByte()
            it[326] = lifetime.toByte(); it[327] = (lifetime shr 8).toByte()
        },
    )

    @Test
    fun `the check says where the sensor is in its life, and a value only while it runs`() {
        val fresh = read(state = 1, ageMinutes = 0).check()!!
        assertEquals(LibreNfc.Stage.NOT_STARTED, fresh.stage)
        assertEquals(null, fresh.mgDl)
        assertEquals(15, fresh.days) // a Libre 2 Plus: 15 days (+ its 12-hour grace)

        val warming = read(state = 2, ageMinutes = 22).check()!!
        assertEquals(LibreNfc.Stage.WARMING_UP, warming.stage)
        assertEquals(38, warming.minutesToReadings)
        assertEquals(1, warming.day)

        val ended = read(state = 4, ageMinutes = 15 * 1440 + 100).check()!!
        assertEquals(LibreNfc.Stage.ENDED, ended.stage)
        assertEquals(16, ended.day)
        assertEquals(null, ended.minutesToReadings)
    }

    @Test
    fun `no check from memory that can't be trusted`() {
        assertEquals(null, read(state = 0, ageMinutes = 0).check()) // a state no sensor writes
        assertEquals(null, read(state = 3, ageMinutes = 500, patch = 0xDF).check()) // a Libre 1
        assertEquals(null, read(state = 3, ageMinutes = 500).copy(fram = null).check()) // didn't decrypt
    }

    @Test
    fun `only Libre 2 EU patch infos are streamable`() {
        assertTrue(Libre2.isLibre2Eu(bytes(0x9D, 0x08, 0x30, 0x01, 0x76, 0x25)))
        assertTrue(Libre2.isLibre2Eu(bytes(0xC5, 0x09, 0x31, 0x01, 0x00, 0x00)))
        assertFalse(Libre2.isLibre2Eu(bytes(0x76, 0x08, 0x30, 0x02, 0x00, 0x00))) // Libre 2 US
        assertFalse(Libre2.isLibre2Eu(bytes(0xDF, 0x00, 0x00, 0x01, 0x00, 0x00))) // Libre 1
    }
}
