package com.sukoon.app.data.source.libre

import org.junit.Assert.assertEquals
import org.junit.Test

class Iso15693Test {

    @Test
    fun `crc16 matches the published ISO15693 (CRC-B) catalog check value`() {
        // "123456789" is the standard self-check string used across virtually every CRC
        // catalog entry. If this fails, the CRC implementation itself is wrong — start here,
        // before suspecting anything sensor-specific.
        val check = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0xD64E, Iso15693.crc16(check))
    }

    @Test
    fun `readSingleBlock frame has the right shape`() {
        val uid = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val frame = Iso15693.readSingleBlock(uid, blockNumber = 5)

        // flags, command, 8-byte UID, block number, 2-byte CRC = 13 bytes
        assertEquals(13, frame.size)
        assertEquals(Iso15693.FLAGS_ADDRESSED, frame[0])
        assertEquals(Iso15693.CMD_READ_SINGLE_BLOCK, frame[1])
        assertEquals(5.toByte(), frame[10])
    }

    @Test
    fun `readMultipleBlocks frame has the right shape`() {
        val uid = ByteArray(8) { it.toByte() }
        val frame = Iso15693.readMultipleBlocks(uid, firstBlock = 0, blockCount = 10)

        // flags, command, 8-byte UID, first block, block count, 2-byte CRC = 14 bytes
        assertEquals(14, frame.size)
        assertEquals(0.toByte(), frame[10])
        assertEquals(9.toByte(), frame[11]) // encoded as count - 1, per spec
    }

    @Test
    fun `getSystemInfo frame has the right shape`() {
        val uid = ByteArray(8) { it.toByte() }
        val frame = Iso15693.getSystemInfo(uid)

        // flags, command, 8-byte UID, 2-byte CRC = 12 bytes
        assertEquals(12, frame.size)
        assertEquals(Iso15693.CMD_GET_SYSTEM_INFO, frame[1])
    }

    @Test
    fun `customCommand carries the given command code`() {
        val uid = ByteArray(8)
        val frame = Iso15693.customCommand(uid, commandCode = 0xA1.toByte())

        assertEquals(0xA1.toByte(), frame[1])
    }
}
