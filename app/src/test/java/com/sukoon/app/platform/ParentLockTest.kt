package com.sukoon.app.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentLockTest {

    @Test
    fun `the PIN is checked against a salted hash, never stored itself`() {
        val stored = ParentLock.encode("2468")
        assertFalse(stored.contains("2468"))
        assertTrue(ParentLock.matches("2468", stored))
        assertFalse(ParentLock.matches("2469", stored))
        assertFalse(ParentLock.matches("2468", null))
        assertFalse(ParentLock.matches("2468", "garbage"))
        assertTrue(ParentLock.encode("2468") != stored) // a new salt each time
    }

    @Test
    fun `a PIN is 4 to 8 digits`() {
        assertTrue(ParentLock.valid("1234"))
        assertFalse(ParentLock.valid("123"))
        assertFalse(ParentLock.valid("12a4"))
        assertFalse(ParentLock.valid("123456789"))
    }
}
