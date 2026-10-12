package com.sukoon.app.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteCodeTest {

    @Test
    fun `typing keeps capitals and only code characters`() {
        assertEquals("ABCDEFGH", InviteCode.normalize("abcd-efgh"))
        assertEquals("ABCD", InviteCode.normalize("ab o0 i1 cd")) // O, 0, I and 1 are never in a code
        assertEquals("ABCDEFGH", InviteCode.normalize("abcdefghjk"))
    }

    @Test
    fun `a code is found in a link, a message or on its own`() {
        assertEquals("K7MPQ2XZ", InviteCode.find(InviteCode.link("K7MPQ2XZ")))
        assertEquals("K7MPQ2XZ", InviteCode.find("Kai invited you. Tap to follow: https://khairyky.github.io/sukoon/join.html?c=K7MPQ2XZ Or enter K7MP-Q2XZ"))
        assertEquals("K7MPQ2XZ", InviteCode.find("k7mp-q2xz"))
        assertEquals("K7MPQ2XZ", InviteCode.find("code: K7MPQ2XZ."))
        assertNull(InviteCode.find("hello world")) // words of 5 letters aren't codes
        assertNull(InviteCode.find("ABCDEFG0")) // a 0 can't be in a code
        assertNull(InviteCode.find("ABCDEFGHJ")) // too long
    }

    @Test
    fun `codes show in two halves and travel as a link`() {
        assertEquals("K7MP-Q2XZ", InviteCode.format("K7MPQ2XZ"))
        assertEquals("https://khairyky.github.io/sukoon/join.html?c=K7MPQ2XZ", InviteCode.link("K7MPQ2XZ"))
    }
}
