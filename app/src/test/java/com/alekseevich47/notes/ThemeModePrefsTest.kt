package com.alekseevich47.notes

import com.alekseevich47.notes.data.secure.ThemeModePrefs
import com.alekseevich47.notes.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeModePrefsTest {
    @Test
    fun unknownOrMissingValuesFollowTheCurrentSystemTheme() {
        listOf(null, "", "FUTURE", "light", "SYSTEM").forEach { raw ->
            assertEquals(ThemeMode.LIGHT, ThemeModePrefs.decode(raw, systemDark = false))
            assertEquals(ThemeMode.DARK, ThemeModePrefs.decode(raw, systemDark = true))
        }
    }

    @Test
    fun knownNamesIgnoreTheSystemTheme() {
        assertEquals(ThemeMode.LIGHT, ThemeModePrefs.decode("LIGHT", systemDark = false))
        assertEquals(ThemeMode.LIGHT, ThemeModePrefs.decode("LIGHT", systemDark = true))
        assertEquals(ThemeMode.DARK, ThemeModePrefs.decode("DARK", systemDark = false))
        assertEquals(ThemeMode.DARK, ThemeModePrefs.decode("DARK", systemDark = true))
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
