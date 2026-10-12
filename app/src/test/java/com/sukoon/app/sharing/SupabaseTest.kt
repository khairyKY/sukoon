package com.sukoon.app.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SupabaseTest {

    @Test
    fun `the PKCE challenge matches RFC 7636's example`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Supabase.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun `verifiers are long enough and never repeat`() {
        val a = Supabase.verifier()
        assertEquals(43, a.length) // 32 bytes, base64url without padding
        assertNotEquals(a, Supabase.verifier())
    }
}
