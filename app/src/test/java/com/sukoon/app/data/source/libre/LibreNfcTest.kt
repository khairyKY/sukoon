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
    fun `only Libre 2 EU patch infos are streamable`() {
        assertTrue(Libre2.isLibre2Eu(bytes(0x9D, 0x08, 0x30, 0x01, 0x76, 0x25)))
        assertTrue(Libre2.isLibre2Eu(bytes(0xC5, 0x09, 0x31, 0x01, 0x00, 0x00)))
        assertFalse(Libre2.isLibre2Eu(bytes(0x76, 0x08, 0x30, 0x02, 0x00, 0x00))) // Libre 2 US
        assertFalse(Libre2.isLibre2Eu(bytes(0xDF, 0x00, 0x00, 0x01, 0x00, 0x00))) // Libre 1
    }
}
