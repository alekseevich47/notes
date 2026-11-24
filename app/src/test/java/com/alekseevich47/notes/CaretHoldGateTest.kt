package com.alekseevich47.notes

import com.alekseevich47.notes.ui.editor.CaretHoldGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaretHoldGateTest {
    @Test
    fun tapHoldsUntilTheDelayAndASecondTapExtendsIt() {
        val gate = CaretHoldGate(100L)
        gate.onTap(0L)
        assertTrue(gate.isHeld(99L))
        assertFalse(gate.isHeld(100L))
        gate.onTap(200L)
        assertTrue(gate.isHeld(250L))
        gate.onTap(260L)
        assertTrue(gate.isHeld(359L))
        assertFalse(gate.isHeld(360L))
    }

    @Test
    fun doubleTapReleasesImmediately() {
        val gate = CaretHoldGate(100L)
        gate.onTap(0L)
        gate.onDoubleTap()
        assertFalse(gate.isHeld(1L))
    }
}
