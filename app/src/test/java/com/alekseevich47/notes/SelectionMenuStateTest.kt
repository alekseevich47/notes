package com.alekseevich47.notes

import androidx.compose.ui.text.TextRange
import com.alekseevich47.notes.ui.editor.SelectionMenuModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionMenuStateTest {
    @Test
    fun menuAppearsAfterTheLastSelectionChangeSettles() {
        val model = SelectionMenuModel(220L)
        model.onSelectionChanged(TextRange(0), TextRange(1, 4))
        assertEquals(0f, model.target, 0f)
        model.advance(219L)
        assertEquals(0f, model.target, 0f)
        model.onSelectionChanged(TextRange(1, 4), TextRange(1, 6))
        model.advance(438L)
        assertEquals(0f, model.target, 0f)
        model.advance(439L)
        assertEquals(1f, model.target, 0f)
    }

    @Test
    fun scrollSuppressesAndCollapseHidesImmediately() {
        val model = SelectionMenuModel(220L)
        model.onSelectionChanged(TextRange(0), TextRange(2, 8))
        model.advance(220L)
        assertEquals(1f, model.target, 0f)
        model.onScroll(true)
        assertEquals(0f, model.target, 0f)
        model.onScroll(false)
        model.advance(439L)
        assertEquals(0f, model.target, 0f)
        model.advance(440L)
        assertEquals(1f, model.target, 0f)
        model.onSelectionChanged(TextRange(2, 8), TextRange(8))
        assertEquals(0f, model.target, 0f)
        assertFalse(model.wanted)
    }

    @Test
    fun oneEdgeMovingMarksAHandleDrag() {
        val model = SelectionMenuModel(220L)
        model.onSelectionChanged(TextRange(2, 8), TextRange(2, 12))
        assertTrue(model.dragging)
        assertTrue(model.activeIsEnd)
        assertEquals(2, model.anchor)
    }
}
