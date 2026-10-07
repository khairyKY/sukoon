package com.sukoon.app.sharing

import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DexcomShareTest {

    @Test
    fun `readings come newest first as Date(ms) and leave oldest first, with trends by name or number`() {
        val rows = JSONArray(
            """[{"WT":"Date(1791328200000)","ST":"Date(1791328200000)","DT":"Date(1791328200000+0300)","Value":150,"Trend":"FortyFiveUp"},
                {"WT":"Date(1791327900000)","Value":140,"Trend":"Flat"},
                {"WT":"Date(1791327600000)","Value":130,"Trend":7},
                {"WT":"nonsense","Value":120,"Trend":"Flat"},
                {"WT":"Date(1791327300000)","Value":5,"Trend":"Flat"}]""",
        )
        val readings = DexcomShare.parseReadings(rows)
        assertEquals(listOf(130, 140, 150), readings.map { it.glucoseMgDl })
        assertEquals(Instant.ofEpochMilli(1791328200000), readings.last().timestamp)
        assertEquals(listOf(TrendDirection.FALLING_FAST, TrendDirection.STEADY, TrendDirection.RISING), readings.map { it.trend })
    }

    @Test
    fun `errors - an ended session signs in again, a bad login is said so`() {
        assertTrue(DexcomShare.parseError("""{"Code":"SessionIdNotFound","Message":"Session not active or timed out"}""").sessionEnded)
        assertTrue(DexcomShare.parseError("""{"Code":"AccountPasswordInvalid"}""").wrongLogin)
        assertTrue(DexcomShare.parseError("""{"Code":"SSO_AuthenticateAccountNotFound"}""").wrongLogin)
        assertEquals("abc-123", DexcomShare.unquote("\"abc-123\"\n"))
    }
}
