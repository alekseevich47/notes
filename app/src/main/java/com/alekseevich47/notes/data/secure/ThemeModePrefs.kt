package com.alekseevich47.notes.data.secure

import com.alekseevich47.notes.domain.ThemeMode

internal object ThemeModePrefs {
    fun decode(raw: String?, systemDark: Boolean): ThemeMode {
        return when (raw) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT
        }
    }

    /**
     * The plain key wins. The encrypted value is only a one-time source when
     * that key has never been written.
     */
    fun rawToKeep(plainContains: Boolean, plainRaw: String?, legacyRaw: String?): String? {
        return if (plainContains) plainRaw else legacyRaw
    }
}
