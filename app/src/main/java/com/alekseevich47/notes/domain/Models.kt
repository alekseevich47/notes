package com.alekseevich47.notes.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

enum class MicPhase { IDLE, RECORDING, PROCESSING, DONE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class MarkStyle { BOLD, ITALIC, STRIKE, UNDERLINE, HIDDEN }

@Serializable
data class TextMark(
    val start: Int,
    val end: Int,
    val style: MarkStyle,
)

@Serializable
data class NoteTable(
    val id: String,
    val cells: List<List<String>>,
)

@Serializable
data class NoteDocument(
    val text: String = "",
    val marks: List<TextMark> = emptyList(),
    val tables: List<NoteTable> = emptyList(),
    /** Paragraph under the last table. Folded back into [text] when no tables remain. */
    val trailing: String = "",
)

data class Note(
    val id: Long,
    val title: String,
    val document: NoteDocument,
    val audioPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val seq: Int,
    val searchBlob: String,
)

object NoteDocuments {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(document: NoteDocument): String = json.encodeToString(document)

    fun decode(raw: String): NoteDocument {
        if (raw.isBlank()) return NoteDocument()
        return runCatching { json.decodeFromString<NoteDocument>(raw) }
            .getOrElse { NoteDocument(text = raw) }
    }

    fun plainText(document: NoteDocument): String {
        val builder = StringBuilder(document.text)
        document.tables.forEach { table ->
            builder.append('\n')
            table.cells.forEach { row ->
                builder.append(row.joinToString(" | "))
                builder.append('\n')
            }
        }
        if (document.trailing.isNotEmpty()) {
            if (builder.isNotEmpty() && !builder.endsWith('\n')) builder.append('\n')
            builder.append(document.trailing)
        }
        return builder.toString()
    }

    /**
     * Tables are attachments drawn after [NoteDocument.text]. Text typed under
     * them lives in [NoteDocument.trailing] so the body editor's marks stay put.
     * Deleting the last table joins that paragraph back onto the body.
     */
    fun withoutTable(document: NoteDocument, id: String): NoteDocument {
        return settle(document.copy(tables = document.tables.filterNot { it.id == id }))
    }

    fun settle(document: NoteDocument): NoteDocument {
        if (document.tables.isNotEmpty() || document.trailing.isEmpty()) return document
        return document.copy(
            text = joinParagraphs(document.text, document.trailing),
            trailing = "",
        )
    }

    fun searchBlob(title: String, document: NoteDocument): String {
        return (title + "\n" + plainText(document))
            .lowercase(Locale.getDefault())
    }

    fun preview(document: NoteDocument, maxChars: Int = 96): String {
        val chars = document.text.toCharArray()
        document.marks.filter { it.style == MarkStyle.HIDDEN }.forEach { mark ->
            val from = mark.start.coerceIn(0, chars.size)
            val to = mark.end.coerceIn(0, chars.size)
            for (index in from until to) {
                if (chars[index] != '\n') chars[index] = '▒'
            }
        }
        val flat = joinParagraphs(String(chars), document.trailing)
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        val clipped = if (flat.length > maxChars) flat.take(maxChars).trimEnd() + "…" else flat
        val suffix = if (document.tables.isNotEmpty()) "  ▦" else ""
        return (clipped + suffix).ifBlank { "Пустая заметка" }
    }

    private fun joinParagraphs(body: String, trailing: String): String {
        if (trailing.isEmpty()) return body
        if (body.isEmpty()) return trailing
        if (body.endsWith('\n') || trailing.startsWith('\n')) return body + trailing
        return body + "\n" + trailing
    }
}

object NoteTables {
    const val MAX_ROWS = 8
    const val MAX_COLS = 6

    fun blank(rows: Int, cols: Int): List<List<String>> {
        val safeRows = rows.coerceIn(1, MAX_ROWS)
        val safeCols = cols.coerceIn(1, MAX_COLS)
        return List(safeRows) { List(safeCols) { "" } }
    }

