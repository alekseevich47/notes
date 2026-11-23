package com.alekseevich47.notes.ui.editor

import java.util.Locale

/**
 * Numbered-list editing in the Cursor / ChatGPT style:
 * Enter continues the list, an empty item exits, inserts renumber, and the
 * first letter after the marker is capitalized with a single space.
 * Backspace on an empty item ("N. " with a blank body), including one that
 * sits between filled items, removes that line and renumbers the tail.
 */
object ListAutoFormat {
    data class Edit(val text: String, val cursor: Int)

    private val numbered = Regex("""^(\d+)\.\s?(.*)$""")
    private val emptyNumbered = Regex("""^\d+\.\s*$""")
    private val ru = Locale.forLanguageTag("ru")

    fun afterEdit(
        previous: String,
        current: String,
        cursor: Int,
        startAt: Int = 1,
        composing: Boolean = false,
    ): Edit {
        if (previous == current) return Edit(current, cursor.coerceIn(0, current.length))
        if (insertedSingleNewline(previous, current, cursor)) {
            val stepped = applyEnter(current, cursor)
            return renumber(stepped.text, stepped.cursor, startAt)
        }
        // Capitalize and renumber only after the IME commits the word. A live
        // composition range must keep matching the text the keyboard still owns.
        if (composing) return Edit(current, cursor.coerceIn(0, current.length))
        val collapsed = collapseEmptyItem(previous, current)
        if (collapsed != null) return renumber(collapsed.text, collapsed.cursor, startAt)
        return renumber(current, cursor.coerceIn(0, current.length), startAt)
    }

    /**
     * Number of the next item when a table sits inside a list.
     * Trailing blank lines are skipped. A non-list line ends the run.
     */
    fun continuationStart(text: String): Int {
        val lines = text.split('\n')
        var index = lines.lastIndex
        while (index >= 0 && lines[index].isBlank()) index--
        if (index < 0) return 1
        val match = numbered.matchEntire(lines[index]) ?: return 1
        return (match.groupValues[1].toIntOrNull() ?: 0) + 1
    }

    fun renumber(text: String, cursor: Int, startAt: Int = 1): Edit {
        val safeCursor = cursor.coerceIn(0, text.length)
        val lines = text.split('\n')
        val (lineIndex, column) = lineColumn(lines, safeCursor)
        val rebuilt = renumberLines(lines, startAt)
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

    fun toggleLine(text: String, cursor: Int, startAt: Int = 1): Edit {
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
        return renumber(updated, nextCursor, startAt)
    }

    /**
     * An empty numbered line ("4." / "4. ") cannot be chipped away: renumber
     * would put the trailing space back. Any deletion that only touches that
     * line — backspace at its start, backspace through the marker, or deleting
     * the marker text — drops the line so neighbours join and renumber.
     */
    private fun collapseEmptyItem(previous: String, current: String): Edit? {
        if (current.length >= previous.length) return null
        val prefix = sharedPrefix(previous, current)
        var suffix = 0
        while (
            suffix < previous.length - prefix &&
            suffix < current.length - prefix &&
            previous[previous.length - 1 - suffix] == current[current.length - 1 - suffix]
        ) {
            suffix++
        }
        val oldStart = prefix
        val oldEnd = previous.length - suffix
        if (oldEnd <= oldStart) return null
        if (current.substring(prefix, current.length - suffix).isNotEmpty()) return null

        val spans = lineSpans(previous)
        val target = spans.firstOrNull { span ->
            emptyNumbered.matches(span.text) && targetsEmptyLine(span, oldStart, oldEnd, previous.length)
        } ?: return null
        val lines = previous.split('\n')
        val kept = lines.filterIndexed { index, _ -> index != target.index }
        val newText = kept.joinToString("\n")
        val newCursor = if (target.index == 0) {
            0
        } else {
            var position = 0
            for (index in 0 until target.index - 1) position += lines[index].length + 1
            position + lines[target.index - 1].length
        }
        return Edit(newText, newCursor.coerceIn(0, newText.length))
    }

    private fun targetsEmptyLine(span: LineSpan, oldStart: Int, oldEnd: Int, length: Int): Boolean {
        val minStart = if (span.start > 0) span.start - 1 else span.start
        val maxEnd = if (span.end < length) span.end + 1 else span.end
        if (oldStart < minStart || oldEnd > maxEnd) return false
        val overlapsLine = oldStart < span.end && oldEnd > span.start
        val precedingNewline = span.start > 0 && oldStart == span.start - 1 && oldEnd == span.start
        return overlapsLine || precedingNewline
    }

    private data class LineSpan(val index: Int, val start: Int, val end: Int, val text: String)

    private fun lineSpans(text: String): List<LineSpan> {
        val lines = text.split('\n')
        val spans = ArrayList<LineSpan>(lines.size)
        var offset = 0
        for (index in lines.indices) {
            val line = lines[index]
            spans += LineSpan(index, offset, offset + line.length, line)
            offset += line.length + 1
        }
        return spans
    }

    private fun sharedPrefix(left: String, right: String): Int {
        val limit = minOf(left.length, right.length)
        var index = 0
        while (index < limit && left[index] == right[index]) index++
        return index
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

    internal fun renumberLines(lines: List<String>, startAt: Int = 1): List<String> {
        val out = lines.toMutableList()
        var index = 0
        while (index < out.size) {
            if (numbered.matchEntire(out[index]) == null) {
                index++
                continue
            }
            var end = index
            while (end < out.size && numbered.matchEntire(out[end]) != null) end++
            val continues = startAt > 1 && out.subList(0, index).all { it.isBlank() }
            var number = if (continues) startAt else 1
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
