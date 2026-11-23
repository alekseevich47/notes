package com.alekseevich47.notes

import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.domain.TextMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDecodeTest {
    @Test
    fun unknownStyleDropsTheMarkAndKeepsTheText() {
        val raw = """
            {"text":"keep visible","marks":[{"start":0,"end":4,"style":"FUTURE"},{"start":5,"end":12,"style":"BOLD"}],"tables":[{"id":"t","cells":[["cell secret"]],"cellMarks":[{"row":0,"col":0,"marks":[{"start":5,"end":11,"style":"FUTURE"},{"start":0,"end":4,"style":"ITALIC"}]}]}],"trailing":"tail","blocks":[{"kind":"TEXT","text":"block stays","marks":[{"start":0,"end":5,"style":"FUTURE"},{"start":6,"end":11,"style":"UNDERLINE"}]}]}
        """.trimIndent()
        val decoded = NoteDocuments.read(raw)
        assertFalse(decoded.unreadable)
        assertEquals("keep visible", decoded.document.text)
        assertEquals("tail", decoded.document.trailing)
        assertEquals(listOf(TextMark(5, 12, MarkStyle.BOLD)), decoded.document.marks)
        assertEquals("cell secret", decoded.document.tables.single().cells.single().single())
        assertEquals(
            listOf(TextMark(0, 4, MarkStyle.ITALIC)),
            decoded.document.tables.single().cellMarks.single().marks,
        )
        val block = decoded.document.blocks.single()
        assertEquals("block stays", block.text)
        assertEquals(listOf(TextMark(6, 11, MarkStyle.UNDERLINE)), block.marks)
        val encoded = NoteDocuments.encode(decoded.document)
        assertFalse(encoded.contains("FUTURE"))
        assertTrue(encoded.contains("\"BOLD\""))
        assertTrue(encoded.contains("\"ITALIC\""))
        assertTrue(encoded.contains("\"UNDERLINE\""))
    }

    @Test
    fun brokenObjectIsUnreadableInsteadOfRawJson() {
        val raw = "{not-json"
        val decoded = NoteDocuments.read(raw)
        assertTrue(decoded.unreadable)
        assertEquals("", decoded.document.text)
        assertFalse(decoded.document.text.contains(raw))
    }

    @Test
    fun blankRawStaysEditable() {
        val decoded = NoteDocuments.read("   ")
        assertFalse(decoded.unreadable)
        assertEquals(NoteDocument(), decoded.document)
    }

    @Test
    fun knownStylesEncodeAsTheirNames() {
        val encoded = NoteDocuments.encode(
            NoteDocument(
                text = "abcde",
                marks = listOf(
                    TextMark(0, 1, MarkStyle.BOLD),
                    TextMark(1, 2, MarkStyle.ITALIC),
                    TextMark(2, 3, MarkStyle.STRIKE),
                    TextMark(3, 4, MarkStyle.UNDERLINE),
                    TextMark(4, 5, MarkStyle.HIDDEN),
                ),
            ),
        )
        assertTrue(encoded.contains("\"BOLD\""))
        assertTrue(encoded.contains("\"ITALIC\""))
        assertTrue(encoded.contains("\"STRIKE\""))
        assertTrue(encoded.contains("\"UNDERLINE\""))
        assertTrue(encoded.contains("\"HIDDEN\""))
        val back = NoteDocuments.read(encoded)
        assertFalse(back.unreadable)
        assertEquals("abcde", back.document.text)
        assertEquals(5, back.document.marks.size)
    }
}
