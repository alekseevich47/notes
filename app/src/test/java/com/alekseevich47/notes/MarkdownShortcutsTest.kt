package com.alekseevich47.notes

import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.ui.editor.MarkdownShortcuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownShortcutsTest {
    @Test
    fun doubleAsteriskBecomesBold() {
        val edit = MarkdownShortcuts.apply("**hi**", cursor = 6, marks = emptyList())
        assertEquals("hi", edit?.text)
        assertEquals(2, edit?.cursor)
        assertEquals(listOf(TextMark(0, 2, MarkStyle.BOLD)), edit?.marks)
    }

    @Test
    fun doubleUnderscoreBecomesItalic() {
        val edit = MarkdownShortcuts.apply("__hi__", cursor = 6, marks = emptyList())
        assertEquals("hi", edit?.text)
        assertEquals(listOf(TextMark(0, 2, MarkStyle.ITALIC)), edit?.marks)
    }

    @Test
    fun doubleTildeBecomesStrike() {
        val edit = MarkdownShortcuts.apply("~~hi~~", cursor = 6, marks = emptyList())
        assertEquals("hi", edit?.text)
        assertEquals(listOf(TextMark(0, 2, MarkStyle.STRIKE)), edit?.marks)
    }

    @Test
    fun doubleBarBecomesHidden() {
        val edit = MarkdownShortcuts.apply("||hi||", cursor = 6, marks = emptyList())
        assertEquals("hi", edit?.text)
        assertEquals(listOf(TextMark(0, 2, MarkStyle.HIDDEN)), edit?.marks)
    }

    @Test
    fun emptyInsideDoesNotFire() {
        assertNull(MarkdownShortcuts.apply("****", cursor = 4, marks = emptyList()))
        assertNull(MarkdownShortcuts.apply("____", cursor = 4, marks = emptyList()))
        assertNull(MarkdownShortcuts.apply("~~~~", cursor = 4, marks = emptyList()))
        assertNull(MarkdownShortcuts.apply("||||", cursor = 4, marks = emptyList()))
    }

    @Test
    fun leadingOrTrailingSpaceDoesNotFire() {
        assertNull(MarkdownShortcuts.apply("** hi**", cursor = 7, marks = emptyList()))
        assertNull(MarkdownShortcuts.apply("**hi **", cursor = 7, marks = emptyList()))
        assertNull(MarkdownShortcuts.apply("__ hi__", cursor = 7, marks = emptyList()))
        assertNull(MarkdownShortcuts.apply("~~hi ~~", cursor = 7, marks = emptyList()))
    }

    @Test
    fun newlineInsideDoesNotFire() {
        assertNull(MarkdownShortcuts.apply("**h\ni**", cursor = 7, marks = emptyList()))
        assertNull(MarkdownShortcuts.apply("||h\ni||", cursor = 7, marks = emptyList()))
    }

    @Test
    fun markerNotSittingAtTheCaretDoesNotFire() {
        assertNull(MarkdownShortcuts.apply("**hi** more", cursor = 10, marks = emptyList()))
        assertNull(MarkdownShortcuts.apply("say **hi**", cursor = 3, marks = emptyList()))
    }

    @Test
    fun otherMarksShiftWhenTheMarkersAreRemoved() {
        val edit = MarkdownShortcuts.apply(
            text = "x**hi**",
            cursor = 7,
            marks = listOf(TextMark(0, 1, MarkStyle.ITALIC)),
        )
        assertEquals("xhi", edit?.text)
        assertEquals(listOf(TextMark(0, 1, MarkStyle.ITALIC)), edit?.marks?.filter { it.style == MarkStyle.ITALIC })
        assertEquals(listOf(TextMark(1, 3, MarkStyle.BOLD)), edit?.marks?.filter { it.style == MarkStyle.BOLD })
    }
}
