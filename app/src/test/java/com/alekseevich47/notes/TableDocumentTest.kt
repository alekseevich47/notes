package com.alekseevich47.notes

import com.alekseevich47.notes.domain.BlockKind
import com.alekseevich47.notes.domain.CellMarks
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.domain.NoteTable
import com.alekseevich47.notes.domain.NoteTables
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.ui.editor.growingColumnWidths
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

    @Test
    fun legacyJsonWithoutBlocksOpensBodyThenTablesThenTrailing() {
        val raw = """
            {"text":"body","marks":[],"tables":[{"id":"old","cells":[["a","b"],["c","d"]]}],"trailing":"tail"}
        """.trimIndent()
        val decoded = NoteDocuments.decode(raw)
        assertTrue(decoded.blocks.isEmpty())
        assertEquals("body", decoded.text)
        assertEquals("tail", decoded.trailing)
        assertEquals("old", decoded.tables.single().id)
        val blocks = NoteDocuments.flow(decoded)
        assertEquals(BlockKind.TEXT, blocks.first().kind)
        assertEquals("body", blocks.first().text)
        assertEquals(BlockKind.TABLE, blocks[1].kind)
        assertEquals("tail", blocks.last().text)
        assertEquals("body\na | b\nc | d\ntail", NoteDocuments.plainText(decoded))
    }

    @Test
    fun tableCanBeInsertedBetweenParagraphs() {
        val doc = NoteDocument(text = "Первый\n\nВторой")
        val cut = doc.text.indexOf("\n\n")
        val next = NoteDocuments.insertTable(doc, blockIndex = 0, cursor = cut, rows = 2, cols = 2, id = "mid")
        val blocks = NoteDocuments.flow(next)
        assertEquals("Первый", blocks.first().text)
        assertEquals(BlockKind.TABLE, blocks[1].kind)
        assertEquals(2, blocks[1].table!!.cells.size)
        assertEquals(2, blocks[1].table!!.cells.first().size)
        assertTrue(blocks.last().text.contains("Второй"))
        assertFalse(blocks.last().text.startsWith("Первый"))
        val again = NoteDocuments.decode(NoteDocuments.encode(next))
        assertEquals(next, again)
    }

    @Test
    fun unchangedDocumentMatchesStoredJson() {
        val doc = NoteDocument(text = "hi", tables = listOf(table))
        val encoded = NoteDocuments.encode(doc)
        assertTrue(NoteDocuments.sameStored("Title", encoded, "a.wav", "Title", doc, "a.wav"))
        assertFalse(NoteDocuments.sameStored("Title", encoded, "a.wav", "Other", doc, "a.wav"))
        assertFalse(NoteDocuments.sameStored("Title", encoded, "a.wav", "Title", doc.copy(text = "ho"), "a.wav"))
        assertFalse(NoteDocuments.sameStored("Title", encoded, "a.wav", "Title", doc, null))
        val legacy = """{"text":"hi","marks":[],"tables":[{"id":"t1","cells":[["a","b"],["c","d"]]}]}"""
        val opened = NoteDocuments.decode(legacy)
        assertTrue(NoteDocuments.sameStored("Title", legacy, null, "Title", opened, null))
    }

    @Test
    fun fillAlignMergeAndCaptionSurviveJson() {
        var styled = NoteTable(id = "t", cells = NoteTables.blank(2, 2), caption = "Шапка")
        styled = NoteTables.fillRow(styled, 0, true)
        styled = NoteTables.alignRow(styled, 1, horizontal = 0, vertical = 2)
        styled = NoteTables.withCell(styled, 0, 0, "a")
        styled = NoteTables.withCellMarks(
            styled,
            0,
            0,
            listOf(TextMark(0, 1, MarkStyle.UNDERLINE)),
        )
        styled = NoteTables.mergeRow(styled, 1)
        val original = NoteDocument(text = "before", tables = listOf(styled), trailing = "after")
        val decoded = NoteDocuments.decode(NoteDocuments.encode(original))
        assertEquals(original, decoded)
        val back = decoded.tables.single()
        assertEquals("Шапка", back.caption)
        assertTrue(NoteTables.filled(back, 0, 0))
        assertTrue(NoteTables.filled(back, 0, 1))
        assertEquals(0, NoteTables.alignH(back, 1, 0))
        assertEquals(2, NoteTables.alignV(back, 1, 0))
        assertTrue(NoteTables.colSpan(back, 1, 0) >= 2)
        assertEquals(0, NoteTables.colSpan(back, 1, 1))
        assertEquals(listOf(CellMarks(0, 0, listOf(TextMark(0, 1, MarkStyle.UNDERLINE)))), back.cellMarks)
    }

    @Test
    fun insertAndDeleteKeepTheLastRowAndColumn() {
        val grid = NoteTable(id = "t", cells = NoteTables.blank(2, 2))
        val wider = NoteTables.insertColumn(grid, 1)
        assertEquals(3, NoteTables.columnCount(wider))
        assertEquals(3, NoteTables.rowCount(NoteTables.insertRow(grid, 0)))
        val single = NoteTable(id = "t", cells = listOf(listOf("a")))
        assertEquals(single.cells, NoteTables.deleteRow(single, 0).cells)
        assertEquals(single.cells, NoteTables.deleteColumn(single, 0).cells)
        val full = NoteTables.blank(1, NoteTables.MAX_COLS)
        assertEquals(NoteTables.MAX_COLS, NoteTables.columnCount(NoteTables.insertColumn(NoteTable(id = "t", cells = full), 0)))
    }

    @Test
    fun columnsGrowWithTextUntilTheCap() {
        val widths = growingColumnWidths(intArrayOf(1, 40), availablePx = 100f, minPx = 72f, maxPx = 200f)
        assertEquals(2, widths.size)
        assertEquals(72f, widths[0], 0.1f)
        assertEquals(200f, widths[1], 0.1f)
        val spread = growingColumnWidths(intArrayOf(1, 1), availablePx = 400f, minPx = 72f, maxPx = 200f)
        assertEquals(400f, spread.sum(), 0.5f)
    }
}
