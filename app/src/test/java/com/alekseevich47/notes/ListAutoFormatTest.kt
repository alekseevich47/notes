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
