package com.alekseevich47.notes

import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.ui.editor.RichClip
import com.alekseevich47.notes.ui.editor.textInsertion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RichClipTest {
    @Test
    fun matchReturnsOnlyTheLastCopiedText() {
        RichClip.put("alpha", listOf(TextMark(0, 5, MarkStyle.BOLD)))
        assertEquals(listOf(TextMark(0, 5, MarkStyle.BOLD)), RichClip.match("alpha"))
        assertNull(RichClip.match("alph"))
        RichClip.put("beta", listOf(TextMark(0, 2, MarkStyle.ITALIC)))
        assertNull(RichClip.match("alpha"))
        assertEquals(listOf(TextMark(0, 2, MarkStyle.ITALIC)), RichClip.match("beta"))
    }

    @Test
    fun clipShiftsTheFragmentSoItStartsAtZero() {
        val clipped = RichClip.clip(
            listOf(
                TextMark(0, 4, MarkStyle.ITALIC),
                TextMark(2, 8, MarkStyle.BOLD),
                TextMark(10, 12, MarkStyle.UNDERLINE),
            ),
            start = 4,
            end = 9,
        )
        assertEquals(listOf(TextMark(0, 4, MarkStyle.BOLD)), clipped)
    }

    @Test
    fun overlayPlacesCopiedMarksOnTheInsertion() {
        val placed = RichClip.overlay(
            existing = listOf(TextMark(0, 2, MarkStyle.ITALIC)),
            origin = 3,
            copied = listOf(TextMark(0, 4, MarkStyle.BOLD)),
        )
        assertEquals(listOf(TextMark(3, 7, MarkStyle.BOLD)), placed.filter { it.style == MarkStyle.BOLD })
        assertEquals(listOf(TextMark(0, 2, MarkStyle.ITALIC)), placed.filter { it.style == MarkStyle.ITALIC })
    }

    @Test
    fun insertionIsThePrefixSuffixDifference() {
        val replaced = textInsertion("hello world", "hello PASTE")
        assertEquals(6, replaced?.start)
        assertEquals(11, replaced?.end)
        val typed = textInsertion("hello", "hello!")
        assertEquals(5, typed?.start)
        assertEquals(6, typed?.end)
        assertNull(textInsertion("hello!", "hello"))
        assertNull(textInsertion("same", "same"))
    }

    @Test
    fun clipRoundTripsTheUrl() {
        val link = TextMark(1, 6, MarkStyle.LINK, "mailto:a@b.c")
        val clipped = RichClip.clip(listOf(link), 2, 5)
        assertEquals("mailto:a@b.c", clipped.single().url)
        RichClip.put("cde", clipped)
        val matched = RichClip.match("cde")
        assertEquals("mailto:a@b.c", matched?.single()?.url)
        val overlaid = RichClip.overlay(emptyList(), 3, matched!!)
        assertEquals(TextMark(3, 6, MarkStyle.LINK, "mailto:a@b.c"), overlaid.single())
    }
}
