package com.alekseevich47.notes.ui.editor

import java.util.Locale

/**
 * Numbered-list editing in the Cursor / ChatGPT style:
 * Enter continues the list, an empty item exits, inserts renumber, and the
 * first letter after the marker is capitalized with a single space.
 */
object ListAutoFormat {
    data class Edit(val text: String, val cursor: Int)

    private val numbered = Regex("""^(\d+)\.\s?(.*)$""")
    private val ru = Locale.forLanguageTag("ru")

    fun afterEdit(previous: String, current: String, cursor: Int): Edit {
        if (previous == current) return Edit(current, cursor.coerceIn(0, current.length))
        val entered = insertedSingleNewline(previous, current, cursor)
        val stepped = if (entered) applyEnter(current, cursor) else Edit(current, cursor)
        return renumber(stepped.text, stepped.cursor)
    }

    fun renumber(text: String, cursor: Int): Edit {
        val safeCursor = cursor.coerceIn(0, text.length)
        val lines = text.split('\n')
        val (lineIndex, column) = lineColumn(lines, safeCursor)
        val rebuilt = renumberLines(lines)
        val oldPrefix = prefixLength(lines.getOrElse(lineIndex) { "" })
        val newPrefix = prefixLength(rebuilt.getOrElse(lineIndex) { "" })
        val newColumn = if (oldPrefix > 0 || newPrefix > 0) {
            newPrefix + (column - oldPrefix).coerceAtLeast(0)
        } else {
            column
        }
        val newText = rebuilt.joinToString("\n")
        var position = 0
        val clampedLine = lineIndex.coerceAtMost(rebuilt.lastIndex.coerceAtLeast(0))
        for (index in 0 until clampedLine) {
            position += rebuilt[index].length + 1
        }
        val lineLen = rebuilt.getOrElse(clampedLine) { "" }.length
        val resolved = (position + newColumn.coerceIn(0, lineLen)).coerceIn(0, newText.length)
        return Edit(newText, resolved)
    }

    fun toggleLine(text: String, cursor: Int): Edit {
        val safe = cursor.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (safe - 1).coerceAtLeast(0)).let { index ->
            if (index < 0) 0 else index + 1
        }
        val lineEndIndex = text.indexOf('\n', safe).let { index -> if (index < 0) text.length else index }
        val line = text.substring(lineStart, lineEndIndex)
        val match = numbered.matchEntire(line)
        val replaced = if (match != null) {
            match.groupValues[2].trimStart()
        } else {
            val body = capitalize(line.trimStart())
            if (body.isEmpty()) "1. " else "1. $body"
        }
        val updated = text.substring(0, lineStart) + replaced + text.substring(lineEndIndex)
        val nextCursor = (lineStart + replaced.length).coerceAtMost(updated.length)
        return renumber(updated, nextCursor)
    }

    private fun insertedSingleNewline(previous: String, current: String, cursor: Int): Boolean {
        if (cursor <= 0 || cursor > current.length) return false
        if (current[cursor - 1] != '\n') return false
        if (current.length != previous.length + 1) return false
        return current.removeRange(cursor - 1, cursor) == previous
    }

    private fun applyEnter(text: String, cursor: Int): Edit {
        val newlineAt = cursor - 1
        if (newlineAt < 0 || text.getOrNull(newlineAt) != '\n') return Edit(text, cursor)
        val lineStart = text.lastIndexOf('\n', newlineAt - 1).let { index ->
            if (index < 0) 0 else index + 1
        }
        val previousLine = text.substring(lineStart, newlineAt)
        val match = numbered.matchEntire(previousLine) ?: return Edit(text, cursor)
        val content = match.groupValues[2]
        if (content.isBlank()) {
            val removed = text.removeRange(lineStart, cursor)
            return Edit(removed, lineStart.coerceAtMost(removed.length))
        }
        val number = match.groupValues[1].toIntOrNull() ?: 1
        val insert = "${number + 1}. "
        val updated = text.substring(0, cursor) + insert + text.substring(cursor)
        return Edit(updated, cursor + insert.length)
    }

    internal fun renumberLines(lines: List<String>): List<String> {
        val out = lines.toMutableList()
        var index = 0
        while (index < out.size) {
            if (numbered.matchEntire(out[index]) == null) {
                index++
                continue
            }
            var end = index
            while (end < out.size && numbered.matchEntire(out[end]) != null) end++
            var number = 1
            for (line in index until end) {
                val content = numbered.matchEntire(out[line])!!.groupValues[2]
                val normalized = capitalize(content.trimStart())
                out[line] = if (normalized.isEmpty()) "$number. " else "$number. $normalized"
                number++
            }
            index = end
        }
        return out
    }

    private fun prefixLength(line: String): Int {
        val match = numbered.matchEntire(line) ?: return 0
        return line.length - match.groupValues[2].length
    }

    private fun lineColumn(lines: List<String>, cursor: Int): Pair<Int, Int> {
        var remaining = cursor
        for (index in lines.indices) {
            if (remaining <= lines[index].length) return index to remaining
            remaining -= lines[index].length + 1
        }
        val last = lines.lastIndex.coerceAtLeast(0)
        return last to lines.getOrElse(last) { "" }.length
    }

    private fun capitalize(value: String): String {
        if (value.isEmpty()) return value
        return value.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(ru) else char.toString()
        }
    }
}
