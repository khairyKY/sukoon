package com.sukoon.app.data.source.libre

/**
 * ISO/IEC 15693 command framing — the standard vicinity-card protocol Libre sensors speak over
 * NFC (Android's NfcV tech). This is the actual ISO standard, not Abbott-proprietary, so it's
 * implemented with real confidence — unlike the sensor-specific pieces in LibreNfcSession.kt.
 */
object Iso15693 {

    // Addressed, high-data-rate, non-inventory — the flags byte used across widely published
    // ISO15693 reference implementations (libnfc, proxmark3, NFC Forum examples).
    const val FLAGS_ADDRESSED: Byte = 0x22

    const val CMD_READ_SINGLE_BLOCK: Byte = 0x20
    const val CMD_READ_MULTIPLE_BLOCKS: Byte = 0x23
    const val CMD_GET_SYSTEM_INFO: Byte = 0x2B

    /** Addressed Read Single Block command frame, CRC included. */
    fun readSingleBlock(uid: ByteArray, blockNumber: Int): ByteArray =
        appendCrc(byteArrayOf(FLAGS_ADDRESSED, CMD_READ_SINGLE_BLOCK, *uid, blockNumber.toByte()))

    /** Addressed Read Multiple Blocks command frame, CRC included. */
    fun readMultipleBlocks(uid: ByteArray, firstBlock: Int, blockCount: Int): ByteArray = appendCrc(
        byteArrayOf(FLAGS_ADDRESSED, CMD_READ_MULTIPLE_BLOCKS, *uid, firstBlock.toByte(), (blockCount - 1).toByte()),
    )

    /** Addressed Get System Information command frame, CRC included. */
    fun getSystemInfo(uid: ByteArray): ByteArray =
        appendCrc(byteArrayOf(FLAGS_ADDRESSED, CMD_GET_SYSTEM_INFO, *uid))

    /**
     * Generic custom-command frame — ISO15693 reserves 0xA0-0xDF for manufacturer-specific
     * commands. Used for Libre's "Get Patch Info" command; see LibreNfcSession.getPatchInfo for
     * why that specific command code is flagged as unverified rather than a plain constant here.
     */
    fun customCommand(uid: ByteArray, commandCode: Byte): ByteArray =
        appendCrc(byteArrayOf(FLAGS_ADDRESSED, commandCode, *uid))

    private fun appendCrc(payload: ByteArray): ByteArray {
        val crc = crc16(payload)
        return payload + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
    }

    /**
     * The standard ISO15693 CRC — same algorithm as ISO14443-3's "CRC_B" (poly 0x8408
     * bit-reversed, init 0xFFFF, input/output reflected, final XOR 0xFFFF). Verified against
     * the published CRC-16/ISO-IEC-14443-3-B catalog check value in Iso15693Test.
     */
    internal fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0x8408 else crc ushr 1
            }
        }
        return crc.inv() and 0xFFFF
    }
}
