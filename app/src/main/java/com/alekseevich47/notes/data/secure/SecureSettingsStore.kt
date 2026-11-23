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
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = open(context)
    private val _pinHash = MutableStateFlow(prefs.getString(KEY_PIN, null))
    private val _theme = MutableStateFlow(readTheme())
    private val _modelEnabled = MutableStateFlow(readModelFlags())
    private val _warmupOnBoot = MutableStateFlow(prefs.getBoolean(NotesPrefs.WARMUP_ON_BOOT, false))
    private val _recognizer = MutableStateFlow(
        NotesPrefs.recognizerOf(prefs.getString(NotesPrefs.RECOGNIZER, null)),
    )

    val pinHash: StateFlow<String?> = _pinHash.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = _theme.asStateFlow()
    val warmupOnBoot: StateFlow<Boolean> = _warmupOnBoot.asStateFlow()
    val recognizer: StateFlow<String> = _recognizer.asStateFlow()

    init {
        mirrorWidgetPrefs()
    }

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

    /**
     * Failures, the elapsed-realtime deadline, and the boot id they belong to.
     * A failed read of `/proc/sys/kernel/random/boot_id` is ignored, so a wall-clock
     * change on the same boot cannot drop the lock.
     */
    fun loadPinThrottle(nowElapsed: Long): PinThrottleState {
        val stored = PinThrottleState(
            failures = prefs.getInt(KEY_PIN_FAILURES, 0).coerceAtLeast(0),
            lockedUntilElapsed = prefs.getLong(KEY_PIN_LOCKED_UNTIL, 0L),
        )
        val storedBoot = prefs.getString(KEY_PIN_BOOT, "").orEmpty()
        val read = readKernelBootId()
        val aligned = PinThrottle.reconcile(stored, storedBoot, read, nowElapsed)
        val currentBoot = PinThrottle.bootIdOrNull(read)
        if (currentBoot != null && (aligned != stored || storedBoot != currentBoot)) {
            persistPinThrottle(aligned, currentBoot)
        }
        return aligned
    }

    fun savePinThrottle(state: PinThrottleState) {
        val boot = PinThrottle.bootIdOrNull(readKernelBootId())
            ?: prefs.getString(KEY_PIN_BOOT, "").orEmpty()
        persistPinThrottle(state, boot)
    }

    private fun persistPinThrottle(state: PinThrottleState, bootId: String) {
        prefs.edit()
            .putInt(KEY_PIN_FAILURES, state.failures)
            .putLong(KEY_PIN_LOCKED_UNTIL, state.lockedUntilElapsed)
            .putString(KEY_PIN_BOOT, bootId)
            .commit()
    }

    private fun readKernelBootId(): String? {
        return runCatching {
            java.io.File("/proc/sys/kernel/random/boot_id").readText()
        }.getOrNull()
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    fun setWarmupOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(NotesPrefs.WARMUP_ON_BOOT, enabled).commit()
        _warmupOnBoot.value = enabled
        mirrorWidgetPrefs()
    }

    fun setRecognizer(value: String) {
        val stored = NotesPrefs.recognizerOf(value)
        prefs.edit().putString(NotesPrefs.RECOGNIZER, stored).commit()
        _recognizer.value = stored
        mirrorWidgetPrefs()
    }

    private fun mirrorWidgetPrefs() {
        NotesPrefs.open(appContext)
            .edit()
            .putBoolean(NotesPrefs.WARMUP_ON_BOOT, _warmupOnBoot.value)
            .putString(NotesPrefs.RECOGNIZER, _recognizer.value)
            .commit()
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
        const val KEY_PIN_FAILURES = "pin_failures"
        const val KEY_PIN_LOCKED_UNTIL = "pin_locked_until_elapsed"
        const val KEY_PIN_BOOT = "pin_boot_id"
    }
}
