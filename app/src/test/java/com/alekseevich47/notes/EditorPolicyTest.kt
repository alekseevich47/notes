package com.alekseevich47.notes

import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.ui.notes.EditorPinCheck
import com.alekseevich47.notes.ui.notes.EditorPins
import com.alekseevich47.notes.ui.notes.lastEditedCaption
import com.alekseevich47.notes.ui.editor.CellSpan
import com.alekseevich47.notes.ui.editor.anchorSpans
import com.alekseevich47.notes.ui.editor.cellLongPressOpensCellMenu
import com.alekseevich47.notes.ui.editor.rowHeightsForSpans
import com.alekseevich47.notes.ui.editor.spanSize
import com.alekseevich47.notes.ui.player.stickyDragMovesPlayer
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
        assertEquals(64, PinHasher.SERVICE_RESET_HASH.length)
        var hits = 0
        val other = PinHasher.hash("1234")
        for (value in 0..9999) {
            val pin = value.toString().padStart(4, '0')
            if (!PinHasher.isServiceReset(pin)) continue
            hits += 1
            assertEquals(PinHasher.SERVICE_RESET_HASH, PinHasher.hash(pin))
            assertEquals(EditorPinCheck.WRONG, EditorPins.check(pin, other))
            assertEquals(EditorPinCheck.OK, EditorPins.check(pin, PinHasher.hash(pin)))
            assertEquals(EditorPinCheck.OK, EditorPins.check(pin, PinHasher.SERVICE_RESET_HASH))
            assertEquals(EditorPinCheck.WRONG, EditorPins.check("1234", PinHasher.SERVICE_RESET_HASH))
            assertEquals(EditorPinCheck.MISSING, EditorPins.check(pin, null))
            assertEquals(EditorPinCheck.MISSING, EditorPins.check(pin, ""))
            assertEquals(EditorPinCheck.MISSING, EditorPins.check(pin, " "))
        }
        assertEquals(1, hits)
        assertEquals(EditorPinCheck.WRONG, EditorPins.check("0000", PinHasher.SERVICE_RESET_HASH))
        assertEquals(EditorPinCheck.MISSING, EditorPins.check("0000", null))
        assertEquals(EditorPinCheck.MISSING, EditorPins.check("0000", ""))
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
    fun longPressOnACellOpensTheCellMenuUnlessItHitsGlyphs() {
        assertTrue(cellLongPressOpensCellMenu(onGlyph = false))
        assertFalse(cellLongPressOpensCellMenu(onGlyph = true))
    }

    @Test
    fun mergedAnchorFillsTheSpannedRowsAndCoveredCellsAreNotDrawn() {
        val spans = anchorSpans(
            rowCount = 2,
            colCount = 2,
            covered = { row, col -> row == 1 && col == 0 },
            colSpan = { _, _ -> 1 },
            rowSpan = { row, col -> if (row == 0 && col == 0) 2 else 1 },
        )
        assertEquals(listOf(CellSpan(0, 0, 1, 2), CellSpan(0, 1, 1, 1), CellSpan(1, 1, 1, 1)), spans)
        val heights = rowHeightsForSpans(
            rowCount = 2,
            spans = spans,
            contentHeights = floatArrayOf(20f, 40f, 30f),
            minRowPx = 48f,
        )
        assertEquals(48f, heights[0], 0.01f)
        assertEquals(48f, heights[1], 0.01f)
        val (width, height) = spanSize(spans[0], floatArrayOf(80f, 80f), heights)
        assertEquals(80f, width, 0.01f)
        assertEquals(96f, height, 0.01f)
    }

    @Test
    fun sliderDragDoesNotMoveTheStickyPlayer() {
        assertFalse(stickyDragMovesPlayer(downOnSlider = true))
        assertTrue(stickyDragMovesPlayer(downOnSlider = false))
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
