package com.alekseevich47.notes

import com.alekseevich47.notes.ui.settings.PinChangeResult
import com.alekseevich47.notes.ui.settings.decidePinChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinChangePolicyTest {
    @Test
    fun existingPinIsRejectedUntilTheCurrentOneMatched() {
        val denied = decidePinChange(
            hasExistingPin = true,
            verified = false,
            firstIsServiceReset = false,
            secondIsServiceReset = false,
            pinsMatch = true,
        )
        assertEquals(PinChangeResult.REJECTED, denied.result)
        assertFalse(denied.clearVerification)

        val allowed = decidePinChange(
            hasExistingPin = true,
            verified = true,
            firstIsServiceReset = false,
            secondIsServiceReset = false,
            pinsMatch = true,
        )
        assertEquals(PinChangeResult.SAVE, allowed.result)
        assertTrue(allowed.clearVerification)
    }

    @Test
    fun serviceResetDoesNotAuthorizeReplacingThePin() {
        val decision = decidePinChange(
            hasExistingPin = true,
            verified = false,
            firstIsServiceReset = true,
            secondIsServiceReset = true,
            pinsMatch = true,
        )
        assertEquals(PinChangeResult.REJECTED, decision.result)
        assertFalse(decision.clearVerification)
    }

    @Test
    fun mismatchKeepsTheCurrentPinCheck() {
        val decision = decidePinChange(
            hasExistingPin = true,
            verified = true,
            firstIsServiceReset = false,
            secondIsServiceReset = false,
            pinsMatch = false,
        )
        assertEquals(PinChangeResult.MISMATCH, decision.result)
        assertFalse(decision.clearVerification)
    }

    @Test
    fun firstPinDoesNotAskForACurrentOne() {
        val decision = decidePinChange(
            hasExistingPin = false,
            verified = false,
            firstIsServiceReset = false,
            secondIsServiceReset = false,
            pinsMatch = true,
        )
        assertEquals(PinChangeResult.SAVE, decision.result)
        assertTrue(decision.clearVerification)
    }

    @Test
    fun serviceResetStillClearsAfterARealCheckOrWhenNoPinExists() {
        val verified = decidePinChange(
            hasExistingPin = true,
            verified = true,
            firstIsServiceReset = true,
            secondIsServiceReset = false,
            pinsMatch = false,
        )
        assertEquals(PinChangeResult.RESET, verified.result)
        assertTrue(verified.clearVerification)

        val absent = decidePinChange(
            hasExistingPin = false,
            verified = false,
            firstIsServiceReset = false,
            secondIsServiceReset = true,
            pinsMatch = false,
        )
        assertEquals(PinChangeResult.RESET, absent.result)
        assertTrue(absent.clearVerification)
    }
}
