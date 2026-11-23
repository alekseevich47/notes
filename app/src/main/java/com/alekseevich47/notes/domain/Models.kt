package com.alekseevich47.notes.domain

import androidx.compose.runtime.Immutable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import java.util.Locale

enum class MicPhase { IDLE, RECORDING, PROCESSING, DONE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class MarkStyle { BOLD, ITALIC, STRIKE, UNDERLINE, HIDDEN }

/**
 * Unknown style names deserialize as null and are dropped from the mark list.
 * Known names still encode as the enum constant so existing files stay valid.
 */
internal object TolerantTextMarkSerializer : KSerializer<TextMark?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TextMark") {
        element<Int>("start")
        element<Int>("end")
        element<String>("style")
    }

    override fun serialize(encoder: Encoder, value: TextMark?) {
        val mark = requireNotNull(value)
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, mark.start)
            encodeIntElement(descriptor, 1, mark.end)
            encodeStringElement(descriptor, 2, mark.style.name)
        }
    }

    override fun deserialize(decoder: Decoder): TextMark? {
        var start = 0
        var end = 0
        var style: MarkStyle? = null
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> start = decodeIntElement(descriptor, 0)
                    1 -> end = decodeIntElement(descriptor, 1)
                    2 -> style = markStyleOrNull(decodeStringElement(descriptor, 2))
                    CompositeDecoder.DECODE_DONE -> break
                    else -> break
                }
            }
        }
        val resolved = style ?: return null
        return TextMark(start, end, resolved)
    }
}

internal object TextMarkListSerializer : KSerializer<List<TextMark>> {
    private val elements = ListSerializer(TolerantTextMarkSerializer)
    override val descriptor: SerialDescriptor = elements.descriptor

    override fun serialize(encoder: Encoder, value: List<TextMark>) {
        val nullable: List<TextMark?> = value.map { mark -> mark }
        elements.serialize(encoder, nullable)
    }

    override fun deserialize(decoder: Decoder): List<TextMark> {
        return elements.deserialize(decoder).filterNotNull()
    }
}

private fun markStyleOrNull(name: String): MarkStyle? {
    return MarkStyle.entries.firstOrNull { it.name == name }
}

@Serializable
data class TextMark(
    val start: Int,
    val end: Int,
    val style: MarkStyle,
)

/**
 * 0 left / top, 1 center, 2 right / bottom.
 * Missing align entries read as center. A span of 0 is a cell covered by a merge.
 */
@Serializable
data class CellMarks(
    val row: Int = 0,
    val col: Int = 0,
    @Serializable(with = TextMarkListSerializer::class)
    val marks: List<TextMark> = emptyList(),
)

@Serializable
data class NoteTable(
    val id: String,
    val cells: List<List<String>> = emptyList(),
    /** Typed by the user. Empty shows the placeholder, never a generated title. */
    val caption: String = "",
    val alignH: List<List<Int>> = emptyList(),
    val alignV: List<List<Int>> = emptyList(),
    val fill: List<List<Boolean>> = emptyList(),
    val spanCol: List<List<Int>> = emptyList(),
    val spanRow: List<List<Int>> = emptyList(),
    val cellMarks: List<CellMarks> = emptyList(),
)

@Serializable
enum class BlockKind { TEXT, TABLE }

/**
 * One piece of a note. Empty [blocks] on [NoteDocument] means the legacy shape:
 * body text, then each table, then [NoteDocument.trailing].
 */
@Serializable
data class NoteBlock(
    val kind: BlockKind = BlockKind.TEXT,
    val text: String = "",
    @Serializable(with = TextMarkListSerializer::class)
    val marks: List<TextMark> = emptyList(),
    val table: NoteTable? = null,
)

@Serializable
data class NoteDocument(
    val text: String = "",
    @Serializable(with = TextMarkListSerializer::class)
    val marks: List<TextMark> = emptyList(),
    val tables: List<NoteTable> = emptyList(),
    /** Paragraph under the last table. Folded back into [text] when no tables remain. */
    val trailing: String = "",
    /**
     * Interleaved text and tables. Empty for notes saved before blocks existed,
     * and for notes that still fit body + tables + trailing.
     */
    val blocks: List<NoteBlock> = emptyList(),
)

data class PreviewPart(
    val text: String,
    val hidden: Boolean,
)

