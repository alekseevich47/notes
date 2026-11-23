package com.alekseevich47.notes.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.alekseevich47.notes.domain.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Theme is not a secret. The activity reads this store and does not open the keystore.
 * An older install is copied out of the encrypted file once, and only when the plain key is absent.
 */
@Singleton
class ThemeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = NotesPrefs.open(context)
    private val _theme = MutableStateFlow(load(context))
    val themeMode: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(NotesPrefs.THEME_MODE, mode.name).apply()
        _theme.value = mode
    }

    private fun load(context: Context): ThemeMode {
        val plainContains = prefs.contains(NotesPrefs.THEME_MODE)
        val plainRaw = if (plainContains) prefs.getString(NotesPrefs.THEME_MODE, null) else null
        val legacyRaw = if (plainContains) null else readLegacyThemeName(context)
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val decoded = ThemeModePrefs.decode(
            ThemeModePrefs.rawToKeep(plainContains, plainRaw, legacyRaw),
            systemDark,
        )
        if (!plainContains) {
            prefs.edit().putString(NotesPrefs.THEME_MODE, decoded.name).commit()
        }
        if (plainRaw == "SYSTEM") {
            prefs.edit().putString(NotesPrefs.THEME_MODE, decoded.name).commit()
        }
        return decoded
    }
}