    fun normalized(cells: List<List<String>>): List<List<String>> {
        val width = cells.maxOfOrNull { it.size } ?: 0
        if (width == 0) return emptyList()
        return cells.map { row -> List(width) { index -> row.getOrElse(index) { "" } } }
    }

    fun addRow(cells: List<List<String>>): List<List<String>> {
        val rows = normalized(cells)
        if (rows.size >= MAX_ROWS) return rows
        val cols = (rows.firstOrNull()?.size ?: 1).coerceAtLeast(1)
        return rows + listOf(List(cols) { "" })
    }

    fun addColumn(cells: List<List<String>>): List<List<String>> {
        val rows = normalized(cells)
        val width = rows.firstOrNull()?.size ?: 0
        if (width >= MAX_COLS) return rows
        if (rows.isEmpty()) return listOf(listOf(""))
        return rows.map { it + "" }
    }

    fun removeRow(cells: List<List<String>>): List<List<String>> {
        val rows = normalized(cells)
        if (rows.size <= 1) return rows
        return rows.dropLast(1)
    }

    fun removeColumn(cells: List<List<String>>): List<List<String>> {
        val rows = normalized(cells)
        val width = rows.firstOrNull()?.size ?: 0
        if (width <= 1) return rows
        return rows.map { it.dropLast(1) }
    }
}

object MarkEdits {
    fun shift(marks: List<TextMark>, before: String, after: String): List<TextMark> {
        if (before == after) return marks
        val prefix = commonPrefix(before, after)
        val suffix = commonSuffix(before, after, prefix)
        val oldEnd = before.length - suffix
        val newEnd = after.length - suffix
        return marks.mapNotNull { mark ->
            val start = mapIndex(mark.start, prefix, oldEnd, newEnd)
            val end = mapIndex(mark.end, prefix, oldEnd, newEnd)
            if (end > start) mark.copy(start = start, end = end) else null
        }
    }

    fun toggle(marks: List<TextMark>, style: MarkStyle, selStart: Int, selEnd: Int): List<TextMark> {
        if (selEnd <= selStart) return marks
        val covers = marks.any { mark ->
            mark.style == style && mark.start <= selStart && mark.end >= selEnd
        }
        val next = if (covers) {
            marks.flatMap { mark ->
                if (mark.style != style || mark.end <= selStart || mark.start >= selEnd) {
                    listOf(mark)
                } else {
                    buildList {
                        if (mark.start < selStart) add(mark.copy(end = selStart))
                        if (mark.end > selEnd) add(mark.copy(start = selEnd))
                    }
                }
            }
        } else {
            marks + TextMark(selStart, selEnd, style)
        }
        return merge(next)
    }

    private fun merge(marks: List<TextMark>): List<TextMark> {
        return marks
            .filter { it.end > it.start }
            .groupBy { it.style }
            .flatMap { (_, group) ->
                val sorted = group.sortedBy { it.start }
                val merged = mutableListOf<TextMark>()
                for (mark in sorted) {
                    val last = merged.lastOrNull()
                    if (last != null && mark.start <= last.end) {
                        merged[merged.lastIndex] = last.copy(end = maxOf(last.end, mark.end))
                    } else {
                        merged += mark
                    }
                }
                merged
            }
    }

    private fun commonPrefix(left: String, right: String): Int {
        val limit = minOf(left.length, right.length)
        var index = 0
        while (index < limit && left[index] == right[index]) index++
        return index
    }

    private fun commonSuffix(left: String, right: String, prefix: Int): Int {
        var suffix = 0
        while (
            suffix < left.length - prefix &&
            suffix < right.length - prefix &&
            left[left.length - 1 - suffix] == right[right.length - 1 - suffix]
        ) {
            suffix++
        }
        return suffix
    }

    private fun mapIndex(index: Int, start: Int, oldEnd: Int, newEnd: Int): Int {
        return when {
            index <= start -> index
            index >= oldEnd -> index + (newEnd - oldEnd)
            else -> newEnd
        }
    }
}
