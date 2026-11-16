package com.alekseevich47.notes.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alekseevich47.notes.data.download.ModelKind
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
    private val _modelEnabled = MutableStateFlow(readModelFlags())

    val pinHash: StateFlow<String?> = _pinHash.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = _theme.asStateFlow()

    /** Explicit switches only. A missing entry means the installed model is on. */
    val modelEnabled: StateFlow<Map<ModelKind, Boolean>> = _modelEnabled.asStateFlow()

    val hasPin: Boolean get() = !_pinHash.value.isNullOrBlank()

    fun isModelEnabled(kind: ModelKind): Boolean = _modelEnabled.value[kind] ?: true

    fun setModelEnabled(kind: ModelKind, enabled: Boolean) {
        prefs.edit().putBoolean(modelKey(kind), enabled).apply()
        _modelEnabled.value = _modelEnabled.value + (kind to enabled)
    }

    fun clearModelEnabled(kind: ModelKind) {
        prefs.edit().remove(modelKey(kind)).apply()
        _modelEnabled.value = _modelEnabled.value - kind
    }

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

    private fun readModelFlags(): Map<ModelKind, Boolean> {
        val stored = linkedMapOf<ModelKind, Boolean>()
        ModelKind.entries.forEach { kind ->
            val key = modelKey(kind)
            if (prefs.contains(key)) stored[kind] = prefs.getBoolean(key, true)
        }
        return stored
    }

    private fun modelKey(kind: ModelKind) = "model_enabled_${kind.name}"

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
