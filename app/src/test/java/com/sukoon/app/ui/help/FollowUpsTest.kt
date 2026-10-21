package com.sukoon.app.ui.help

import com.sukoon.app.alarms.AlarmType
import com.sukoon.app.insights.MealSlot
import com.sukoon.app.insulin.DoseSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpsTest {

    private val live = listOf(true, true, false) // real readings, alarms reach you, no contact yet
    private val none = followUpsDone(signedIn = false, hasContact = false, extras = false, dose = DoseSettings())

    @Test fun `each follow-up ticks from its own setting`() {
        assertEquals(listOf(false, false, false, false), none)
        assertEquals(listOf(true, true, true, false), followUpsDone(true, true, true, DoseSettings()))
        assertTrue(followUpsDone(false, false, false, DoseSettings(enabled = true))[3])
        assertTrue(followUpsDone(false, false, false, DoseSettings(carbRatio = mapOf(MealSlot.LUNCH to 10.0)))[3])
    }

    @Test fun `due after the first real reading with alarms reaching you`() {
        assertTrue(followUpsDue(offered = true, start = live, low = false, done = none))
        assertFalse(followUpsDue(true, listOf(false, true, false), false, none)) // waiting for the first reading
        assertFalse(followUpsDue(true, listOf(true, false, false), false, none)) // alarms can't reach you: the banner's turn
    }

    @Test fun `never for older installs, after Got it, during a low or once all done`() {
        assertFalse(followUpsDue(offered = false, start = live, low = false, done = none))
        assertFalse(followUpsDue(true, live, low = true, done = none))
        assertFalse(followUpsDue(true, live, false, listOf(true, true, true, true)))
    }

    @Test fun `low is under the low line or a low alarm going`() {
        assertTrue(lowNow(65, 70, emptySet()))
        assertFalse(lowNow(70, 70, emptySet()))
        assertFalse(lowNow(null, 70, setOf(AlarmType.HIGH)))
        assertTrue(lowNow(120, 70, setOf(AlarmType.URGENT_LOW)))
    }
}
