package com.alekseevich47.notes.ui.settings

import androidx.lifecycle.ViewModel
import com.alekseevich47.notes.data.download.ModelCardState
import com.alekseevich47.notes.data.download.ModelDownloadManager
import com.alekseevich47.notes.data.download.ModelKind
import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.data.secure.SecureSettingsStore
import com.alekseevich47.notes.domain.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SecureSettingsStore,
    private val downloads: ModelDownloadManager,
) : ViewModel() {
    val theme: StateFlow<ThemeMode> = settings.themeMode
    val pinHash: StateFlow<String?> = settings.pinHash
    val models: StateFlow<List<ModelCardState>> = downloads.cards

    fun setTheme(mode: ThemeMode) = settings.setTheme(mode)

    fun startDownload(kind: ModelKind) = downloads.start(kind)
    fun pauseDownload(kind: ModelKind) = downloads.pause(kind)
    fun stopDownload(kind: ModelKind) = downloads.stop(kind)
    fun deleteModel(kind: ModelKind) = downloads.deleteInstalled(kind)

    fun submitNewPin(first: String, second: String): PinChange {
        if (PinHasher.isServiceReset(first) || PinHasher.isServiceReset(second)) {
            settings.setPinHash(null)
            return PinChange.RESET
        }
        if (first != second) return PinChange.MISMATCH
        settings.setPinHash(PinHasher.hash(first))
        return PinChange.SAVED
    }

    fun clearWith(pin: String): Boolean {
        if (PinHasher.isServiceReset(pin)) {
            settings.setPinHash(null)
            return true
        }
        if (PinHasher.matches(pin, settings.pinHash.value)) {
            settings.setPinHash(null)
            return true
        }
        return false
    }

    enum class PinChange { SAVED, MISMATCH, RESET }
}
