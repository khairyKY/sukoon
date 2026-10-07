package com.sukoon.app.sharing

import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// org.json is Android's: the unit tests get the real one from the test classpath (testImplementation org.json).
class LibreLinkUpTest {

    @Test
    fun `login answers - signed in, sent to a region, refused`() {
        val ok = LibreLinkUp.parseLogin(JSONObject("""{"status":0,"data":{"user":{"id":"abc-123"},"authTicket":{"token":"T","expires":1791000000,"duration":15552000000}}}"""))
        assertEquals(LibreLinkUp.Login.Ok("T", 1791000000, "abc-123"), ok)
        assertEquals(LibreLinkUp.Login.Redirect("eu"), LibreLinkUp.parseLogin(JSONObject("""{"status":0,"data":{"redirect":true,"region":"eu"}}""")))
        assertEquals(LluProblem.WRONG_LOGIN, (LibreLinkUp.parseLogin(JSONObject("""{"status":2,"error":{"message":"incorrect username/password"}}""")) as LibreLinkUp.Login.Refused).problem)
        assertEquals(LluProblem.ACCEPT_TERMS, (LibreLinkUp.parseLogin(JSONObject("""{"status":4,"data":{"step":{"type":"tou"}}}""")) as LibreLinkUp.Login.Refused).problem)
    }

    @Test
    fun `the account id header is the sha256 of the user id`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", LibreLinkUp.sha256("abc"))
    }

    @Test
    fun `connections and graph - UTC factory times, mg per dl, trend arrows, oldest first`() {
        val connections = LibreLinkUp.parseConnections(
            JSONObject("""{"status":0,"data":[{"patientId":"p1","firstName":"Abdo","lastName":"Islam","glucoseMeasurement":{"FactoryTimestamp":"10/7/2026 9:52:31 AM","Timestamp":"10/7/2026 12:52:31 PM","ValueInMgPerDl":142,"TrendArrow":4}}]}"""),
        )
        assertEquals(1, connections.size)
        assertEquals("Abdo Islam", connections[0].name)
        val latest = connections[0].latest!!
        assertEquals(Instant.parse("2026-10-07T09:52:31Z"), latest.timestamp)
        assertEquals(142, latest.glucoseMgDl)
        assertEquals(TrendDirection.RISING, latest.trend)

        val graph = LibreLinkUp.parseGraph(
            JSONObject(
                """{"status":0,"data":{"connection":{"glucoseMeasurement":{"FactoryTimestamp":"10/7/2026 10:01:00 AM","ValueInMgPerDl":150,"TrendArrow":5}},
                "graphData":[{"FactoryTimestamp":"10/7/2026 9:45:00 AM","ValueInMgPerDl":130},{"FactoryTimestamp":"10/7/2026 9:30:00 AM","ValueInMgPerDl":120},{"FactoryTimestamp":"bad","ValueInMgPerDl":1}]}}""",
            ),
        )
        assertEquals(listOf(120, 130, 150), graph.map { it.glucoseMgDl })
        assertEquals(TrendDirection.RISING_FAST, graph.last().trend)
        assertTrue(graph.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
        assertNull(LibreLinkUp.parseTime("13/45/2026 9:00:00 AM"))
    }
}
