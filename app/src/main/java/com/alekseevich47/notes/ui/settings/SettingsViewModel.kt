package com.alekseevich47.notes.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.alekseevich47.notes.data.download.ModelCardState
import com.alekseevich47.notes.data.download.ModelDownloadManager
import com.alekseevich47.notes.data.download.ModelKind
import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.data.secure.SecureSettingsStore
import com.alekseevich47.notes.domain.ThemeMode
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.service.ModelWarmupService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SecureSettingsStore,
    private val downloads: ModelDownloadManager,
) : ViewModel() {
    val theme: StateFlow<ThemeMode> = settings.themeMode
    val pinHash: StateFlow<String?> = settings.pinHash
    val models: StateFlow<List<ModelCardState>> = downloads.cards
    val modelEnabled: StateFlow<Map<ModelKind, Boolean>> = settings.modelEnabled
    val warmupOnBoot: StateFlow<Boolean> = settings.warmupOnBoot
    val recognizer: StateFlow<String> = settings.recognizer

    fun setTheme(mode: ThemeMode) {
        val previous = theme.value
        settings.setTheme(mode)
        AppLog.i(TAG, "theme $previous -> $mode")
    }

    fun setWarmupOnBoot(enabled: Boolean) {
        settings.setWarmupOnBoot(enabled)
        AppLog.i(TAG, if (enabled) "warmup on" else "warmup off")
        if (enabled) ModelWarmupService.start(context) else ModelWarmupService.stop(context)
    }

    fun setRecognizer(value: String) {
        settings.setRecognizer(value)
        AppLog.i(TAG, "recognizer ${settings.recognizer.value}")
    }

    fun startDownload(kind: ModelKind) = downloads.start(kind)
    fun pauseDownload(kind: ModelKind) = downloads.pause(kind)
    fun stopDownload(kind: ModelKind) = downloads.stop(kind)

    fun setModelEnabled(kind: ModelKind, enabled: Boolean) {
        settings.setModelEnabled(kind, enabled)
        AppLog.i(TAG, if (enabled) "model enable $kind" else "model disable $kind")
    }

    fun deleteModel(kind: ModelKind) {
        downloads.deleteInstalled(kind)
        settings.clearModelEnabled(kind)
    }

    fun submitNewPin(first: String, second: String): PinChange {
        if (PinHasher.isServiceReset(first) || PinHasher.isServiceReset(second)) {
            settings.setPinHash(null)
            AppLog.i(TAG, "pin cleared service-reset")
            return PinChange.RESET
        }
        if (first != second) {
            AppLog.i(TAG, "pin rejected mismatch")
            return PinChange.MISMATCH
        }
        settings.setPinHash(PinHasher.hash(first))
        AppLog.i(TAG, "pin set")
        return PinChange.SAVED
    }

    fun clearWith(pin: String): Boolean {
        if (PinHasher.isServiceReset(pin)) {
            settings.setPinHash(null)
            AppLog.i(TAG, "pin cleared service-reset")
            return true
        }
        if (PinHasher.matches(pin, settings.pinHash.value)) {
            settings.setPinHash(null)
            AppLog.i(TAG, "pin cleared")
            return true
        }
        AppLog.i(TAG, "pin clear rejected")
        return false
    }

    enum class PinChange { SAVED, MISMATCH, RESET }

    private companion object {
        const val TAG = "Settings"
    }
}