@Immutable
data class NoteListItem(
    val id: Long,
    val title: String,
    val preview: List<PreviewPart>,
    val updatedAt: Long,
    val hasAudio: Boolean,
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

/** Caret in [NoteDocuments.flow] after a structural edit. */
data class BlockCaret(val blockIndex: Int, val offset: Int)

object NoteDocuments {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** [unreadable] is a `{` payload that still failed. The body is not the raw JSON. */
    data class Decoded(
        val document: NoteDocument,
        val unreadable: Boolean,
    )

    fun encode(document: NoteDocument): String = json.encodeToString(document)

    fun read(raw: String): Decoded {
        if (raw.isBlank()) return Decoded(NoteDocument(), unreadable = false)
        return try {
            Decoded(json.decodeFromString<NoteDocument>(raw), unreadable = false)
        } catch (_: Exception) {
            if (raw.startsWith("{")) Decoded(NoteDocument(), unreadable = true)
            else Decoded(NoteDocument(text = raw), unreadable = false)
        }
    }

    fun decode(raw: String): NoteDocument = read(raw).document

    fun plainText(document: NoteDocument): String {
        if (document.blocks.isNotEmpty()) {
            val builder = StringBuilder()
            document.blocks.forEach { block ->
                when (block.kind) {
                    BlockKind.TEXT -> appendParagraph(builder, block.text)
                    BlockKind.TABLE -> block.table?.let { appendTable(builder, it) }
                }
            }
            return builder.toString()
        }
        val builder = StringBuilder(document.text)
        document.tables.forEach { table -> appendTable(builder, table) }
        if (document.trailing.isNotEmpty()) {
            if (builder.isNotEmpty() && !builder.endsWith('\n')) builder.append('\n')
            builder.append(document.trailing)
        }
        return builder.toString()
    }

    /**
     * Legacy notes keep body text, then tables, then [NoteDocument.trailing].
     * A table inserted between paragraphs is stored in [NoteDocument.blocks]
     * when that no longer fits (text between tables, or marks on the tail).
     */
    fun withoutTable(document: NoteDocument, id: String): NoteDocument {
        if (document.blocks.isEmpty()) {
            return settle(document.copy(tables = document.tables.filterNot { it.id == id }))
        }
        val next = document.blocks.filterNot { it.kind == BlockKind.TABLE && it.table?.id == id }
        return pack(next)
    }

    /**
     * Backspace at the start of the text block under a table removes that table.
     * [textIndex] is that text block in [flow]. The offset is the start of the
     * paragraph that sat under the table; legacy notes insert a newline when the
     * paragraphs join, and the caret stays on the following text.
     */
    fun caretAfterRemovingTable(document: NoteDocument, textIndex: Int): BlockCaret? {
        val before = flow(document)
        val following = before.getOrNull(textIndex) ?: return null
        if (following.kind != BlockKind.TEXT) return null
        val tableIndex = textIndex - 1
        val table = before.getOrNull(tableIndex) ?: return null
        if (table.kind != BlockKind.TABLE) return null
        val tableId = table.table?.id ?: return null
        val previous = before.getOrNull(tableIndex - 1)?.takeIf { it.kind == BlockKind.TEXT }
        val anchorOriginal = if (previous != null) tableIndex - 1 else textIndex
        val anchorInRemaining = if (anchorOriginal > tableIndex) anchorOriginal - 1 else anchorOriginal
        val boundary = previous?.text?.length ?: 0

        data class Node(var block: NoteBlock, val sources: MutableList<Int>)
        val merged = mutableListOf<Node>()
        before.filterIndexed { index, _ -> index != tableIndex }.forEachIndexed { index, block ->
            val prior = merged.lastOrNull()
            if (block.kind == BlockKind.TEXT && prior != null && prior.block.kind == BlockKind.TEXT) {
                val shift = prior.block.text.length
                val marks = prior.block.marks + block.marks.map { mark ->
                    mark.copy(start = mark.start + shift, end = mark.end + shift)
                }
                prior.block = prior.block.copy(text = prior.block.text + block.text, marks = marks)
                prior.sources += index
            } else {
                merged += Node(block, mutableListOf(index))
            }
        }
        val kept = merged.filter { node ->
            node.block.kind == BlockKind.TABLE || node.block.text.isNotEmpty() || node.block.marks.isNotEmpty()
        }.ifEmpty {
            listOf(Node(NoteBlock(kind = BlockKind.TEXT), mutableListOf(anchorInRemaining)))
        }
        var blockIndex = kept.indexOfFirst { anchorInRemaining in it.sources }
        if (blockIndex < 0) {
            blockIndex = kept.indexOfFirst { it.block.kind == BlockKind.TEXT }.coerceAtLeast(0)
        }
        val simulated = kept.getOrNull(blockIndex)?.block?.text.orEmpty()
        val actualBlocks = flow(withoutTable(document, tableId))
        val resolvedIndex = blockIndex.coerceIn(0, (actualBlocks.size - 1).coerceAtLeast(0))
        val actual = actualBlocks.getOrNull(resolvedIndex)?.text.orEmpty()
        val offset = if (
            boundary < actual.length &&
            actual.getOrNull(boundary) == '\n' &&
            actual.removeRange(boundary, boundary + 1) == simulated
        ) {
            boundary + 1
        } else {
            boundary.coerceIn(0, actual.length)
        }
        return BlockCaret(resolvedIndex, offset)
    }

    /** Reading order. Legacy notes gain a text block under the tables so the caret can sit there. */
    fun flow(document: NoteDocument): List<NoteBlock> {
        if (document.blocks.isNotEmpty()) return document.blocks
        val out = mutableListOf<NoteBlock>()
        out += NoteBlock(kind = BlockKind.TEXT, text = document.text, marks = document.marks)
        document.tables.forEach { table ->
            out += NoteBlock(kind = BlockKind.TABLE, table = table)
        }
        if (document.tables.isNotEmpty()) {
            out += NoteBlock(kind = BlockKind.TEXT, text = document.trailing)
        }
        return out
    }

    /**
     * Writes [blocks] back. Body + tables + unmarked tail stays on the legacy
     * fields so older notes round-trip without a block list.
     */
    fun pack(blocks: List<NoteBlock>): NoteDocument {
        val compact = compactBlocks(blocks)
        if (isLegacyShape(compact)) return toLegacy(compact)
        val texts = compact.filter { it.kind == BlockKind.TEXT }
        val tables = compact.mapNotNull { if (it.kind == BlockKind.TABLE) it.table else null }
        val first = texts.firstOrNull()
        val trailing = texts.drop(1).joinToString("\n") { it.text }
        return NoteDocument(
            text = first?.text ?: "",
            marks = first?.marks ?: emptyList(),
            tables = tables,
            trailing = trailing,
            blocks = compact,
        )
    }

    fun insertTable(
        document: NoteDocument,
        blockIndex: Int,
        cursor: Int,
        rows: Int,
        cols: Int,
        id: String,
    ): NoteDocument {
        val blocks = flow(document).toMutableList()
        if (blocks.isEmpty()) blocks += NoteBlock(kind = BlockKind.TEXT)
        val table = NoteBlock(
            kind = BlockKind.TABLE,
            table = NoteTable(id = id, cells = NoteTables.blank(rows, cols)),
        )
        val index = blockIndex.coerceIn(0, blocks.lastIndex)
        val block = blocks[index]
        if (cursor < 0 || block.kind != BlockKind.TEXT) {
            val at = (index + 1).coerceAtMost(blocks.size)
            blocks.add(at, table)
            if (blocks.getOrNull(at + 1)?.kind != BlockKind.TEXT) {
                blocks.add(at + 1, NoteBlock(kind = BlockKind.TEXT))
            }
            return pack(blocks)
        }
        val cut = cursor.coerceIn(0, block.text.length)
        val before = NoteBlock(
            kind = BlockKind.TEXT,
            text = block.text.substring(0, cut),
            marks = clipMarks(block.marks, 0, cut),
        )
        val after = NoteBlock(
            kind = BlockKind.TEXT,
            text = block.text.substring(cut),
            marks = clipMarks(block.marks, cut, block.text.length),
        )
        blocks[index] = before
        blocks.add(index + 1, table)
        blocks.add(index + 2, after)
        return pack(blocks)
    }

    /**
     * True when the loaded row already has this title, document, and audio.
     * Compared after decode so a missing default key is not a new edit.
     */
    fun sameStored(
        storedTitle: String,
        storedBody: String,
        storedAudio: String?,
        title: String,
        document: NoteDocument,
        audioPath: String?,
    ): Boolean {
        if (storedTitle != title || storedAudio != audioPath) return false
        return decode(storedBody) == document
    }

    fun settle(document: NoteDocument): NoteDocument {
        if (document.tables.isNotEmpty() || document.trailing.isEmpty()) return document
        return document.copy(
            text = joinParagraphs(document.text, document.trailing),
            trailing = "",
        )
    }

    fun searchBlob(title: String, document: NoteDocument): String {
        return (title + "\n" + visiblePlain(document))
            .lowercase(Locale.getDefault())
    }

    /** Title and visible text. HIDDEN characters are removed, not masked. */
    private fun visiblePlain(document: NoteDocument): String {
        val builder = StringBuilder()
        for (block in flow(document)) {
            when (block.kind) {
                BlockKind.TEXT -> appendParagraph(builder, withoutHidden(block.text, block.marks))
                BlockKind.TABLE -> block.table?.let { appendVisibleTable(builder, it) }
            }
        }
        return builder.toString()
    }

    private fun appendVisibleTable(builder: StringBuilder, table: NoteTable) {
        if (table.caption.isNotBlank()) {
            if (builder.isNotEmpty() && !builder.endsWith('\n')) builder.append('\n')
            builder.append(table.caption)
        }
        table.cells.forEachIndexed { row, cells ->
            if (builder.isNotEmpty() && !builder.endsWith('\n')) builder.append('\n')
            val visible = cells.mapIndexed { col, cell ->
                withoutHidden(cell, NoteTables.cellMarks(table, row, col))
            }
            builder.append(visible.joinToString(" | "))
        }
        if (table.cells.isNotEmpty() && !builder.endsWith('\n')) builder.append('\n')
    }

    private fun withoutHidden(text: String, marks: List<TextMark>): String {
        if (text.isEmpty() || marks.none { it.style == MarkStyle.HIDDEN && it.end > it.start }) return text
        val hidden = BooleanArray(text.length)
        for (mark in marks) {
            if (mark.style != MarkStyle.HIDDEN) continue
            val from = mark.start.coerceIn(0, text.length)
            val to = mark.end.coerceIn(from, text.length)
            for (index in from until to) hidden[index] = true
        }
        val out = StringBuilder(text.length)
        for (index in text.indices) {
            if (!hidden[index]) out.append(text[index])
        }
        return out.toString()
    }

    fun preview(document: NoteDocument, maxChars: Int = 96): String {
        if (document.blocks.isNotEmpty()) {
            return previewFromParts(previewParts(document, maxChars))
        }
        val chars = document.text.toCharArray()
        maskHidden(chars, document.marks)
        val flat = joinParagraphs(String(chars), document.trailing)
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        val clipped = if (flat.length > maxChars) flat.take(maxChars).trimEnd() + "…" else flat
        val suffix = if (document.tables.isNotEmpty()) "  ▦" else ""
        return (clipped + suffix).ifBlank { "Пустая заметка" }
    }

    /**
     * List preview pieces. Hidden characters stay real glyphs (the card blurs them);
     * [preview] still masks with ▒ for the plain string.
     */
    fun previewParts(document: NoteDocument, maxChars: Int = 96): List<PreviewPart> {
        val chars = StringBuilder()
        val flags = ArrayList<Boolean>()
        val blocks = flow(document)
        var wrote = false
        for (block in blocks) {
            if (block.kind != BlockKind.TEXT || block.text.isEmpty()) continue
            if (wrote && chars.isNotEmpty() && !chars.endsWith(' ')) {
                chars.append(' ')
                flags.add(false)
            }
            block.text.forEachIndexed { index, ch ->
                chars.append(if (ch == '\n') ' ' else ch)
                flags.add(ch != '\n' && covers(block.marks, index))
            }
            wrote = true
        }
        val collapsedChars = StringBuilder()
        val collapsedFlags = ArrayList<Boolean>()
        var previousSpace = true
        for (index in chars.indices) {
            val space = chars[index].isWhitespace()
            if (space) {
                if (previousSpace) continue
                collapsedChars.append(' ')
                collapsedFlags.add(false)
                previousSpace = true
            } else {
                collapsedChars.append(chars[index])
                collapsedFlags.add(flags[index])
                previousSpace = false
            }
        }
        if (collapsedChars.endsWith(' ')) {
            collapsedChars.setLength(collapsedChars.length - 1)
            collapsedFlags.removeAt(collapsedFlags.lastIndex)
        }
        val hasTable = blocks.any { it.kind == BlockKind.TABLE }
        if (collapsedChars.isEmpty()) {
            return if (hasTable) listOf(PreviewPart("▦", false)) else listOf(PreviewPart("Пустая заметка", false))
        }
        val clipped = collapsedChars.length > maxChars
        val end = if (clipped) maxChars else collapsedChars.length
        val parts = groupParts(collapsedChars.substring(0, end), collapsedFlags.subList(0, end)).toMutableList()
        if (clipped && parts.isNotEmpty()) {
            val last = parts.last()
            parts[parts.lastIndex] = last.copy(text = last.text.trimEnd() + "…")
        }
        if (hasTable) parts += PreviewPart("  ▦", false)
        return parts
    }

    private fun previewFromParts(parts: List<PreviewPart>): String {
        val flat = buildString {
            parts.forEach { part ->
                if (part.hidden) {
                    part.text.forEach { ch -> append(if (ch == '\n') ch else '▒') }
                } else {
                    append(part.text)
                }
            }
        }.trim()
        return flat.ifBlank { "Пустая заметка" }
    }

    private fun maskHidden(chars: CharArray, marks: List<TextMark>) {
        marks.filter { it.style == MarkStyle.HIDDEN }.forEach { mark ->
            val from = mark.start.coerceIn(0, chars.size)
            val to = mark.end.coerceIn(0, chars.size)
            for (index in from until to) {
                if (chars[index] != '\n') chars[index] = '▒'
            }
        }
    }

    private fun covers(marks: List<TextMark>, index: Int): Boolean {
        return marks.any { it.style == MarkStyle.HIDDEN && index >= it.start && index < it.end }
    }

    private fun groupParts(text: String, flags: List<Boolean>): List<PreviewPart> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<PreviewPart>()
        var start = 0
        for (index in 1..text.length) {
            if (index == text.length || flags[index] != flags[start]) {
                out += PreviewPart(text.substring(start, index), flags[start])
                start = index
            }
        }
        return out
    }

    private fun appendParagraph(builder: StringBuilder, text: String) {
        if (text.isEmpty()) return
        if (builder.isNotEmpty() && !builder.endsWith('\n')) builder.append('\n')
        builder.append(text)
    }

    private fun appendTable(builder: StringBuilder, table: NoteTable) {
        if (table.caption.isNotBlank()) {
            if (builder.isNotEmpty() && !builder.endsWith('\n')) builder.append('\n')
            builder.append(table.caption)
        }
        table.cells.forEach { row ->
            if (builder.isNotEmpty() && !builder.endsWith('\n')) builder.append('\n')
            builder.append(row.joinToString(" | "))
        }
        if (table.cells.isNotEmpty() && !builder.endsWith('\n')) builder.append('\n')
        if (table.cells.isNotEmpty() && builder.isNotEmpty() && !builder.endsWith('\n')) builder.append('\n')
    }

    private fun clipMarks(marks: List<TextMark>, start: Int, end: Int): List<TextMark> {
        return marks.mapNotNull { mark ->
            val from = maxOf(mark.start, start)
            val to = minOf(mark.end, end)
            if (to <= from) null else mark.copy(start = from - start, end = to - start)
        }
    }

    private fun compactBlocks(blocks: List<NoteBlock>): List<NoteBlock> {
        val merged = mutableListOf<NoteBlock>()
        for (block in blocks) {
            val previous = merged.lastOrNull()
            if (block.kind == BlockKind.TEXT && previous != null && previous.kind == BlockKind.TEXT) {
                val shift = previous.text.length
                val marks = previous.marks + block.marks.map { mark ->
                    mark.copy(start = mark.start + shift, end = mark.end + shift)
                }
                merged[merged.lastIndex] = previous.copy(text = previous.text + block.text, marks = marks)
            } else {
                merged += block
            }
        }
        val kept = merged.filter { block ->
            block.kind == BlockKind.TABLE || block.text.isNotEmpty() || block.marks.isNotEmpty()
        }
        return if (kept.isEmpty()) listOf(NoteBlock(kind = BlockKind.TEXT)) else kept
    }

    private fun isLegacyShape(blocks: List<NoteBlock>): Boolean {
        if (blocks.isEmpty()) return true
        var index = 0
        if (blocks[0].kind == BlockKind.TEXT) index = 1
        var sawTable = false
        while (index < blocks.size && blocks[index].kind == BlockKind.TABLE && blocks[index].table != null) {
            sawTable = true
            index++
        }
        if (index >= blocks.size) return true
        val tail = blocks[index]
        if (tail.kind != BlockKind.TEXT || tail.marks.isNotEmpty()) return false
        if (index != blocks.lastIndex) return false
        return sawTable
    }

    private fun toLegacy(blocks: List<NoteBlock>): NoteDocument {
        val firstText = blocks.firstOrNull()?.takeIf { it.kind == BlockKind.TEXT }
        val tables = blocks.mapNotNull { block -> if (block.kind == BlockKind.TABLE) block.table else null }
        val last = blocks.lastOrNull()
        val trailing = if (
            tables.isNotEmpty() &&
            last != null &&
            last.kind == BlockKind.TEXT &&
            last !== firstText
        ) {
            last.text
        } else {
            ""
        }
        return NoteDocument(
            text = firstText?.text ?: "",
            marks = firstText?.marks ?: emptyList(),
            tables = tables,
            trailing = trailing,
        )
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

    fun rowCount(table: NoteTable): Int = normalized(table.cells).size

    fun columnCount(table: NoteTable): Int = normalized(table.cells).firstOrNull()?.size ?: 0

    fun canInsertRow(table: NoteTable): Boolean = rowCount(table) < MAX_ROWS

    fun canInsertColumn(table: NoteTable): Boolean = columnCount(table) < MAX_COLS

    fun canDeleteRow(table: NoteTable): Boolean = rowCount(table) > 1

    fun canDeleteColumn(table: NoteTable): Boolean = columnCount(table) > 1

    fun alignH(table: NoteTable, row: Int, col: Int): Int = table.alignH.getOrNull(row)?.getOrNull(col) ?: 1

    fun alignV(table: NoteTable, row: Int, col: Int): Int = table.alignV.getOrNull(row)?.getOrNull(col) ?: 1

    fun filled(table: NoteTable, row: Int, col: Int): Boolean = table.fill.getOrNull(row)?.getOrNull(col) == true

    fun colSpan(table: NoteTable, row: Int, col: Int): Int = table.spanCol.getOrNull(row)?.getOrNull(col) ?: 1

    fun rowSpan(table: NoteTable, row: Int, col: Int): Int = table.spanRow.getOrNull(row)?.getOrNull(col) ?: 1

    fun covered(table: NoteTable, row: Int, col: Int): Boolean {
        return colSpan(table, row, col) == 0 || rowSpan(table, row, col) == 0
    }

    fun cellMarks(table: NoteTable, row: Int, col: Int): List<TextMark> {
        return table.cellMarks.firstOrNull { it.row == row && it.col == col }?.marks.orEmpty()
    }

    fun withCell(table: NoteTable, row: Int, col: Int, text: String): NoteTable {
        val rows = normalized(table.cells).map { it.toMutableList() }
        if (row !in rows.indices || col !in rows[row].indices) return table
        val previous = rows[row][col]
        if (previous == text) return table
        rows[row][col] = text
        val marks = MarkEdits.shift(cellMarks(table, row, col), previous, text)
        return table.copy(cells = rows.map { it.toList() }, cellMarks = putMarks(table.cellMarks, row, col, marks))
    }

    fun withCellMarks(table: NoteTable, row: Int, col: Int, marks: List<TextMark>): NoteTable {
        return table.copy(cellMarks = putMarks(table.cellMarks, row, col, marks))
    }

    fun withCaption(table: NoteTable, caption: String): NoteTable {
        if (table.caption == caption) return table
        return table.copy(caption = caption)
    }

    fun alignRow(table: NoteTable, row: Int, horizontal: Int, vertical: Int): NoteTable {
        val rows = normalized(table.cells)
        if (row !in rows.indices) return table
        return table.copy(
            cells = rows,
            alignH = writeIntRow(table.alignH, rows, row) { _, _ -> horizontal },
            alignV = writeIntRow(table.alignV, rows, row) { _, _ -> vertical },
        )
    }

    fun alignColumn(table: NoteTable, col: Int, horizontal: Int, vertical: Int): NoteTable {
        val rows = normalized(table.cells)
        val width = rows.firstOrNull()?.size ?: 0
        if (col !in 0 until width) return table
        return table.copy(
            cells = rows,
            alignH = writeIntColumn(table.alignH, rows, col) { horizontal },
            alignV = writeIntColumn(table.alignV, rows, col) { vertical },
        )
    }

    fun alignCell(table: NoteTable, row: Int, col: Int, horizontal: Int, vertical: Int): NoteTable {
        val rows = normalized(table.cells)
        if (row !in rows.indices || col !in rows[row].indices) return table
        return table.copy(
            cells = rows,
            alignH = writeIntRow(table.alignH, rows, row) { c, current -> if (c == col) horizontal else current },
            alignV = writeIntRow(table.alignV, rows, row) { c, current -> if (c == col) vertical else current },
        )
    }

    fun fillRow(table: NoteTable, row: Int, filled: Boolean): NoteTable {
        val rows = normalized(table.cells)
        if (row !in rows.indices) return table
        return table.copy(cells = rows, fill = writeBoolRow(table.fill, rows, row) { _, _ -> filled })
    }

    fun fillColumn(table: NoteTable, col: Int, filled: Boolean): NoteTable {
        val rows = normalized(table.cells)
        val width = rows.firstOrNull()?.size ?: 0
        if (col !in 0 until width) return table
        return table.copy(cells = rows, fill = writeBoolColumn(table.fill, rows, col) { filled })
    }

    fun fillCell(table: NoteTable, row: Int, col: Int, filled: Boolean): NoteTable {
        val rows = normalized(table.cells)
        if (row !in rows.indices || col !in rows[row].indices) return table
        return table.copy(
            cells = rows,
            fill = writeBoolRow(table.fill, rows, row) { c, current -> if (c == col) filled else current },
        )
    }

    fun rowFilled(table: NoteTable, row: Int): Boolean {
        val width = columnCount(table)
        if (width == 0) return false
        return (0 until width).all { col -> covered(table, row, col) || filled(table, row, col) }
    }

    fun columnFilled(table: NoteTable, col: Int): Boolean {
        val height = rowCount(table)
        if (height == 0) return false
        return (0 until height).all { row -> covered(table, row, col) || filled(table, row, col) }
    }

    fun mergeRow(table: NoteTable, row: Int): NoteTable {
        val rows = normalized(table.cells).map { it.toMutableList() }
        if (row !in rows.indices) return table
        val width = rows[row].size
        if (width <= 1) return table
        val anchor = (0 until width).firstOrNull { !covered(table, row, it) } ?: return table
        val span = colSpan(table, row, anchor)
        if (anchor == 0 && span >= width) return table
        val text = (0 until width).mapNotNull { col ->
            if (covered(table, row, col)) null else rows[row][col].takeIf { it.isNotBlank() }
        }.joinToString(" ")
        for (col in 0 until width) rows[row][col] = if (col == anchor) text else ""
        val spanC = intGrid(table.spanCol, rows.size, width)
        val spanR = intGrid(table.spanRow, rows.size, width)
        spanC[row][anchor] = width - anchor
        for (col in anchor + 1 until width) {
            spanC[row][col] = 0
            spanR[row][col] = 0
        }
        val marks = table.cellMarks.filterNot { it.row == row && it.col != anchor }
        return table.copy(
            cells = rows.map { it.toList() },
            spanCol = spanC.map { it.toList() },
            spanRow = spanR.map { it.toList() },
            cellMarks = marks,
        )
    }

    fun mergeColumn(table: NoteTable, col: Int): NoteTable {
        val rows = normalized(table.cells).map { it.toMutableList() }
        val height = rows.size
        val width = rows.firstOrNull()?.size ?: 0
        if (col !in 0 until width || height <= 1) return table
        val anchor = (0 until height).firstOrNull { !covered(table, it, col) } ?: return table
        val span = rowSpan(table, anchor, col)
        if (anchor == 0 && span >= height) return table
        val text = (0 until height).mapNotNull { row ->
            if (covered(table, row, col)) null else rows[row][col].takeIf { it.isNotBlank() }
        }.joinToString(" ")
        for (row in 0 until height) rows[row][col] = if (row == anchor) text else ""
        val spanC = intGrid(table.spanCol, height, width)
        val spanR = intGrid(table.spanRow, height, width)
        spanR[anchor][col] = height - anchor
        for (row in anchor + 1 until height) {
            spanC[row][col] = 0
            spanR[row][col] = 0
        }
        val marks = table.cellMarks.filterNot { it.col == col && it.row != anchor }
        return table.copy(
            cells = rows.map { it.toList() },
            spanCol = spanC.map { it.toList() },
            spanRow = spanR.map { it.toList() },
            cellMarks = marks,
        )
    }

    fun insertRow(table: NoteTable, index: Int): NoteTable {
        val rows = normalized(table.cells).map { it.toMutableList() }.toMutableList()
        if (rows.isEmpty() || rows.size >= MAX_ROWS) return table
        val width = rows.first().size
        val at = index.coerceIn(0, rows.size)
        val spanC = intGrid(table.spanCol, rows.size, width)
        val spanR = intGrid(table.spanRow, rows.size, width)
        val freshC = MutableList(width) { 1 }
        val freshR = MutableList(width) { 1 }
        for (row in 0 until at) {
            for (col in 0 until width) {
                val span = spanR[row][col]
                if (span > 1 && row + span > at) {
                    spanR[row][col] = span + 1
                    freshC[col] = 0
                    freshR[col] = 0
                }
            }
        }
        rows.add(at, MutableList(width) { "" })
        spanC.add(at, freshC)
        spanR.add(at, freshR)
        val alignH = insertDefaultRow(intGrid(table.alignH, rows.size - 1, width, 1), at, width, 1)
        val alignV = insertDefaultRow(intGrid(table.alignV, rows.size - 1, width, 1), at, width, 1)
        val fill = insertDefaultRow(boolGrid(table.fill, rows.size - 1, width), at, width, false)
        // rows.size already includes the new row; grids above were built from the old height.
        return table.copy(
            cells = rows.map { it.toList() },
            alignH = packDefaultInt(alignH, 1),
            alignV = packDefaultInt(alignV, 1),
            fill = packDefaultBool(fill),
            spanCol = packDefaultInt(spanC, 1),
            spanRow = packDefaultInt(spanR, 1),
            cellMarks = table.cellMarks.map { if (it.row >= at) it.copy(row = it.row + 1) else it },
        )
    }

    fun insertColumn(table: NoteTable, index: Int): NoteTable {
        val rows = normalized(table.cells).map { it.toMutableList() }
        if (rows.isEmpty()) return table.copy(cells = listOf(listOf("")))
        val width = rows.first().size
        if (width >= MAX_COLS) return table
        val at = index.coerceIn(0, width)
        val height = rows.size
        val spanC = intGrid(table.spanCol, height, width)
        val spanR = intGrid(table.spanRow, height, width)
        rows.forEachIndexed { row, line ->
            var covered = false
            for (col in 0 until at) {
                val span = spanC[row][col]
                if (span > 1 && col + span > at) {
                    spanC[row][col] = span + 1
                    covered = true
                }
            }
            line.add(at, "")
            spanC[row].add(at, if (covered) 0 else 1)
            spanR[row].add(at, if (covered) 0 else 1)
        }
        val alignH = insertDefaultColumn(intGrid(table.alignH, height, width, 1), at, 1)
        val alignV = insertDefaultColumn(intGrid(table.alignV, height, width, 1), at, 1)
        val fill = insertDefaultColumn(boolGrid(table.fill, height, width), at, false)
        return table.copy(
            cells = rows.map { it.toList() },
            alignH = packDefaultInt(alignH, 1),
            alignV = packDefaultInt(alignV, 1),
            fill = packDefaultBool(fill),
            spanCol = packDefaultInt(spanC, 1),
            spanRow = packDefaultInt(spanR, 1),
            cellMarks = table.cellMarks.map { mark -> if (mark.col >= at) mark.copy(col = mark.col + 1) else mark },
        )
    }

    fun deleteRow(table: NoteTable, index: Int): NoteTable {
        val rows = normalized(table.cells).map { it.toMutableList() }.toMutableList()
        if (rows.size <= 1 || index !in rows.indices) return table
        val width = rows.first().size
        val spanC = intGrid(table.spanCol, rows.size, width)
        val spanR = intGrid(table.spanRow, rows.size, width)
        for (row in 0 until index) {
            for (col in 0 until width) {
                val span = spanR[row][col]
                if (span > 1 && row + span > index) spanR[row][col] = span - 1
            }
        }
        for (col in 0 until width) {
            val span = spanR[index][col]
            if (span > 1 && index + 1 < rows.size) {
                rows[index + 1][col] = rows[index][col]
                spanR[index + 1][col] = span - 1
                spanC[index + 1][col] = spanC[index][col]
            }
        }
        rows.removeAt(index)
        spanC.removeAt(index)
        spanR.removeAt(index)
        val alignH = intGrid(table.alignH, rows.size + 1, width, 1).also { it.removeAt(index) }
        val alignV = intGrid(table.alignV, rows.size + 1, width, 1).also { it.removeAt(index) }
        val fill = boolGrid(table.fill, rows.size + 1, width).also { it.removeAt(index) }
        val marks = table.cellMarks.mapNotNull { mark ->
            when {
                mark.row == index -> null
                mark.row > index -> mark.copy(row = mark.row - 1)
                else -> mark
            }
        }
        return table.copy(
            cells = rows.map { it.toList() },
            alignH = packDefaultInt(alignH, 1),
            alignV = packDefaultInt(alignV, 1),
            fill = packDefaultBool(fill),
            spanCol = packDefaultInt(spanC, 1),
            spanRow = packDefaultInt(spanR, 1),
            cellMarks = marks,
        )
    }

    fun deleteColumn(table: NoteTable, index: Int): NoteTable {
        val rows = normalized(table.cells).map { it.toMutableList() }
        val width = rows.firstOrNull()?.size ?: 0
        if (width <= 1 || index !in 0 until width) return table
        val height = rows.size
        val spanC = intGrid(table.spanCol, height, width)
        val spanR = intGrid(table.spanRow, height, width)
        for (row in 0 until height) {
            for (col in 0 until index) {
                val span = spanC[row][col]
                if (span > 1 && col + span > index) spanC[row][col] = span - 1
            }
            val span = spanC[row][index]
            if (span > 1 && index + 1 < width) {
                rows[row][index + 1] = rows[row][index]
                spanC[row][index + 1] = span - 1
                spanR[row][index + 1] = spanR[row][index]
            }
            rows[row].removeAt(index)
            spanC[row].removeAt(index)
            spanR[row].removeAt(index)
        }
        val alignH = intGrid(table.alignH, height, width, 1).onEach { it.removeAt(index) }
        val alignV = intGrid(table.alignV, height, width, 1).onEach { it.removeAt(index) }
        val fill = boolGrid(table.fill, height, width).onEach { it.removeAt(index) }
        val marks = table.cellMarks.mapNotNull { mark ->
            when {
                mark.col == index -> null
                mark.col > index -> mark.copy(col = mark.col - 1)
                else -> mark
            }
        }
        return table.copy(
            cells = rows.map { it.toList() },
            alignH = packDefaultInt(alignH, 1),
            alignV = packDefaultInt(alignV, 1),
            fill = packDefaultBool(fill),
            spanCol = packDefaultInt(spanC, 1),
            spanRow = packDefaultInt(spanR, 1),
            cellMarks = marks,
        )
    }

    private fun putMarks(existing: List<CellMarks>, row: Int, col: Int, marks: List<TextMark>): List<CellMarks> {
        val rest = existing.filterNot { it.row == row && it.col == col }
        if (marks.isEmpty()) return rest
        return rest + CellMarks(row, col, marks)
    }

    private fun intGrid(
        source: List<List<Int>>,
        rows: Int,
        cols: Int,
        default: Int = 1,
    ): MutableList<MutableList<Int>> {
        return MutableList(rows) { row ->
            MutableList(cols) { col -> source.getOrNull(row)?.getOrNull(col) ?: default }
        }
    }

    private fun boolGrid(source: List<List<Boolean>>, rows: Int, cols: Int): MutableList<MutableList<Boolean>> {
        return MutableList(rows) { row ->
            MutableList(cols) { col -> source.getOrNull(row)?.getOrNull(col) == true }
        }
    }

    private fun writeIntRow(
        source: List<List<Int>>,
        rows: List<List<String>>,
        row: Int,
        value: (col: Int, current: Int) -> Int,
    ): List<List<Int>> {
        val height = rows.size
        val width = rows.first().size
        return List(height) { r ->
            List(width) { c ->
                val current = source.getOrNull(r)?.getOrNull(c) ?: 1
                if (r == row) value(c, current) else current
            }
        }
    }

    private fun writeIntColumn(
        source: List<List<Int>>,
        rows: List<List<String>>,
        col: Int,
        value: () -> Int,
    ): List<List<Int>> {
        val height = rows.size
        val width = rows.first().size
        return List(height) { r ->
            List(width) { c ->
                val current = source.getOrNull(r)?.getOrNull(c) ?: 1
                if (c == col) value() else current
            }
        }
    }

    private fun writeBoolRow(
        source: List<List<Boolean>>,
        rows: List<List<String>>,
        row: Int,
        value: (col: Int, current: Boolean) -> Boolean,
    ): List<List<Boolean>> {
        val height = rows.size
        val width = rows.first().size
        return List(height) { r ->
            List(width) { c ->
                val current = source.getOrNull(r)?.getOrNull(c) == true
                if (r == row) value(c, current) else current
            }
        }
    }

    private fun writeBoolColumn(
        source: List<List<Boolean>>,
        rows: List<List<String>>,
        col: Int,
        value: () -> Boolean,
    ): List<List<Boolean>> {
        val height = rows.size
        val width = rows.first().size
        return List(height) { r ->
            List(width) { c ->
                val current = source.getOrNull(r)?.getOrNull(c) == true
                if (c == col) value() else current
            }
        }
    }

    private fun <T> insertDefaultRow(
        grid: List<MutableList<T>>,
        index: Int,
        width: Int,
        value: T,
    ): List<MutableList<T>> {
        val next = grid.map { it.toMutableList() }.toMutableList()
        next.add(index, MutableList(width) { value })
        return next
    }

    private fun <T> insertDefaultColumn(grid: List<MutableList<T>>, index: Int, value: T): List<MutableList<T>> {
        grid.forEach { it.add(index, value) }
        return grid
    }

    private fun packDefaultInt(grid: List<List<Int>>, default: Int): List<List<Int>> {
        return if (grid.all { row -> row.all { it == default } }) emptyList() else grid.map { it.toList() }
    }

    private fun packDefaultBool(grid: List<List<Boolean>>): List<List<Boolean>> {
        return if (grid.all { row -> row.all { !it } }) emptyList() else grid.map { it.toList() }
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
            val start = mapStart(mark.start, prefix, oldEnd, newEnd)
            val end = mapEnd(mark.end, prefix, oldEnd, newEnd)
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

    /**
     * Removes every style that overlaps [start, end). A mark that crosses either
     * boundary is split there, the same way [toggle] cuts one style.
     */
    fun clear(marks: List<TextMark>, start: Int, end: Int): List<TextMark> {
        if (end <= start) return marks
        val next = marks.flatMap { mark ->
            if (mark.end <= start || mark.start >= end) {
                listOf(mark)
            } else {
                buildList {
                    if (mark.start < start) add(mark.copy(end = start))
                    if (mark.end > end) add(mark.copy(start = end))
                }
            }
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

    /**
     * Exclusive start. A pure insertion sitting exactly on the boundary moves
     * the start forward, so the new characters stay outside the span.
     */
    private fun mapStart(index: Int, prefix: Int, oldEnd: Int, newEnd: Int): Int {
        if (index == prefix && oldEnd == prefix) return newEnd
        return mapEnd(index, prefix, oldEnd, newEnd)
    }

    /** Exclusive end. An insertion at [prefix] is already outside the span. */
    private fun mapEnd(index: Int, prefix: Int, oldEnd: Int, newEnd: Int): Int {
        return when {
            index <= prefix -> index
            index >= oldEnd -> index + (newEnd - oldEnd)
            else -> newEnd
        }
    }
}
