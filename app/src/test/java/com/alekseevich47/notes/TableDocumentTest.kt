package com.alekseevich47.notes

import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.domain.NoteTable
import com.alekseevich47.notes.domain.NoteTables
import com.alekseevich47.notes.domain.TextMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableDocumentTest {
    private val table = NoteTable(id = "t1", cells = listOf(listOf("a", "b"), listOf("c", "d")))

    @Test
    fun plainTextPutsTheCaptionAfterTheGrid() {
        val text = NoteDocuments.plainText(
            NoteDocument(text = "Hello", tables = listOf(table), trailing = "Bye"),
        )
        assertEquals("Hello\na | b\nc | d\nBye", text)
    }

    @Test
    fun plainTextWithoutACaptionMatchesTheOldShape() {
        val text = NoteDocuments.plainText(NoteDocument(text = "Hello", tables = listOf(table)))
        assertEquals("Hello\na | b\nc | d\n", text)
    }

    @Test
    fun removingTheLastTableKeepsMarksAndJoinsTheCaption() {
        val mark = TextMark(start = 0, end = 5, style = MarkStyle.BOLD)
        val next = NoteDocuments.withoutTable(
            NoteDocument(text = "Hello", marks = listOf(mark), tables = listOf(table), trailing = "after"),
            "t1",
        )
        assertEquals("Hello\nafter", next.text)
        assertEquals("", next.trailing)
        assertTrue(next.tables.isEmpty())
        assertEquals(listOf(mark), next.marks)
    }

    @Test
    fun removingTheLastTableDoesNotInventABreakWhenTheCaptionIsEmpty() {
        val next = NoteDocuments.withoutTable(
            NoteDocument(text = "Hello", tables = listOf(table)),
            "t1",
        )
        assertEquals("Hello", next.text)
        assertEquals("", next.trailing)
    }

    @Test
    fun removingOneTableLeavesTheCaptionUnderTheRest() {
        val other = NoteTable(id = "t2", cells = listOf(listOf("z")))
        val next = NoteDocuments.withoutTable(
            NoteDocument(text = "body", tables = listOf(table, other), trailing = "tail"),
            "t1",
        )
        assertEquals(listOf(other), next.tables)
        assertEquals("body", next.text)
        assertEquals("tail", next.trailing)
    }

    @Test
    fun captionJoinsWithoutASecondBreakWhenTheBodyAlreadyEnds() {
        val next = NoteDocuments.withoutTable(
            NoteDocument(text = "Hello\n", tables = listOf(table), trailing = "after"),
            "t1",
        )
        assertEquals("Hello\nafter", next.text)
    }

    @Test
    fun emptyBodyBecomesTheCaption() {
        val next = NoteDocuments.withoutTable(
            NoteDocument(tables = listOf(table), trailing = "after"),
            "t1",
        )
        assertEquals("after", next.text)
    }

    @Test
    fun previewShowsTheCaptionAndStillMasksHiddenText() {
        val preview = NoteDocuments.preview(
            NoteDocument(
                text = "abSECRET",
                marks = listOf(TextMark(start = 2, end = 8, style = MarkStyle.HIDDEN)),
                tables = listOf(table),
                trailing = "visible",
            ),
        )
        assertFalse(preview.contains("SECRET"))
        assertTrue(preview.contains("visible"))
        assertTrue(preview.contains("▦"))
    }

    @Test
    fun previewWithoutTablesStaysTheBody() {
        assertEquals("Привет", NoteDocuments.preview(NoteDocument(text = "Привет")))
        assertEquals("Пустая заметка", NoteDocuments.preview(NoteDocument()))
    }

    @Test
    fun legacyJsonDecodesWithAnEmptyCaption() {
        val decoded = NoteDocuments.decode("""{"text":"hi","marks":[],"tables":[]}""")
        assertEquals("hi", decoded.text)
        assertEquals("", decoded.trailing)
        assertTrue(decoded.tables.isEmpty())
    }

    @Test
    fun captionRoundTripsThroughJson() {
        val original = NoteDocument(text = "a", tables = listOf(table), trailing = "b")
        assertEquals(original, NoteDocuments.decode(NoteDocuments.encode(original)))
    }

    @Test
    fun blankTableIsClampedToTheGridLimits() {
        assertEquals(1, NoteTables.blank(0, 0).size)
        assertEquals(1, NoteTables.blank(0, 0).first().size)
        assertEquals(NoteTables.MAX_ROWS, NoteTables.blank(100, 1).size)
        assertEquals(NoteTables.MAX_COLS, NoteTables.blank(1, 100).first().size)
    }

    @Test
    fun rowsAndColumnsCanGrowAndShrinkButNotDisappear() {
        val grown = NoteTables.addColumn(listOf(listOf("a", "b"), listOf("c")))
        assertEquals(listOf(listOf("a", "b", ""), listOf("c", "", "")), grown)
        assertEquals(listOf(listOf("a")), NoteTables.removeRow(listOf(listOf("a"), listOf("b"))))
        assertEquals(listOf(listOf("a")), NoteTables.removeRow(listOf(listOf("a"))))
        assertEquals(listOf(listOf("a")), NoteTables.removeColumn(listOf(listOf("a", "b"))))
        assertEquals(listOf(listOf("a")), NoteTables.removeColumn(listOf(listOf("a"))))
        val full = NoteTables.blank(1, NoteTables.MAX_COLS)
        assertEquals(full, NoteTables.addColumn(full))
        val tall = NoteTables.blank(NoteTables.MAX_ROWS, 1)
        assertEquals(tall, NoteTables.addRow(tall))
    }
}
