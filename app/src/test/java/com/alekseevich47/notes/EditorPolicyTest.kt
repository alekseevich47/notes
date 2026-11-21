package com.alekseevich47.notes

import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.ui.notes.EditorPinCheck
import com.alekseevich47.notes.ui.notes.EditorPins
import com.alekseevich47.notes.ui.notes.lastEditedCaption
import com.alekseevich47.notes.ui.player.stickyShouldDismiss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class EditorPolicyTest {
    @Test
    fun serviceResetCodeIsWrongUnlessItMatchesTheStoredHash() {
        val reset = "4757"
        assertTrue(PinHasher.isServiceReset(reset))
        val other = PinHasher.hash("1234")
        assertEquals(EditorPinCheck.WRONG, EditorPins.check(reset, other))
        assertEquals(EditorPinCheck.OK, EditorPins.check(reset, PinHasher.hash(reset)))
        assertEquals(EditorPinCheck.WRONG, EditorPins.check("1234", PinHasher.hash(reset)))
        assertEquals(EditorPinCheck.MISSING, EditorPins.check(reset, null))
        assertEquals(EditorPinCheck.MISSING, EditorPins.check(reset, ""))
    }

    @Test
    fun editedLineUsesDeviceZoneWithoutSeconds() {
        val created = 1_700_000_000_000L
        val updated = created + 3_600_000L
        val line = lastEditedCaption(created, updated, ZoneOffset.UTC)
        assertEquals("последнее изменение: 14.11.2023 23:13", line)
        assertNull(lastEditedCaption(created, created, ZoneOffset.UTC))
        assertNull(lastEditedCaption(created, created + 500L, ZoneOffset.UTC))
    }

    @Test
    fun stickyPlayerDismissesOnlyPastThirtyPercent() {
        assertFalse(stickyShouldDismiss(30f, 100f))
        assertFalse(stickyShouldDismiss(-30f, 100f))
        assertTrue(stickyShouldDismiss(30.1f, 100f))
        assertTrue(stickyShouldDismiss(-40f, 100f))
        assertFalse(stickyShouldDismiss(0f, 100f))
        assertFalse(stickyShouldDismiss(50f, 0f))
    }
}
