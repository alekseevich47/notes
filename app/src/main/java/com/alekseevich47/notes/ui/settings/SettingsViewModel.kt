package com.alekseevich47.notes.ui.settings

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.alekseevich47.notes.data.download.ModelCardState
import com.alekseevich47.notes.data.download.ModelDownloadManager
import com.alekseevich47.notes.data.download.ModelKind
import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.data.secure.PinThrottle
import com.alekseevich47.notes.data.secure.SecureSettingsStore
import com.alekseevich47.notes.domain.ThemeMode
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.service.ModelWarmupService
import com.alekseevich47.notes.service.OemPowerSettings
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
        if (enabled) {
            ModelWarmupService.start(context)
            OemPowerSettings.open(context)
        } else {
            ModelWarmupService.stop(context)
        }
    }

    /** Opens battery or OEM autostart. Does not change the switch. */
    fun openWarmupPowerSettings() {
        OemPowerSettings.open(context)
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

    /**
     * Set only after [verifyCurrentPin] sees [PinHasher.matches].
     * A typo on the repeat step leaves it set; save, reset, and dismiss clear it.
     */
    private var pinVerified = false

    fun verifyCurrentPin(pin: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val throttle = settings.loadPinThrottle(now)
        if (PinThrottle.isLocked(throttle, now)) {
            pinVerified = false
            return false
        }
        pinVerified = PinHasher.matches(pin, settings.pinHash.value)
        settings.savePinThrottle(
            if (pinVerified) PinThrottle.recordCorrect(throttle)
            else PinThrottle.recordWrong(throttle, now),
        )
        if (!pinVerified) AppLog.i(TAG, "pin change rejected")
        return pinVerified
    }

    fun pinLockDeadline(): Long {
        val now = SystemClock.elapsedRealtime()
        return settings.loadPinThrottle(now).lockedUntilElapsed
    }

    fun clearPinVerified() {
        pinVerified = false
    }

    fun submitNewPin(first: String, second: String): PinChange {
        val decision = decidePinChange(
            hasExistingPin = !settings.pinHash.value.isNullOrBlank(),
            verified = pinVerified,
            firstIsServiceReset = PinHasher.isServiceReset(first),
            secondIsServiceReset = PinHasher.isServiceReset(second),
            pinsMatch = first == second,
        )
        if (decision.clearVerification) pinVerified = false
        return when (decision.result) {
            PinChangeResult.REJECTED -> {
                AppLog.i(TAG, "pin change rejected")
                PinChange.REJECTED
            }
            PinChangeResult.RESET -> {
                settings.setPinHash(null)
                AppLog.i(TAG, "pin cleared service-reset")
                PinChange.RESET
            }
            PinChangeResult.MISMATCH -> {
                AppLog.i(TAG, "pin rejected mismatch")
                PinChange.MISMATCH
            }
            PinChangeResult.SAVE -> {
                settings.setPinHash(PinHasher.hash(first))
                AppLog.i(TAG, "pin set")
                PinChange.SAVED
            }
        }
    }

    fun clearWith(pin: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val throttle = settings.loadPinThrottle(now)
        if (PinThrottle.isLocked(throttle, now)) return false
        if (PinHasher.isServiceReset(pin)) {
            settings.setPinHash(null)
            settings.savePinThrottle(PinThrottle.recordCorrect(throttle))
            AppLog.i(TAG, "pin cleared service-reset")
            return true
        }
        if (PinHasher.matches(pin, settings.pinHash.value)) {
            settings.setPinHash(null)
            settings.savePinThrottle(PinThrottle.recordCorrect(throttle))
            AppLog.i(TAG, "pin cleared")
            return true
        }
        settings.savePinThrottle(PinThrottle.recordWrong(throttle, now))
        AppLog.i(TAG, "pin clear rejected")
        return false
    }

    enum class PinChange { SAVED, MISMATCH, RESET, REJECTED }

    private companion object {
        const val TAG = "Settings"
    }
}
