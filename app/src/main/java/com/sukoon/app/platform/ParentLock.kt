package com.sukoon.app.platform

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The parent lock: a 4–8 digit PIN guarding the alarms, the target range and dose suggestions, so a
 * child can't switch a low alarm off or change a ratio. Kept as a salted SHA-256, never the PIN itself.
 * ponytail: a lock against changes on the phone, not encryption; clearing the app's data removes it.
 */
object ParentLock {

    fun valid(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }

    /** "salt:hash", what's stored. */
    fun encode(pin: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String =
        Base64.getEncoder().encodeToString(salt) + ":" + hash(salt, pin)

    fun matches(pin: String, stored: String?): Boolean {
        val (salt, expected) = stored?.split(':')?.takeIf { it.size == 2 } ?: return false
        val bytes = runCatching { Base64.getDecoder().decode(salt) }.getOrNull() ?: return false
        return MessageDigest.isEqual(hash(bytes, pin).toByteArray(), expected.toByteArray())
    }

    private fun hash(salt: ByteArray, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray()).joinToString("") { "%02x".format(it) }
}
