package com.sukoon.app.data.source

import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedSourceTest {

    @Test
    fun `connect transitions to Connected and starts emitting bounded readings`() = runTest {
        val source = SimulatedSource(scope = backgroundScope, tickIntervalMillis = 100, random = Random(1))

        source.connect()
        val reading = source.readings.first()

        assertEquals(SourceStatus.Connected, source.status.value)
        assertEquals(SourceKind.SIMULATED, reading.source)
        assertTrue(reading.glucoseMgDl in 40..400)
    }

    @Test
    fun `disconnect resets status to Disconnected`() = runTest {
        val source = SimulatedSource(scope = backgroundScope, tickIntervalMillis = 100, random = Random(2))

        source.connect()
        source.readings.first()
        source.disconnect()

        assertEquals(SourceStatus.Disconnected, source.status.value)
    }

    @Test
    fun `readings stay within physiologically bounded range over many ticks`() = runTest {
        val source = SimulatedSource(scope = backgroundScope, tickIntervalMillis = 10, random = Random(3))

        source.connect()
        repeat(50) {
            assertTrue(source.readings.first().glucoseMgDl in 40..400)
        }
    }

    @Test
    fun `connect is idempotent`() = runTest {
        val source = SimulatedSource(scope = backgroundScope, tickIntervalMillis = 100, random = Random(4))

        source.connect()
        source.connect() // should not throw or restart the loop
        source.readings.first()

        assertEquals(SourceStatus.Connected, source.status.value)
    }
}
