package com.sukoon.app.data.source.libre

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden vectors from tools/libre2_reference.py — an independent Python transliteration of
 * GlucoseDirect's Swift (unbounded ints, unsigned bytes), so agreement means the Kotlin port has
 * no signed-byte / 16-bit-overflow bugs. Regenerate with the script; don't hand-edit the hex.
 * These prove the port matches the method; only a real sensor (FRAM/BLE CRCs + finger-pricks)
 * proves the method matches Kai's sensor.
 */
class Libre2Test {

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val uid = hex("0E02AD49FE9307E0")
    private val patch = hex("9D08300136C6")

    @Test
    fun `crc matches the CRC-16 MCRF4XX catalog value in GlucoseDirect's bit-reversed, byte-swapped form`() {
        assertEquals(0xF689, Libre2.crc16("123456789".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `fram decryption matches the reference`() {
        assertArrayEquals(hex("468AA9107C1FC9B1AD59A9FBA08C26DCAE3B9BCDA06F5690439CC7825ED9331B8C0BD0FC6E7D801835FBD1287A87B65CAD3A1C07D0130EB947CC1733F9295F29EF1AAF4BC8160EEF8A59990522E81FD005F03ABFB5F68740E76F6CF24E68E4909E1C89310898091D82380CC051C833FAC5023D76A687BB7945825C7352EE4EDA9F57997CA3676BF71D19DCEC11BCCD50737293DC8D264A57C6021BA1E9AB611A91B43A00A28B2ED73BEF7188CE1122BE019FC9F8666C59305880D7B11F6336B882BF683BE5D306661D8247ABA2739CA8B91CE5221C90D59FAAA55F4E1A8EEF8438FF22590887E9C00BE4FC62BC1EDDF5DF68D3A14BB638F772E2A34136EE15CC6320887029C73A2B666B3AF6B3E6C7997EB904914679F524B90C7030B095DD0F85192E260AB7C173500A4AF31B826FA3C5D2E37B696FBAC1C2614DD5E290DF0A50AC5134136D40A6B677E46802272A4549ADF5099038FCBA"), Libre2.decryptFram(uid, patch, hex("981A09F3C03F411D73FB49769BAEAA12815E13A3E7B810612BA5949AEA6E40A0CF4B9D659F7F5D3D13285AD9049D0F737A2EFF15D2FC7DA9D7842F5708A61973FF67D57D5EE3FB358508D725425F2BC8FB661C7CA9B479675EA59147A14A2FFD0CAF6A05A20F6CEE7518299C74473203C3E570C9FFFD70BF0439CABAF8F4B056D6BD8A1FEAC69DDF54C18FE1DF1F3F1ACB6DA8323F7072223941FB39A89D6C25458EC419A6088D768A4649F9458AE51541F1996A910254A45FB25C551B6D0E6605B8A18D86A78D3807AFA211172DD73452F6687BD53B543C0613096120455B6DBF306AF777F9F3B7E9B872A44C78A388CCF33584C7258CB526259E12491D94C4C9AAAE5954EF8EA5CCD35CB149CC777525C60A35C0A68FF7A52FA5E2C52A9296B243E5753ABD207402C347C8A490EAAE66DC86A3AA88F5F32633F37BD217A57234CBADC84490895ECD3C2C82A398227FC32155004D723EBF")))
        assertNull(Libre2.decryptFram(uid, hex("DF0000010000"), ByteArray(344))) // Libre 1 FRAM isn't encrypted
    }

    @Test
    fun `ble decryption matches the reference and rejects a corrupted packet`() {
        val packet = hex("F3B2C2FD1B6F942EB1FB00FECE5045C2BECC49E99231BBB61D2C685703C09C91C491DAE1E0211CBB8A08558C49D9")
        assertArrayEquals(hex("9B58DF805FA47991F89245DB09D24A0EEE40E9641769C0E8F0D6161A7A916AE8647CC88EFDD8DF7C34125694"), Libre2.decryptBle(uid, packet))
        packet[10] = (packet[10].toInt() xor 0x01).toByte()
        assertThrows(Libre2.BleDecryptException::class.java) { Libre2.decryptBle(uid, packet) }
    }

    @Test
    fun `streaming unlock payload and nfc enable parameters match the reference`() {
        assertArrayEquals(hex("2B0000009A855339447B9B9B"), Libre2.streamingUnlockPayload(uid, patch, 42, 1))
        assertArrayEquals(hex("560100001893CEC23C0D03D7"), Libre2.streamingUnlockPayload(uid, patch, 42, 300))
        assertArrayEquals(hex("1E2A000000D49BA069"), Libre2.enableStreamingParameters(uid, patch, 42))
    }

    @Test
    fun `factory calibration matches the reference`() {
        assertEquals(181, FactoryCalibration(i1 = 0, i2 = 500, i3 = 20.0, i4 = 9000.0, i5 = 0.0, i6 = 6000.0).calibrate(1800.0, 6500.0, 0.0))
        assertEquals(181, FactoryCalibration(i1 = 0, i2 = 200, i3 = -5.0, i4 = 8000.0, i5 = 0.0, i6 = 5800.0).calibrate(1200.0, 6600.0, 40.0))
        assertEquals(166, FactoryCalibration(i1 = 0, i2 = 900, i3 = 0.0, i4 = 10000.0, i5 = 0.0, i6 = 6200.0).calibrate(3000.0, 6400.0, -40.0))
        assertNull(FactoryCalibration(0, 0, 0.0, 1.0, 0.0, 6000.0).calibrate(1500.0, 6500.0, 0.0)) // i2 = 0 is invalid
    }

    @Test
    fun `fram crc gate accepts a consistent fram and rejects one flipped bit`() {
        val fram = ByteArray(344) { (it * 7).toByte() }
        for ((crcAt, from, to) in listOf(Triple(0, 2, 24), Triple(24, 26, 320), Triple(320, 322, 344))) {
            val crc = Libre2.crc16(fram, from, to)
            fram[crcAt] = (crc ushr 8).toByte()
            fram[crcAt + 1] = crc.toByte()
        }
        assertTrue(Libre2.framCrcValid(fram))
        fram[100] = (fram[100].toInt() xor 0x10).toByte()
        assertFalse(Libre2.framCrcValid(fram))
    }

    @Test
    fun `serial is the family digit plus 10 base-32 characters`() {
        val serial = Libre2.serial(uid, patch)
        assertEquals(11, serial.length)
        assertEquals('3', serial[0]) // patch info byte 2 = 0x30 -> family 3 (Libre 2)
    }
}
