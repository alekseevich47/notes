package com.alekseevich47.notes

import com.alekseevich47.notes.domain.PreviewPart
import com.alekseevich47.notes.ui.editor.spoilerContentDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpoilerContentDescriptionTest {
    @Test
    fun hiddenWordBecomesTheSpokenPhrase() {
        val spoken = spoilerContentDescription(
            listOf(
                PreviewPart("привет ", false),
                PreviewPart("секрет", true),
            ),
        )
        assertEquals("привет скрытый текст", spoken)
    }

    @Test
    fun visibleWordStays() {
        assertEquals(
            "заметка",
            spoilerContentDescription(listOf(PreviewPart("заметка", false))),
        )
    }

    @Test
    fun allVisiblePreviewDoesNotInsertThePhrase() {
        val spoken = spoilerContentDescription(
            listOf(
                PreviewPart("один", false),
                PreviewPart(" ", false),
                PreviewPart("два", false),
            ),
        )
        assertEquals("один два", spoken)
        assertFalse(spoken.contains("скрытый текст"))
    }

    @Test
    fun repeatedSpacesCollapseAroundThePhrase() {
        val spoken = spoilerContentDescription(
            listOf(
                PreviewPart("a   ", false),
                PreviewPart("тайна", true),
                PreviewPart("   b", false),
            ),
        )
        assertEquals("a скрытый текст b", spoken)
    }
}
