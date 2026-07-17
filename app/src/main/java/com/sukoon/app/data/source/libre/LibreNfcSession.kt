package com.sukoon.app.data.source.libre

import android.nfc.Tag
import android.nfc.tech.NfcV
import java.io.IOException

/**
 * Wraps Android's NfcV tech for a Libre sensor NFC session (docs/PLAN.md §1). Standard Android
 * NFC API usage throughout — confident, low-risk. What's deliberately NOT here: the
 * Libre-specific key derivation that turns a read patch-info + UID into something that unlocks
 * the BLE stream — see LibreKeyDerivation.kt for why that's a separate, honestly-unimplemented
 * seam rather than guessed-at bytes.
 */
class LibreNfcSession(tag: Tag) {

    private val nfcV = NfcV.get(tag)
        ?: throw IllegalArgumentException("Tag does not support NfcV/ISO15693")

    // ISO15693 addressed commands need the UID in transmission order, which on most devices is
    // the REVERSE of what Tag.getId() returns. Unverified without a real sensor to test against —
    // if reads come back with an error status byte, try dropping .reversedArray() first.
    private val uid: ByteArray = tag.id.reversedArray()

    val isConnected: Boolean get() = nfcV.isConnected

    fun connect() = nfcV.connect()
    fun close() = nfcV.close()

    @Throws(IOException::class)
    fun readSystemInfo(): ByteArray = transceive(Iso15693.getSystemInfo(uid))

    @Throws(IOException::class)
    fun readSingleBlock(blockNumber: Int): ByteArray = transceive(Iso15693.readSingleBlock(uid, blockNumber))

    @Throws(IOException::class)
    fun readMultipleBlocks(firstBlock: Int, blockCount: Int): ByteArray =
        transceive(Iso15693.readMultipleBlocks(uid, firstBlock, blockCount))

    /**
     * Libre sensors need this custom command before ordinary block reads behave reliably — the
     * response identifies the sensor generation, which is what the firmware-versioned decoder
     * work (docs/PLAN.md §10 risk #1) keys off. The exact command code (0xA1) is commonly cited
     * in community write-ups but is NOT independently verified here — confirm the response
     * against a real sensor before trusting it; if it errors, this byte is the first thing to
     * re-check.
     */
    @Throws(IOException::class)
    fun getPatchInfo(): ByteArray = transceive(Iso15693.customCommand(uid, CMD_GET_PATCH_INFO))

    private fun transceive(command: ByteArray): ByteArray {
        val response = nfcV.transceive(command)
        // First byte of any ISO15693 response is the flags/error byte; bit 0 set means error.
        if (response.isNotEmpty() && (response[0].toInt() and 0x01) != 0) {
            throw IOException("ISO15693 error response: ${response.joinToString(",") { "%02X".format(it) }}")
        }
        return response.copyOfRange(1, response.size)
    }

    private companion object {
        const val CMD_GET_PATCH_INFO: Byte = 0xA1.toByte()
    }
}
