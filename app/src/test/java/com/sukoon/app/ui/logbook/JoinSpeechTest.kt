package com.sukoon.app.ui.logbook

import org.junit.Assert.assertEquals
import org.junit.Test

class JoinSpeechTest {

    @Test
    fun `stretches of speech join with one space, blanks dropped`() {
        assertEquals("kalt 2 sandwich foul w tea", joinSpeech("kalt 2 sandwich foul", " w tea "))
        assertEquals("w tea", joinSpeech("", "w tea"))
        assertEquals("kalt foul", joinSpeech("kalt foul ", "  "))
        assertEquals("", joinSpeech(" ", ""))
    }
}
