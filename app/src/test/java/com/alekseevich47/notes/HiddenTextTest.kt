package com.alekseevich47.notes

import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.ui.editor.HiddenText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenTextTest {
    private val hidden = listOf(TextMark(start = 2, end = 7, style = MarkStyle.HIDDEN))

    @Test
    fun insertInsideTheSpanIsBlocked() {
        assertTrue(HiddenText.editTouches(hidden, "xxsecret", "xxseXcret"))
    }

    @Test
    fun insertBeforeOrAfterIsAllowed() {
        assertFalse(HiddenText.editTouches(hidden, "xxsecret", "Axxsecret"))
        assertFalse(HiddenText.editTouches(hidden, "xxsecret", "xxsecret!"))
    }

    @Test
    fun deleteInsideTheSpanIsBlocked() {
        assertTrue(HiddenText.editTouches(hidden, "xxsecret", "xxecret"))
    }

    @Test
    fun cursorTapInsideOpensTheLock() {
        assertTrue(HiddenText.cursorInside(hidden, start = 4, end = 4))
        assertFalse(HiddenText.cursorInside(hidden, start = 7, end = 7))
        assertFalse(HiddenText.cursorInside(hidden, start = 1, end = 1))
        assertTrue(HiddenText.cursorInside(hidden, start = 1, end = 4))
    }
}
