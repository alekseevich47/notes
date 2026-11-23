package com.alekseevich47.notes

import androidx.compose.ui.graphics.Color
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.ui.editor.HiddenText
import com.alekseevich47.notes.ui.editor.spoilerAnnotated
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
    fun spoilerBlurIsWideEnoughToMeltGlyphs() {
        assertTrue(HiddenText.BLUR_EM >= 0.4f)
    }

    @Test
    fun spoilerInkColorsOnlyTheHiddenSpan() {
        val annotated = spoilerAnnotated(
            "Secret prase testh",
            listOf(TextMark(7, 12, MarkStyle.HIDDEN)),
            Color.Black,
        )
        val colors = annotated.spanStyles.filter { it.item.color == Color.Black }
        assertEquals(1, colors.size)
        assertEquals(7, colors[0].start)
        assertEquals(12, colors[0].end)
    }
}
