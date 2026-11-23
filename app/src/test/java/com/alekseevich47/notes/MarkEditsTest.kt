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
    fun clearCutsMarksOnTheBoundaries() {
        val cleared = MarkEdits.clear(
            listOf(
                TextMark(0, 6, MarkStyle.BOLD),
                TextMark(4, 6, MarkStyle.ITALIC),
                TextMark(10, 14, MarkStyle.UNDERLINE),
                TextMark(5, 12, MarkStyle.STRIKE),
            ),
            start = 4,
            end = 8,
        )
        assertEquals(listOf(TextMark(0, 4, MarkStyle.BOLD)), cleared.filter { it.style == MarkStyle.BOLD })
        assertEquals(emptyList<TextMark>(), cleared.filter { it.style == MarkStyle.ITALIC })
        assertEquals(listOf(TextMark(10, 14, MarkStyle.UNDERLINE)), cleared.filter { it.style == MarkStyle.UNDERLINE })
        assertEquals(listOf(TextMark(8, 12, MarkStyle.STRIKE)), cleared.filter { it.style == MarkStyle.STRIKE })
        val split = MarkEdits.clear(listOf(TextMark(0, 9, MarkStyle.BOLD)), start = 2, end = 6)
        assertEquals(
            listOf(TextMark(0, 2, MarkStyle.BOLD), TextMark(6, 9, MarkStyle.BOLD)),
            split,
        )
    }

    @Test
    fun applyTypingPendingBoldCoversOnlyTheInsert() {
        val marks = listOf(TextMark(0, 2, MarkStyle.ITALIC))
        val next = MarkEdits.applyTyping(
            marks,
            insStart = 4,
            insEnd = 7,
            pendingOn = setOf(MarkStyle.BOLD),
            pendingOff = emptySet(),
        )
        assertEquals(listOf(TextMark(4, 7, MarkStyle.BOLD)), next.filter { it.style == MarkStyle.BOLD })
        assertEquals(listOf(TextMark(0, 2, MarkStyle.ITALIC)), next.filter { it.style == MarkStyle.ITALIC })
    }

    @Test
    fun applyTypingPendingOffSplitsBoldAroundTheInsert() {
        val next = MarkEdits.applyTyping(
            listOf(TextMark(0, 6, MarkStyle.BOLD)),
            insStart = 2,
            insEnd = 3,
            pendingOn = emptySet(),
            pendingOff = setOf(MarkStyle.BOLD),
        )
        assertEquals(
            listOf(TextMark(0, 2, MarkStyle.BOLD), TextMark(3, 6, MarkStyle.BOLD)),
            next,
        )
    }

    @Test
    fun applyTypingIgnoresHiddenInPendingOn() {
        val next = MarkEdits.applyTyping(
            emptyList(),
            insStart = 1,
            insEnd = 4,
            pendingOn = setOf(MarkStyle.HIDDEN, MarkStyle.BOLD),
            pendingOff = emptySet(),
        )
        assertEquals(listOf(TextMark(1, 4, MarkStyle.BOLD)), next)
    }

    @Test
    fun applyTypingEmptyInsertAddsNothing() {
        val marks = listOf(TextMark(0, 3, MarkStyle.BOLD))
        val next = MarkEdits.applyTyping(
            marks,
            insStart = 1,
            insEnd = 1,
            pendingOn = setOf(MarkStyle.BOLD, MarkStyle.ITALIC),
            pendingOff = setOf(MarkStyle.UNDERLINE),
        )
        assertEquals(marks, next)
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
