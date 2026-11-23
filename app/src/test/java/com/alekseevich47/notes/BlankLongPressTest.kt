package com.alekseevich47.notes

import com.alekseevich47.notes.ui.editor.isBlankLongPress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankLongPressTest {
    @Test
    fun spaceEndOfLineAndEmptyNoteAreBlank() {
        val text = "hello world"
        val space = text.indexOf(' ')
        assertTrue(isBlankLongPress(text, space, onGlyph = true))
        assertTrue(isBlankLongPress(text, text.length, onGlyph = false))
        assertTrue(isBlankLongPress("", 0, onGlyph = false))
        assertTrue(isBlankLongPress("", 0, onGlyph = true))
        val punctuated = "hello,"
        assertTrue(isBlankLongPress(punctuated, punctuated.indexOf(','), onGlyph = true))
    }

    @Test
    fun aLetterIsNotBlank() {
        assertFalse(isBlankLongPress("hello", 2, onGlyph = true))
        assertFalse(isBlankLongPress("a1", 1, onGlyph = true))
    }
}
