package com.alekseevich47.notes

import com.alekseevich47.notes.domain.BlockKind
import com.alekseevich47.notes.domain.CellMarks
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.NoteBlock
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.domain.NoteTable
import com.alekseevich47.notes.domain.TextMark
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBlobTest {
    @Test
    fun hiddenWordsStayOutOfTheBlob() {
        val text = "alpha secretum beta"
        val textMark = TextMark(start = 6, end = 14, style = MarkStyle.HIDDEN)
        val cell = "gamma concealed delta"
        val cellMark = TextMark(start = 6, end = 15, style = MarkStyle.HIDDEN)
        val table = NoteTable(
            id = "t",
            caption = "caption",
            cells = listOf(listOf(cell, "shown")),
            cellMarks = listOf(CellMarks(row = 0, col = 0, marks = listOf(cellMark))),
        )
        val blocks = NoteDocument(
            blocks = listOf(
                NoteBlock(kind = BlockKind.TEXT, text = text, marks = listOf(textMark)),
                NoteBlock(kind = BlockKind.TABLE, table = table),
            ),
        )
        val legacy = NoteDocument(
            text = text,
            marks = listOf(textMark),
            tables = listOf(table),
            trailing = "tailword",
        )
        for (document in listOf(blocks, legacy)) {
            val blob = NoteDocuments.searchBlob("TitleWord", document)
            assertTrue(blob.contains("titleword"))
            assertTrue(blob.contains("alpha"))
            assertTrue(blob.contains("beta"))
            assertTrue(blob.contains("caption"))
            assertTrue(blob.contains("gamma"))
            assertTrue(blob.contains("delta"))
            assertTrue(blob.contains("shown"))
            assertFalse(blob.contains("secretum"))
            assertFalse(blob.contains("concealed"))
            assertFalse(blob.contains("▒"))
        }
        assertTrue(NoteDocuments.searchBlob("TitleWord", legacy).contains("tailword"))
    }
}
