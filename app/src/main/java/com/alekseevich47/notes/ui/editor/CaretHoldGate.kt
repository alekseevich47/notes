package com.alekseevich47.notes.ui.editor

/**
 * Hides the caret for one double-tap window after a short tap.
 * A second tap extends the hold. A double tap releases it.
 */
internal class CaretHoldGate(private val delayMillis: Long) {
    private var held: Boolean = false
    private var until: Long = Long.MIN_VALUE

    fun onTap(now: Long) {
        held = true
        until = now + delayMillis
    }

    fun onDoubleTap() {
        held = false
        until = Long.MIN_VALUE
    }

    fun isHeld(now: Long): Boolean {
        if (!held) return false
        if (now >= until) {
            held = false
            return false
        }
        return true
    }
}
