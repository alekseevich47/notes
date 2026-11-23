package com.alekseevich47.notes

import com.alekseevich47.notes.data.secure.ThemeModePrefs
import com.alekseevich47.notes.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeModePrefsTest {
    @Test
    fun unknownOrMissingValuesFallBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeModePrefs.decode(null))
        assertEquals(ThemeMode.SYSTEM, ThemeModePrefs.decode(""))
        assertEquals(ThemeMode.SYSTEM, ThemeModePrefs.decode("FUTURE"))
        assertEquals(ThemeMode.SYSTEM, ThemeModePrefs.decode("light"))
    }

    @Test
    fun knownNamesDecode() {
        assertEquals(ThemeMode.SYSTEM, ThemeModePrefs.decode("SYSTEM"))
        assertEquals(ThemeMode.LIGHT, ThemeModePrefs.decode("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeModePrefs.decode("DARK"))
    }

    @Test
    fun plainKeyHidesTheEncryptedTheme() {
        assertEquals(
            "DARK",
            ThemeModePrefs.rawToKeep(plainContains = true, plainRaw = "DARK", legacyRaw = "LIGHT"),
        )
        assertEquals(
            "NOPE",
            ThemeModePrefs.rawToKeep(plainContains = true, plainRaw = "NOPE", legacyRaw = "LIGHT"),
        )
        assertNull(ThemeModePrefs.rawToKeep(plainContains = true, plainRaw = null, legacyRaw = "LIGHT"))
    }

    @Test
    fun missingPlainKeyCopiesTheEncryptedThemeOnce() {
        assertEquals(
            "DARK",
            ThemeModePrefs.rawToKeep(plainContains = false, plainRaw = null, legacyRaw = "DARK"),
        )
        assertNull(ThemeModePrefs.rawToKeep(plainContains = false, plainRaw = "LIGHT", legacyRaw = null))
    }
}
