package com.sukoon.app.alarms

import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AlarmEngineTest {

    private val t0 = Instant.parse("2026-10-03T03:00:00Z")
    private val settings = AlarmSettings()
    private fun at(minute: Long) = t0.plusSeconds(minute * 60)
    private fun r(minute: Long, mgDl: Int) = GlucoseReading(at(minute), mgDl, TrendDirection.STEADY, SourceKind.LIBRE_BLE)

    /** Feeds one reading per minute (values[i] at minute i) and collects what fired, minute by minute. */
    private fun run(values: List<Int>, s: AlarmSettings = settings): List<Pair<Long, Set<AlarmType>>> {
        var state = AlarmState()
        val readings = mutableListOf<GlucoseReading>()
        return values.mapIndexed { i, v ->
            readings += r(i.toLong(), v)
            val e = AlarmEngine.evaluate(readings.takeLast(30), at(i.toLong()), s, state)
            state = e.state
            i.toLong() to e.fire.map { it.type }.toSet()
        }
    }

    @Test
    fun `quiet hours hold back highs but never lows`() {
        val quiet = settings.copy(quietHighsFrom = 22, quietHighsTo = 7) // t0 is 03:00 UTC: inside
        assertTrue(AlarmEngine.evaluate(listOf(r(0, 300)), at(0), quiet, AlarmState(), ZoneOffset.UTC).fire.isEmpty())
        assertEquals(setOf(AlarmType.URGENT_LOW), AlarmEngine.evaluate(listOf(r(0, 50)), at(0), quiet, AlarmState(), ZoneOffset.UTC).fire.map { it.type }.toSet())
        val seven = at(4 * 60)
        val morning = AlarmEngine.evaluate(listOf(GlucoseReading(seven, 300, TrendDirection.STEADY, SourceKind.LIBRE_BLE)), seven, quiet, AlarmState(), ZoneOffset.UTC)
        assertEquals(setOf(AlarmType.HIGH), morning.fire.map { it.type }.toSet())
        assertTrue(quiet.quietAt(23) && quiet.quietAt(6) && !quiet.quietAt(7) && !quiet.quietAt(21))
    }

    private fun firedAt(log: List<Pair<Long, Set<AlarmType>>>, type: AlarmType) = log.filter { type in it.second }.map { it.first }

    @Test
    fun `urgent low fires immediately and repeats every 5 minutes`() {
        val log = run(List(12) { 50 })
        assertEquals(listOf(0L, 5L, 10L), firedAt(log, AlarmType.URGENT_LOW))
        assertTrue(firedAt(log, AlarmType.LOW).isEmpty()) // urgent supersedes low
    }

    @Test
    fun `low fires once, repeats after its snooze window, and clears with hysteresis`() {
        val values = List(20) { 65 } + listOf(72, 74) + List(5) { 76 }
        val log = run(values)
        assertEquals(listOf(0L, 15L), firedAt(log, AlarmType.LOW))
        // 72 and 74 are above 70 but inside the +5 hysteresis: the episode is still open, no new alert.
        var state = AlarmState()
        val readings = mutableListOf<GlucoseReading>()
        var clearedAt: Long? = null
        values.forEachIndexed { i, v ->
            readings += r(i.toLong(), v)
            val e = AlarmEngine.evaluate(readings.takeLast(30), at(i.toLong()), settings, state)
            state = e.state
            if (AlarmType.LOW in e.cleared && clearedAt == null) clearedAt = i.toLong()
        }
        assertEquals(22L, clearedAt) // first 76 (≥ 75)
    }

    @Test
    fun `recovering from urgent low into low is the same episode`() {
        val log = run(listOf(50, 50, 52, 61, 63, 64))
        assertEquals(listOf(0L), firedAt(log, AlarmType.URGENT_LOW))
        assertTrue(firedAt(log, AlarmType.LOW).isEmpty())
    }

    @Test
    fun `snooze silences low until it ends, but urgent low is capped at 5 minutes`() {
        var state = AlarmEngine.evaluate(listOf(r(0, 65)), at(0), settings, AlarmState()).state
        state = AlarmEngine.snooze(state, AlarmType.LOW, 30, at(0))
        assertTrue(AlarmEngine.evaluate(listOf(r(20, 65)), at(20), settings, state).fire.isEmpty())
        assertEquals(AlarmType.LOW, AlarmEngine.evaluate(listOf(r(31, 65)), at(31), settings, state).fire.single().type)

        var urgent = AlarmEngine.evaluate(listOf(r(0, 48)), at(0), settings, AlarmState()).state
        urgent = AlarmEngine.snooze(urgent, AlarmType.URGENT_LOW, 60, at(0))
        assertEquals(AlarmType.URGENT_LOW, AlarmEngine.evaluate(listOf(r(5, 48)), at(5), settings, urgent).fire.single().type)
    }

    @Test
    fun `high fires above threshold and respects its own snooze`() {
        val log = run(List(70) { 260 })
        assertEquals(listOf(0L, 60L), firedAt(log, AlarmType.HIGH))
        assertTrue(run(List(10) { 260 }, settings.copy(highEnabled = false)).all { it.second.isEmpty() })
    }

    @Test
    fun `going low soon fires once on a steep fall before crossing the low line`() {
        // 120 falling 3/min → projected 60 in 20 min while still above 70.
        val log = run((0 until 10).map { 120 - it * 3 })
        val fired = firedAt(log, AlarmType.GOING_LOW)
        assertTrue("fired at $fired", fired.size == 1 && fired.single() >= 5)
    }

    @Test
    fun `old readings never trigger glucose alarms, but signal loss does`() {
        val reading = listOf(r(0, 45))
        val stale = AlarmEngine.evaluate(reading, at(15), settings, AlarmState())
        assertTrue(stale.fire.none { it.type == AlarmType.URGENT_LOW })
        val lost = AlarmEngine.evaluate(reading, at(20), settings, AlarmState())
        assertEquals(listOf(AlarmType.SIGNAL_LOSS), lost.fire.map { it.type })
        assertTrue(AlarmEngine.evaluate(emptyList(), at(60), settings, AlarmState()).fire.isEmpty()) // never had a reading
    }

    @Test
    fun `settings are clamped to safe ranges`() {
        val s = AlarmSettings(lowMgDl = 40, highMgDl = 1000, signalLossMinutes = 1).sanitized()
        assertEquals(60, s.lowMgDl) // can't sit at or below urgent (55)
        assertEquals(400, s.highMgDl)
        assertEquals(10, s.signalLossMinutes)
    }

    @Test
    fun `no readings keeps repeating every 30 minutes however old the last reading is`() {
        val t0 = Instant.parse("2026-10-07T03:00:00Z")
        val last = GlucoseReading(t0, 120, TrendDirection.STEADY, SourceKind.LIBRE_BLE)
        val first = AlarmEngine.evaluate(listOf(last), t0.plus(Duration.ofMinutes(20)), AlarmSettings(), AlarmState())
        assertEquals(listOf(AlarmType.SIGNAL_LOSS), first.fire.map { it.type })
        val later = AlarmEngine.evaluate(listOf(last), t0.plus(Duration.ofMinutes(50)), AlarmSettings(), first.state)
        assertEquals(listOf(AlarmType.SIGNAL_LOSS), later.fire.map { it.type }) // an hour-old reading still counts
        assertTrue(later.cleared.isEmpty())
    }
}
