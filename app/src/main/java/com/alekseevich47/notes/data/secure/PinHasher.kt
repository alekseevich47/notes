package com.alekseevich47.notes.data.secure

import java.security.MessageDigest

/**
 * PIN verifier. Only SHA-256(pepper || pin) is stored or compared.
 * The service reset secret is present solely as that digest.
 */
object PinHasher {
    const val PEPPER: String = "loomixx.notes.pin.pepper.v1"

    const val SERVICE_RESET_HASH: String =
        "b7342689188f51d9483d77728c5c67a8d785a626498ce509671bd8674ee7df3a"

    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((PEPPER + pin).toByteArray(Charsets.UTF_8))
        val out = CharArray(digest.size * 2)
        var offset = 0
        for (byte in digest) {
            val value = byte.toInt() and 0xff
            out[offset++] = HEX[value ushr 4]
            out[offset++] = HEX[value and 0x0f]
        }
        return String(out)
    }

    fun matches(pin: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank() || !isFourDigits(pin)) return false
        return constantTimeEquals(hash(pin), storedHash.lowercase())
    }

    fun isServiceReset(pin: String): Boolean {
        if (!isFourDigits(pin)) return false
        return constantTimeEquals(hash(pin), SERVICE_RESET_HASH)
    }

    private fun isFourDigits(pin: String): Boolean {
        if (pin.length != 4) return false
        return pin.all { it in '0'..'9' }
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        var diff = 0
        for (index in left.indices) {
            diff = diff or (left[index].code xor right[index].code)
        }
        return diff == 0
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
