package com.alekseevich47.notes

import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.ui.editor.HiddenText
import org.junit.Assert.assertEquals
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

    @Test
    fun spoilerIsOffsetCopiesNotAPlate() {
        val offsets = HiddenText.shadowOffsetsDp
        assertTrue(offsets.size >= 8)
        assertEquals(offsets.size, HiddenText.shadowAlphas.size)
        assertTrue(offsets.any { it.first > 0f })
        assertTrue(offsets.any { it.first < 0f })
        assertTrue(offsets.any { it.second > 0f })
        assertTrue(offsets.any { it.second < 0f })
        assertTrue(HiddenText.shadowAlphas.all { it in 0.04f..0.45f })
    }
}
