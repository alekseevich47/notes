package com.alekseevich47.notes

import com.alekseevich47.notes.stt.ModelLoadSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLoadSlotTest {
    @Test
    fun claimWaitsUntilPublishThenTakesTheValueOnce() {
        val slot = ModelLoadSlot<String>()
        assertTrue(slot.claim() is ModelLoadSlot.Claim.Pending)
        assertTrue(slot.publish("tone"))
        assertFalse(slot.publish("other"))
        val taken = slot.claim() as ModelLoadSlot.Claim.Taken
        assertEquals("tone", taken.value)
        assertNull(slot.close())
    }

    @Test
    fun closeBeforePublishMakesTheLoaderKeepTheValue() {
        val slot = ModelLoadSlot<String>()
        assertNull(slot.close())
        assertFalse(slot.publish("tone"))
    }

    @Test
    fun closeReturnsAValueCaptureNeverClaimed() {
        val slot = ModelLoadSlot<String>()
        assertTrue(slot.publish("tone"))
        assertEquals("tone", slot.close())
        assertNull(slot.close())
    }
}
