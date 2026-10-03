package com.sukoon.app.sharing

import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SharingTest {

    @Test
    fun `readings come back from Postgres timestamps with offsets`() {
        val parsed = Sharing.parseReadings(
            """[{"ts":"2026-10-03T08:00:00+00:00","mg_dl":142,"trend":1},{"ts":"2026-10-03T08:01:00.5+03:00","mg_dl":60,"trend":-2}]""",
        )
        assertEquals(Instant.parse("2026-10-03T08:00:00Z"), parsed[0].timestamp)
        assertEquals(142, parsed[0].glucoseMgDl)
        assertEquals(TrendDirection.RISING, parsed[0].trend)
        assertEquals(Instant.parse("2026-10-03T05:01:00.500Z"), parsed[1].timestamp)
        assertEquals(TrendDirection.FALLING_FAST, parsed[1].trend)
    }

    @Test
    fun `trend codes round-trip and uploads use them`() {
        TrendDirection.entries.forEach { assertEquals(it, Sharing.trendFrom(Sharing.trendCode(it))) }
        val json = Sharing.readingJson(com.sukoon.app.data.source.GlucoseReading(Instant.parse("2026-10-03T08:00:00Z"), 99, TrendDirection.FALLING, com.sukoon.app.data.source.SourceKind.LIBRE_BLE))
        assertEquals("2026-10-03T08:00:00Z", json.getString("ts"))
        assertEquals(-1, json.getInt("trend"))
    }

    @Test
    fun `invite codes read in two halves`() {
        assertEquals("ABCD-EFGH", Sharing.formatCode("ABCDEFGH"))
    }

    @Test
    fun `server errors keep their human part`() {
        assertEquals("Invalid login credentials", Supabase.errorMessage("""{"error":"invalid_grant","error_description":"Invalid login credentials"}""", 400))
        assertEquals("Email not confirmed", Supabase.errorMessage("""{"code":400,"error_code":"email_not_confirmed","msg":"Email not confirmed"}""", 400))
        assertEquals("That code is wrong, used or expired", Supabase.errorMessage("""{"code":"P0001","message":"That code is wrong, used or expired"}""", 400))
        assertEquals("HTTP 502", Supabase.errorMessage("<html>bad gateway</html>", 502))
    }
}
