package com.alekseevich47.notes

import com.alekseevich47.notes.data.secure.PinHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun hashIsSha256HexAndNotThePin() {
        val hash = PinHasher.hash("1234")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals("1234", hash)
        assertEquals(hash, PinHasher.hash("1234"))
        assertNotEquals(hash, PinHasher.hash("1235"))
    }

    @Test
    fun matchesStoredDigestOnly() {
        val stored = PinHasher.hash("2468")
        assertTrue(PinHasher.matches("2468", stored))
        assertTrue(PinHasher.matches("2468", stored.uppercase()))
        assertFalse(PinHasher.matches("2469", stored))
        assertFalse(PinHasher.matches("2468", null))
        assertFalse(PinHasher.matches("2468", ""))
        assertFalse(PinHasher.matches("12", stored))
        assertFalse(PinHasher.matches("12ab", stored))
    }

    @Test
    fun serviceResetMatchesExactlyOneFourDigitSecret() {
        var hits = 0
        for (value in 0..9999) {
            val pin = value.toString().padStart(4, '0')
            if (PinHasher.isServiceReset(pin)) hits += 1
        }
        assertEquals(1, hits)
        assertFalse(PinHasher.isServiceReset("0000"))
        assertFalse(PinHasher.isServiceReset("12"))
        assertEquals(64, PinHasher.SERVICE_RESET_HASH.length)
    }

    @Test
    fun pepperIsMixedIntoTheDigest() {
        assertTrue(PinHasher.PEPPER.isNotBlank())
        assertNotEquals(PinHasher.hash("1111"), sha256WithoutPepper("1111"))
    }

    private fun sha256WithoutPepper(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
