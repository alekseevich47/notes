package com.alekseevich47.notes

import com.alekseevich47.notes.data.repository.escapeSearchNeedle
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchNeedleTest {
    @Test
    fun escapesLikeMetacharactersBackslashFirst() {
        assertEquals("hello", escapeSearchNeedle("hello"))
        assertEquals("\\%", escapeSearchNeedle("%"))
        assertEquals("\\_", escapeSearchNeedle("_"))
        assertEquals("\\\\", escapeSearchNeedle("\\"))
        assertEquals("a\\%b\\_c\\\\d", escapeSearchNeedle("a%b_c\\d"))
    }
}
