package com.alekseevich47.notes.data.secure

import com.alekseevich47.notes.domain.ThemeMode

internal object ThemeModePrefs {
    fun decode(raw: String?): ThemeMode {
        if (raw.isNullOrEmpty()) return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    /**
     * The plain key wins. The encrypted value is only a one-time source when
     * that key has never been written.
     */
    fun rawToKeep(plainContains: Boolean, plainRaw: String?, legacyRaw: String?): String? {
        return if (plainContains) plainRaw else legacyRaw
    }
}
