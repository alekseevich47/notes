package com.alekseevich47.notes.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alekseevich47.notes.domain.AppSettings
import com.alekseevich47.notes.domain.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = createPrefs(context)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun createPrefs(context: Context): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            context.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    private fun read(): AppSettings = AppSettings(
        themeMode = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM) }
            ?: ThemeMode.SYSTEM,
        pinHash = prefs.getString(KEY_PIN, null),
        gigaFinalizeEnabled = prefs.getBoolean(KEY_GIGA, true),
        tOneInstalled = prefs.getBoolean(KEY_TONE_OK, false),
        gigaInstalled = prefs.getBoolean(KEY_GIGA_OK, false),
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_THEME, next.themeMode.name)
            .putString(KEY_PIN, next.pinHash)
            .putBoolean(KEY_GIGA, next.gigaFinalizeEnabled)
            .putBoolean(KEY_TONE_OK, next.tOneInstalled)
            .putBoolean(KEY_GIGA_OK, next.gigaInstalled)
            .apply()
        _settings.value = next
    }

    companion object {
        private const val PREFS_NAME = "notes_secure_prefs"
        private const val PREFS_FALLBACK = "notes_secure_prefs_fallback"
        private const val KEY_THEME = "theme"
        private const val KEY_PIN = "pin_hash"
        private const val KEY_GIGA = "giga_finalize"
        private const val KEY_TONE_OK = "tone_installed"
        private const val KEY_GIGA_OK = "giga_installed"
    }
}
