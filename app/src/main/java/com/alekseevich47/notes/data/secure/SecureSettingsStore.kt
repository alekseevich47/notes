package com.alekseevich47.notes.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alekseevich47.notes.domain.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = open(context)
    private val _pinHash = MutableStateFlow(prefs.getString(KEY_PIN, null))
    private val _theme = MutableStateFlow(readTheme())

    val pinHash: StateFlow<String?> = _pinHash.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = _theme.asStateFlow()

    val hasPin: Boolean get() = !_pinHash.value.isNullOrBlank()

    fun setPinHash(hash: String?) {
        prefs.edit().putString(KEY_PIN, hash).apply()
        _pinHash.value = hash
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    private fun readTheme(): ThemeMode {
        val raw = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(raw ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    private fun open(context: Context): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            context.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    private companion object {
        const val PREFS = "notes_secure"
        const val PREFS_FALLBACK = "notes_secure_fallback"
        const val KEY_PIN = "pin_hash"
        const val KEY_THEME = "theme_mode"
    }
}
