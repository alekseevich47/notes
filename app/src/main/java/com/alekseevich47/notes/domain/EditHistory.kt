package com.alekseevich47.notes.domain

internal enum class EditKind { TYPING, STRUCTURE }

/**
 * One undo step. [caret] is a position in this snapshot, restored with the text.
 * [atMs] is the time of the latest keystroke folded into the step.
 */
internal data class EditSnapshot(
    val title: String,
    val document: NoteDocument,
    val kind: EditKind,
    val atMs: Long,
    val caret: BlockCaret? = null,
)

/**
 * Word-sized undo. Typing of a single character folds into the open entry when
 * the previous step was also typing, the gap is under a second, and the
 * inserted text has no space or newline. The stored snapshot stays the text
 * from before the word.
 */
internal object EditHistory {
    const val COALESCE_WINDOW_MS = 1_000L
    const val MAX_ENTRIES = 200
    const val MAX_CHARS = 2_000_000

    fun inserted(before: String, after: String): String {
        val prefix = sharedPrefix(before, after)
        var suffix = 0
        val limit = minOf(before.length - prefix, after.length - prefix)
        while (
            suffix < limit &&
            before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
        ) {
            suffix++
        }
        val end = after.length - suffix
        if (end <= prefix) return ""
        return after.substring(prefix, end)
    }

    /**
     * List autoformat rewrites the text. A multi-character insert is a paste.
     * One inserted character, including a space or newline, stays typing; the
     * coalesce rule decides whether it opens a new step.
     */
    fun kindForTextChange(before: String, proposed: String, formatted: String): EditKind {
        if (formatted != proposed) return EditKind.STRUCTURE
        if (inserted(before, formatted).length > 1) return EditKind.STRUCTURE
        return EditKind.TYPING
    }

    fun coalesce(previous: EditSnapshot, kind: EditKind, atMs: Long, insertion: String): Boolean {
        if (kind != EditKind.TYPING || previous.kind != EditKind.TYPING) return false
        if (atMs - previous.atMs >= COALESCE_WINDOW_MS) return false
        if (' ' in insertion || '\n' in insertion) return false
        return true
    }

    /** Title, block text, and table cell text. Marks and ids are not counted. */
    fun snapshotChars(title: String, document: NoteDocument): Int {
        var total = title.length
        for (block in NoteDocuments.flow(document)) {
            total += block.text.length
            val cells = block.table?.cells ?: continue
            for (row in cells) {
                for (cell in row) total += cell.length
            }
        }
        return total
    }

    fun record(
        undo: List<EditSnapshot>,
        title: String,
        document: NoteDocument,
        kind: EditKind,
        atMs: Long,
        insertion: String,
        caret: BlockCaret? = null,
        forceNew: Boolean = false,
    ): List<EditSnapshot> {
        val last = undo.lastOrNull()
        if (!forceNew && last != null && coalesce(last, kind, atMs, insertion)) {
            val updated = undo.toMutableList()
            updated[updated.lastIndex] = last.copy(atMs = atMs)
            return updated
        }
        return trim(undo + EditSnapshot(title, document, kind, atMs, caret))
    }

    fun trim(entries: List<EditSnapshot>): List<EditSnapshot> {
        if (entries.isEmpty()) return entries
        var total = 0
        for (entry in entries) total += snapshotChars(entry.title, entry.document)
        var start = 0
        val overflow = entries.size > MAX_ENTRIES || total > MAX_CHARS
        if (!overflow) return entries
        while (start < entries.size && (entries.size - start > MAX_ENTRIES || total > MAX_CHARS)) {
            total -= snapshotChars(entries[start].title, entries[start].document)
            start++
        }
        return entries.subList(start, entries.size).toList()
    }

    private fun sharedPrefix(left: String, right: String): Int {
        val limit = minOf(left.length, right.length)
        var index = 0
        while (index < limit && left[index] == right[index]) index++
        return index
    }
}
