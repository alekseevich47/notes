package com.alekseevich47.notes

import com.alekseevich47.notes.ui.editor.ListAutoFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ListAutoFormatTest {
    @Test
    fun enterContinuesAndCapitalizes() {
        val previous = "1. Первая"
        val current = "1. Первая\n"
        val edit = ListAutoFormat.afterEdit(previous, current, current.length)
        assertEquals("1. Первая\n2. ", edit.text)
        assertEquals(edit.text.length, edit.cursor)
    }

    @Test
    fun emptyEnterLeavesTheList() {
        val previous = "1. Первая\n2. "
        val current = "1. Первая\n2. \n"
        val edit = ListAutoFormat.afterEdit(previous, current, current.length)
        assertEquals("1. Первая\n", edit.text)
        assertEquals(edit.text.length, edit.cursor)
    }

    @Test
    fun emptyFirstItemClearsTheMarker() {
        val previous = "1. "
        val current = "1. \n"
        val edit = ListAutoFormat.afterEdit(previous, current, current.length)
        assertEquals("", edit.text)
        assertEquals(0, edit.cursor)
    }

    @Test
    fun emptyMarkerWithoutSpaceAlsoExits() {
        val previous = "1. Первая\n2."
        val current = "1. Первая\n2.\n"
        val edit = ListAutoFormat.afterEdit(previous, current, current.length)
        assertEquals("1. Первая\n", edit.text)
        assertEquals("1. Первая\n".length, edit.cursor)
    }

    @Test
    fun emptyEnterInMiddleExitsAndRenumbersTheTail() {
        val previous = "1. А\n2. \n3. Б"
        val insertAt = "1. А\n2. ".length
        val current = previous.substring(0, insertAt) + "\n" + previous.substring(insertAt)
        val edit = ListAutoFormat.afterEdit(previous, current, insertAt + 1)
        assertEquals("1. А\n\n1. Б", edit.text)
        assertEquals("1. А\n".length, edit.cursor)
    }

    @Test
    fun enterAfterNinthContinuesAtTen() {
        val previous = (1..9).joinToString("\n") { "$it. Пункт" }
        val current = previous + "\n"
        val edit = ListAutoFormat.afterEdit(previous, current, current.length)
        assertEquals(previous + "\n10. ", edit.text)
        assertEquals(edit.text.length, edit.cursor)
    }

    @Test
    fun markerGrowthKeepsTheCursorInsideTheWord() {
        val head = (1..8).joinToString("\n") { "$it. П" }
        val source = head + "\n9. А\n9. Хвост"
        val cursor = source.length - "Хвост".length + 2
        val edit = ListAutoFormat.renumber(source, cursor)
        val expected = head + "\n9. А\n10. Хвост"
        assertEquals(expected, edit.text)
        assertEquals(expected.length - "Хвост".length + 2, edit.cursor)
    }

    @Test
    fun typingOnAnEmptyMarkerStaysInTheList() {
        val edit = ListAutoFormat.afterEdit("1. ", "1. а", cursor = "1. а".length)
        assertEquals("1. А", edit.text)
        assertEquals("1. А".length, edit.cursor)
    }

    @Test
    fun insertBetweenRenumbers() {
        val edit = ListAutoFormat.renumber("1. А\n2. Вставка\n2. Б", cursor = 0)
        assertEquals("1. А\n2. Вставка\n3. Б", edit.text)
    }

    @Test
    fun markerGetsSpaceAndCapital() {
        val edit = ListAutoFormat.renumber("1.привет", cursor = "1.привет".length)
        assertEquals("1. Привет", edit.text)
        assertEquals("1. Привет".length, edit.cursor)
    }

    @Test
    fun separateBlocksRestart() {
        val edit = ListAutoFormat.renumber("1. А\n\n2. Б", cursor = 0)
        assertEquals("1. А\n\n1. Б", edit.text)
    }

    @Test
    fun plainTextStaysUntouched() {
        val edit = ListAutoFormat.afterEdit("Заметка", "Заметки", cursor = 7)
        assertEquals("Заметки", edit.text)
    }

    @Test
    fun toggleAddsAndRemovesMarker() {
        val on = ListAutoFormat.toggleLine("пункт", cursor = 5)
        assertEquals("1. Пункт", on.text)
        val off = ListAutoFormat.toggleLine(on.text, cursor = on.text.length)
        assertEquals("Пункт", off.text)
    }
}
