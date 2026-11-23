package com.alekseevich47.notes.ui.settings

internal enum class PinChangeResult { SAVE, MISMATCH, RESET, REJECTED }

/**
 * Whether [SettingsViewModel.submitNewPin] may write.
 * A service-reset value never counts as proof of the current PIN.
 * A mismatch of the two new values keeps [clearVerification] false so the
 * caller can leave the current-PIN check in place.
 */
internal data class PinChangeDecision(
    val result: PinChangeResult,
    val clearVerification: Boolean,
)

internal fun decidePinChange(
    hasExistingPin: Boolean,
    verified: Boolean,
    firstIsServiceReset: Boolean,
    secondIsServiceReset: Boolean,
    pinsMatch: Boolean,
): PinChangeDecision {
    if (hasExistingPin && !verified) {
        return PinChangeDecision(PinChangeResult.REJECTED, clearVerification = false)
    }
    if (firstIsServiceReset || secondIsServiceReset) {
        return PinChangeDecision(PinChangeResult.RESET, clearVerification = true)
    }
    if (!pinsMatch) {
        return PinChangeDecision(PinChangeResult.MISMATCH, clearVerification = false)
    }
    return PinChangeDecision(PinChangeResult.SAVE, clearVerification = true)
}
