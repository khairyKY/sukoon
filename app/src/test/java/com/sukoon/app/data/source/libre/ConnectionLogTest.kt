package com.sukoon.app.data.source.libre

import com.sukoon.app.data.source.libre.ConnectionLog.Event
import com.sukoon.app.data.source.libre.ConnectionLog.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.Instant

class ConnectionLogTest {
    private fun at(s: Long) = Instant.ofEpochSecond(1_760_000_000 + s)

    @Test
    fun `a drop is each down, back after the next up, newest first`() {
        val drops = ConnectionLog.drops(
            listOf(
                Event(at(0), Kind.UP),
                Event(at(3600), Kind.DOWN, status = 8, connectedSeconds = 3600),
                Event(at(3690), Kind.UP),
                Event(at(4000), Kind.DOWN, status = ConnectionLog.STATUS_WATCHDOG, connectedSeconds = 310),
            ),
        )
        assertEquals(2, drops.size)
        assertEquals(at(4000), drops[0].at)
        assertNull(drops[0].backAfterSeconds)
        assertEquals(ConnectionLog.Drop(at(3600), 3600, 8, 90), drops[1])
    }

    @Test
    fun `the file round-trips and skips broken lines`() {
        val file = File.createTempFile("connection", ".log").apply { deleteOnExit() }
        val log = ConnectionLog(file)
        log.add(Event(at(0), Kind.UP))
        file.appendText("garbage\n")
        log.add(Event(at(60), Kind.DOWN, 19, 60))
        assertEquals(listOf(Event(at(0), Kind.UP), Event(at(60), Kind.DOWN, 19, 60)), log.events())
        assertEquals(1, log.dropsSince(at(30)).size)
        assertEquals(0, log.dropsSince(at(61)).size)
    }
}
