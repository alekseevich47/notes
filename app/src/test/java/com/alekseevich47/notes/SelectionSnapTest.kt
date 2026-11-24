package com.alekseevich47.notes

import androidx.compose.ui.text.TextRange
import com.alekseevich47.notes.ui.editor.SelectAllStep
import com.alekseevich47.notes.ui.editor.autoScrollStep
import com.alekseevich47.notes.ui.editor.selectAllStep
import com.alekseevich47.notes.ui.editor.snapDraggedSelection
import com.alekseevich47.notes.ui.editor.snapOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionSnapTest {
    @Test
    fun draggingIntoAWordJumpsToItsFarEdge() {
        val text = "alpha beta"
        assertEquals(10, snapOffset(text, 5, 7))
        assertEquals(6, snapOffset(text, 10, 8))
        assertEquals(TextRange(0, 10), snapDraggedSelection(text, TextRange(0, 5), TextRange(0, 7)))
    }

    @Test
    fun aSpaceDoesNotPullThePreviousWord() {
        assertEquals(6, snapOffset("alpha beta", 5, 6))
    }

    @Test
    fun aNewSelectionAndACollapsedRangeStayPut() {
        val text = "alpha"
        assertEquals(TextRange(0, 5), snapDraggedSelection(text, TextRange(0), TextRange(0, 5)))
        assertEquals(TextRange(3), snapDraggedSelection(text, TextRange(0, 5), TextRange(3)))
        assertEquals(TextRange(1, 4), snapDraggedSelection(text, TextRange(0, 5), TextRange(1, 4)))
    }

    @Test
    fun hyphenatedWordIsOneJump() {
        assertEquals(9, snapOffset("по-русски", 0, 3))
    }

    @Test
    fun selectAllEscalatesOnlyWhenTheBlockIsCovered() {
        assertEquals(SelectAllStep.BLOCK, selectAllStep(TextRange(1, 4), 10))
        assertEquals(SelectAllStep.DOCUMENT, selectAllStep(TextRange(0, 10), 10))
        assertEquals(SelectAllStep.BLOCK, selectAllStep(TextRange(0), 0))
    }

    @Test
    fun autoScrollStepsHalfALineTowardTheEdge() {
        assertEquals(10f, autoScrollStep(20f, 1f), 0f)
        assertEquals(-10f, autoScrollStep(20f, -3f), 0f)
        assertEquals(0f, autoScrollStep(20f, 0f), 0f)
        assertEquals(0f, autoScrollStep(0f, 1f), 0f)
    }
}
