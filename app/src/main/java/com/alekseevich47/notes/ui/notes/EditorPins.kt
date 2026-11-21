package com.alekseevich47.notes.ui.notes

import com.alekseevich47.notes.data.secure.PinHasher

internal enum class EditorPinCheck { OK, WRONG, MISSING }

/**
 * PIN entry inside a note. The service-reset code is not special here: it
 * succeeds only when it matches the stored hash, and it never clears storage.
 */
internal object EditorPins {
    fun check(pin: String, storedHash: String?): EditorPinCheck {
        if (storedHash.isNullOrBlank()) return EditorPinCheck.MISSING
        if (PinHasher.matches(pin, storedHash)) return EditorPinCheck.OK
        return EditorPinCheck.WRONG
    }
}
