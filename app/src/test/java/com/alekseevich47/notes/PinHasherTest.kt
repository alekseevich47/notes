package com.alekseevich47.notes

import com.alekseevich47.notes.data.secure.PinHasher
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinHasherTest {
    @Test
    fun pinRoundTrip() {
        val hash = PinHasher.hashPin("1234")
        assertThat(PinHasher.matchesPin("1234", hash)).isTrue()
        assertThat(PinHasher.matchesPin("0000", hash)).isFalse()
    }

    @Test
    fun serviceResetMatchesObfuscatedValue() {
        assertThat(PinHasher.matchesServiceReset("4757")).isTrue()
        assertThat(PinHasher.matchesServiceReset("0000")).isFalse()
    }

    @Test
    fun sourceDoesNotContainPlainServiceReset() {
        // Guardrail: the literal digits must not appear as a contiguous string in PinHasher.kt
        val source = java.io.File("src/main/java/com/alekseevich47/notes/data/secure/PinHasher.kt").readText()
        assertThat(source).doesNotContain("4757")
    }
}
