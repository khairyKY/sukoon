package com.sukoon.app.data.source.libre

import org.junit.Assert.assertThrows
import org.junit.Test

class LibreKeyDerivationTest {

    @Test
    fun `unimplemented derivation fails loudly rather than returning a fake key`() {
        assertThrows(NotImplementedError::class.java) {
            UnimplementedLibreKeyDerivation.deriveKey(ByteArray(8), ByteArray(6))
        }
    }
}
