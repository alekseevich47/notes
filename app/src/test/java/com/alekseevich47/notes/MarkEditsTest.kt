package com.alekseevich47.notes

import com.alekseevich47.notes.domain.MarkEdits
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkEditsTest {
    private val source = "hello bold world"
    private val bold = TextMark(start = 6, end = 10, style = MarkStyle.BOLD)

    @Test
    fun insertBeforeTheSpanStaysOutside() {
        val after = "hello Xbold world"
        val mark = MarkEdits.shift(listOf(bold), source, after).single()
        assertEquals(7, mark.start)
        assertEquals(11, mark.end)
        assertEquals("bold", after.substring(mark.start, mark.end))
    }

    @Test
    fun insertInsideTheSpanStaysInside() {
        val after = "hello boXld world"
        val mark = MarkEdits.shift(listOf(bold), source, after).single()
        assertEquals(6, mark.start)
        assertEquals(11, mark.end)
        assertEquals("boXld", after.substring(mark.start, mark.end))
    }

    @Test
    fun insertAfterTheSpanStaysOutside() {
        val after = "hello boldX world"
        val mark = MarkEdits.shift(listOf(bold), source, after).single()
        assertEquals(6, mark.start)
        assertEquals(10, mark.end)
        assertEquals("bold", after.substring(mark.start, mark.end))
    }

    @Test
    fun deleteAcrossTheBoundaryTrimsTheSpan() {
        // "d w" crosses the end of "bold" into the following word.
        val after = "hello bolorld"
        val mark = MarkEdits.shift(listOf(bold), source, after).single()
        assertEquals(6, mark.start)
        assertEquals(9, mark.end)
        assertEquals("bol", after.substring(mark.start, mark.end))
    }
}
