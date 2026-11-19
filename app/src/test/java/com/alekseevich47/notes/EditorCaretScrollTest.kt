package com.alekseevich47.notes

import com.alekseevich47.notes.ui.notes.inferCaretAfterEdit
import com.alekseevich47.notes.ui.notes.revealCaretDelta
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorCaretScrollTest {
    @Test
    fun typingAndPasteLeaveTheCaretAtTheEndOfTheInsertion() {
        assertEquals(6, inferCaretAfterEdit("hello", "hello!"))
        assertEquals(3, inferCaretAfterEdit("hlo", "hello"))
        assertEquals(3, inferCaretAfterEdit("hello", "he\nllo"))
        assertEquals(4, inferCaretAfterEdit("hello", "hell"))
        assertEquals(0, inferCaretAfterEdit("hello", ""))
    }

    @Test
    fun aLongTailRewriteKeepsTheCaretAtTheStartOfTheChange() {
        val before = "1. A\n" + (2..12).joinToString("\n") { "$it. line" }
        val after = "1. A\n2. \n" + (3..13).joinToString("\n") { "$it. line" }
        // The new item is "2. " and the old second line also began with "2. ", so the
        // caret sits just after that marker, not at the bottom of the renumbered tail.
        assertEquals("1. A\n2. ".length, inferCaretAfterEdit(before, after))
    }

    @Test
    fun revealOnlyMovesDownPastTheViewport() {
        assertEquals(0, revealCaretDelta(scrollValue = 0, viewport = 400, caretBottom = 80, marginPx = 16))
        assertEquals(56, revealCaretDelta(scrollValue = 0, viewport = 400, caretBottom = 440, marginPx = 16))
        assertEquals(0, revealCaretDelta(scrollValue = 300, viewport = 400, caretBottom = 40, marginPx = 16))
        assertEquals(0, revealCaretDelta(scrollValue = 0, viewport = 0, caretBottom = 80, marginPx = 16))
    }
}
