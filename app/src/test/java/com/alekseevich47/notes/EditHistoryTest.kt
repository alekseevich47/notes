package com.alekseevich47.notes

import com.alekseevich47.notes.domain.BlockKind
import com.alekseevich47.notes.domain.EditHistory
import com.alekseevich47.notes.domain.EditKind
import com.alekseevich47.notes.domain.EditSnapshot
import com.alekseevich47.notes.domain.NoteBlock
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {
    @Test
    fun lettersInsideTheWindowShareOneUndoTarget() {
        var undo = emptyList<EditSnapshot>()
        undo = push(undo, "", TYPING, 0L, "h")
        undo = push(undo, "h", TYPING, 400L, "e")
        undo = push(undo, "he", TYPING, 900L, "l")
        assertEquals(1, undo.size)
        assertEquals("", undo.single().document.text)
        assertEquals(900L, undo.single().atMs)
        assertEquals(EditKind.TYPING, undo.single().kind)
    }

    @Test
    fun aSpaceStartsANewEntry() {
        var undo = push(emptyList(), "", TYPING, 0L, "h")
        undo = push(undo, "h", TYPING, 100L, "e")
        undo = push(undo, "he", TYPING, 200L, "l")
        undo = push(undo, "hel", TYPING, 300L, "l")
        undo = push(undo, "hell", TYPING, 400L, "o")
        undo = push(undo, "hello", TYPING, 500L, " ")
        assertEquals(2, undo.size)
        assertEquals("", undo[0].document.text)
        assertEquals("hello", undo[1].document.text)
    }

    @Test
    fun aNewlineStartsANewEntry() {
        val undo = push(push(emptyList(), "hi", TYPING, 0L, "i"), "hi", TYPING, 100L, "\n")
        assertEquals(2, undo.size)
        assertEquals("hi", undo[1].document.text)
    }

    @Test
    fun aThousandMillisecondsStartsANewEntry() {
        var undo = push(emptyList(), "a", TYPING, 0L, "a")
        undo = push(undo, "a", TYPING, 999L, "b")
        assertEquals(1, undo.size)
        undo = push(undo, "ab", TYPING, 999L + 1_000L, "c")
        assertEquals(2, undo.size)
        assertEquals("ab", undo[1].document.text)
    }

    @Test
    fun structureBreaksTheTypingChain() {
        var undo = push(emptyList(), "a", TYPING, 0L, "a")
        undo = push(undo, "ab", STRUCTURE, 100L, "")
        undo = push(undo, "ab", TYPING, 200L, "c")
        assertEquals(3, undo.size)
        assertEquals(EditKind.STRUCTURE, undo[1].kind)
        assertEquals("ab", undo[2].document.text)
    }

    @Test
    fun theEntryCapDropsTheOldest() {
        var undo = emptyList<EditSnapshot>()
        repeat(250) { index ->
            undo = EditHistory.record(
                undo,
                title = index.toString(),
                document = NoteDocument(text = "n"),
                kind = EditKind.STRUCTURE,
                atMs = index.toLong(),
                insertion = "",
            )
        }
        assertEquals(200, undo.size)
        assertEquals("50", undo.first().title)
        assertEquals("249", undo.last().title)
    }

    @Test
    fun theCharacterCapDropsTheOldest() {
        val chunk = "a".repeat(500_000)
        var undo = emptyList<EditSnapshot>()
        repeat(5) { index ->
            undo = EditHistory.record(
                undo,
                title = "",
                document = NoteDocument(text = chunk),
                kind = EditKind.STRUCTURE,
                atMs = index.toLong(),
                insertion = "",
            )
        }
        assertEquals(4, undo.size)
        assertEquals(1L, undo.first().atMs)
        val stored = undo.sumOf { EditHistory.snapshotChars(it.title, it.document) }
        assertTrue(stored <= 2_000_000)
    }

    @Test
    fun snapshotCharsCountTitleBlocksAndCells() {
        val document = NoteDocument(
            blocks = listOf(
                NoteBlock(kind = BlockKind.TEXT, text = "hi"),
                NoteBlock(
                    kind = BlockKind.TABLE,
                    table = NoteTable(id = "t", cells = listOf(listOf("abcd", "ef"))),
                ),
            ),
        )
        assertEquals(1 + 2 + 4 + 2, EditHistory.snapshotChars("z", document))
    }

    @Test
    fun listRewriteAndPasteAreStructure() {
        assertEquals(
            EditKind.STRUCTURE,
            EditHistory.kindForTextChange("1. а", "1. а\n", "1. А\n2. "),
        )
        assertEquals(EditKind.STRUCTURE, EditHistory.kindForTextChange("a", "a hello", "a hello"))
        assertEquals(EditKind.TYPING, EditHistory.kindForTextChange("a", "ab", "ab"))
        assertEquals(EditKind.TYPING, EditHistory.kindForTextChange("ab", "a b", "a b"))
    }

    private fun push(
        undo: List<EditSnapshot>,
        before: String,
        kind: EditKind,
        atMs: Long,
        insertion: String,
    ): List<EditSnapshot> {
        return EditHistory.record(
            undo,
            title = "",
            document = NoteDocument(text = before),
            kind = kind,
            atMs = atMs,
            insertion = insertion,
        )
    }

    private companion object {
        val TYPING = EditKind.TYPING
        val STRUCTURE = EditKind.STRUCTURE
    }
}
