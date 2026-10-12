package com.sukoon.app.sharing

import org.junit.Assert.assertEquals
import org.junit.Test

class NewFollowersTest {

    @Test
    fun `only people who weren't there last time, and nobody on the first check`() {
        val reema = Person("r", "Reema")
        val mum = Person("m", "Mum")
        assertEquals(emptyList<Person>(), NewFollowers.newOnes(null, listOf(reema, mum))) // first check: just remember
        assertEquals(listOf(mum), NewFollowers.newOnes(setOf("r"), listOf(reema, mum)))
        assertEquals(emptyList<Person>(), NewFollowers.newOnes(setOf("r", "m"), listOf(reema))) // someone leaving isn't news
    }
}
