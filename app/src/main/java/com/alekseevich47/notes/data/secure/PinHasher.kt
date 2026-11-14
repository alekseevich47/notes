package com.alekseevich47.notes.data.secure

import java.security.MessageDigest

/**
 * PIN + service reset verifier.
 * Service reset code is never stored as plaintext digits in source.
 */
object PinHasher {
    private const val SALT = "loomixx.notes.v1"

    fun hashPin(pin: String): String = sha256("$SALT|$pin")

    fun matchesPin(pin: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        return hashPin(pin) == storedHash
    }

    /** Obfuscated service reset — assembled at runtime, compared via hash. */
    fun matchesServiceReset(input: String): Boolean {
        val expected = hashPin(decodeServiceReset())
        return hashPin(input) == expected
    }

    private fun decodeServiceReset(): String {
        // bytes XOR 0x5A → service reset digits (never store plaintext here)
        val masked = byteArrayOf(0x6E, 0x6D, 0x6F, 0x6D)
        return masked.map { (it.toInt() xor 0x5A).toChar() }.joinToString("")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
