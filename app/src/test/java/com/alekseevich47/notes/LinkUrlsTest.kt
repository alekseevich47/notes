package com.alekseevich47.notes

import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.domain.isSafeLink
import com.alekseevich47.notes.domain.linkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkUrlsTest {
    @Test
    fun allowsOnlyHttpHttpsMailtoAndTel() {
        assertTrue(isSafeLink("https://example.com"))
        assertTrue(isSafeLink("http://example.com/a"))
        assertTrue(isSafeLink("HTTP://Example.COM"))
        assertTrue(isSafeLink(" mailto:a@b.c "))
        assertTrue(isSafeLink("tel:+7999"))
        assertFalse(isSafeLink(""))
        assertFalse(isSafeLink("   "))
        assertFalse(isSafeLink("javascript:alert(1)"))
        assertFalse(isSafeLink("JaVaScRiPt:alert(1)"))
        assertFalse(isSafeLink("intent://scan/#Intent;scheme=https;end"))
        assertFalse(isSafeLink("file:///etc/passwd"))
        assertFalse(isSafeLink("https://"))
        assertFalse(isSafeLink("http"))
        assertFalse(isSafeLink("mailto:"))
        assertFalse(isSafeLink("tel:"))
    }

    @Test
    fun hiddenTextDoesNotExposeItsLink() {
        val marks = listOf(
            TextMark(0, 4, MarkStyle.LINK, "https://example.com"),
            TextMark(0, 4, MarkStyle.HIDDEN),
        )
        assertNull(linkTarget(marks, 1, hiddenUnlocked = false))
        assertEquals("https://example.com", linkTarget(marks, 1, hiddenUnlocked = true))
        assertNull(linkTarget(listOf(TextMark(0, 4, MarkStyle.LINK, "javascript:x")), 1, true))
    }
}
