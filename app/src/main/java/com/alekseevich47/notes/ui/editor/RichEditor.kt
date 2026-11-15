package com.alekseevich47.notes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.MarkEdits
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteTable
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesPurple
import kotlin.math.max
import kotlin.math.min

private const val TABLE_MAX_ROWS = 8
private const val TABLE_MAX_COLS = 6

private val CensorInk = Color(0xFF6A6478)
private val CensorTiles = arrayOf(
    Color(0xFF5C5668),
    Color(0xFF7D768A),
    Color(0xFF3E3948),
    Color(0xFF948DA3),
    Color(0xFF6A6478),
    Color(0xFF4C4658),
)

@Composable
fun RichNoteEditor(
    document: NoteDocument,
    hiddenUnlocked: Boolean,
    onDocument: (NoteDocument) -> Unit,
    onNeedSelection: () -> Unit,
    onRequestUnlock: () -> Unit,
    onAskTable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember {
        mutableStateOf(TextFieldValue(document.text, TextRange(document.text.length)))
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val censorLayout = layout
    val censor = !hiddenUnlocked && document.marks.any { it.style == MarkStyle.HIDDEN }
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            Tool(Icons.Outlined.FormatBold, "Жирный") {
                applyStyle(document, field, MarkStyle.BOLD, onDocument, onNeedSelection)
            }
            Tool(Icons.Outlined.FormatItalic, "Курсив") {
                applyStyle(document, field, MarkStyle.ITALIC, onDocument, onNeedSelection)
            }
            Tool(Icons.Outlined.FormatStrikethrough, "Зачёркнутый") {
                applyStyle(document, field, MarkStyle.STRIKE, onDocument, onNeedSelection)
            }
            Tool(Icons.Outlined.FormatUnderlined, "Подчёркнутый") {
                applyStyle(document, field, MarkStyle.UNDERLINE, onDocument, onNeedSelection)
            }
            Tool(Icons.Outlined.VisibilityOff, "Скрытый") {
                applyStyle(document, field, MarkStyle.HIDDEN, onDocument, onNeedSelection)
            }
            Tool(Icons.Outlined.FormatListNumbered, "Список") {
                val previous = field.text
                val edit = ListAutoFormat.toggleLine(previous, field.selection.min)
                field = TextFieldValue(edit.text, TextRange(edit.cursor))
                onDocument(
                    document.copy(
                        text = edit.text,
                        marks = MarkEdits.shift(document.marks, previous, edit.text),
                    ),
                )
            }
            Tool(Icons.Outlined.TableChart, "Таблица", onAskTable)
        }
        if (censor) {
            Text(
                "Скрытый фрагмент под цензурой. Нажмите его и введите PIN.",
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onRequestUnlock)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                color = NotesPurple,
                fontFamily = NotesFont,
            )
        }
        BasicTextField(
            value = field,
            onValueChange = { next ->
                val previous = field.text
                val textHitsHidden = HiddenText.editTouches(document.marks, previous, next.text)
                val cursorInHidden = HiddenText.cursorInside(
                    document.marks,
                    next.selection.start,
                    next.selection.end,
                )
                if (!hiddenUnlocked && (textHitsHidden || cursorInHidden)) onRequestUnlock()
                if (!hiddenUnlocked && textHitsHidden) return@BasicTextField
                val edit = ListAutoFormat.afterEdit(previous, next.text, next.selection.end)
                val selection = if (edit.text == next.text) next.selection else TextRange(edit.cursor)
                field = next.copy(text = edit.text, selection = selection)
                onDocument(
                    document.copy(
                        text = edit.text,
                        marks = MarkEdits.shift(document.marks, previous, edit.text),
                    ),
                )
            },
            onTextLayout = { result ->
                val previous = layout
                if (
                    previous == null ||
                    previous.size != result.size ||
                    previous.layoutInput.text.text != result.layoutInput.text.text
                ) {
                    layout = result
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .drawWithContent {
                    drawContent()
                    if (censor && censorLayout != null) {
                        drawCensorMosaic(censorLayout, document.marks)
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = NotesFont,
            ),
            cursorBrush = SolidColor(NotesPurple),
            visualTransformation = remember(document.marks, hiddenUnlocked) {
                NoteVisual(document.marks, hiddenUnlocked)
            },
        )
    }
}

private fun applyStyle(
    document: NoteDocument,
    field: TextFieldValue,
    style: MarkStyle,
    onDocument: (NoteDocument) -> Unit,
    onNeedSelection: () -> Unit,
) {
    val start = field.selection.min
    val end = field.selection.max
    if (start == end) {
        onNeedSelection()
        return
    }
    onDocument(document.copy(marks = MarkEdits.toggle(document.marks, style, start, end)))
}

@Composable
private fun Tool(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Locked hidden text keeps its length (cursor mapping stays 1:1) but is painted
 * the same colour as the censor bar, so glyphs never show. A canvas mosaic is
 * drawn on top — no AGSL and no RenderEffect, which break on Tecno.
 */
private class NoteVisual(
    private val marks: List<TextMark>,
    private val unlocked: Boolean,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            marks.forEach { mark ->
                val start = mark.start.coerceIn(0, length)
                val end = mark.end.coerceIn(0, length)
                if (end <= start) return@forEach
                addStyle(styleFor(mark.style, unlocked), start, end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private fun styleFor(style: MarkStyle, unlocked: Boolean): SpanStyle = when (style) {
    MarkStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    MarkStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    MarkStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    MarkStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    MarkStyle.HIDDEN -> if (unlocked) {
        SpanStyle(background = NotesPurple.copy(alpha = 0.18f), textDecoration = TextDecoration.Underline)
    } else {
        SpanStyle(color = CensorInk, background = CensorInk)
    }
}

private fun DrawScope.drawCensorMosaic(layout: TextLayoutResult, marks: List<TextMark>) {
    val textLength = layout.layoutInput.text.length
    if (textLength == 0) return
    val tile = 4.dp.toPx()
    val pad = 1.dp.toPx()
    marks.filter { it.style == MarkStyle.HIDDEN }.forEach { mark ->
        val start = mark.start.coerceIn(0, textLength)
        val end = mark.end.coerceIn(0, textLength)
        if (end <= start) return@forEach
        val last = (end - 1).coerceAtMost(textLength - 1)
        val startLine = layout.getLineForOffset(start.coerceAtMost(last))
        val endLine = layout.getLineForOffset(last)
        for (line in startLine..endLine) {
            val from = max(start, layout.getLineStart(line))
            val to = min(end, layout.getLineEnd(line, visibleEnd = true))
            if (to <= from) continue
            val left = layout.getHorizontalPosition(from, true)
            val right = layout.getHorizontalPosition(to, true)
            val barLeft = min(left, right) - pad
            val barRight = max(left, right) + pad
            if (barRight - barLeft < 1f) continue
            val top = layout.getLineTop(line)
            val bottom = layout.getLineBottom(line)
            if (bottom - top < 1f) continue
            var y = top
            var row = 0
            while (y < bottom - 0.4f) {
                val height = min(tile, bottom - y)
                var x = barLeft
                var column = 0
                while (x < barRight - 0.4f) {
                    val width = min(tile, barRight - x)
                    drawRect(
                        color = censorTileColor(row, column, line),
                        topLeft = Offset(x, y),
                        size = Size(width, height),
                    )
                    x += tile
                    column++
                }
                y += tile
                row++
            }
        }
    }
}

private fun censorTileColor(row: Int, column: Int, line: Int): Color {
    val mixed = (row * 17 + column * 11 + line * 5 + (row xor column)) and 0x7fffffff
    return CensorTiles[mixed % CensorTiles.size]
}

@Composable
fun TableGrid(
    table: NoteTable,
    onChange: (NoteTable) -> Unit,
    onDelete: () -> Unit,
) {
    val width = table.cells.maxOfOrNull { it.size } ?: 0
    val rows = if (width == 0) {
        emptyList()
    } else {
        table.cells.map { row -> List(width) { index -> row.getOrElse(index) { "" } } }
    }
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, border, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (width == 0) "Таблица" else "Таблица  ${rows.size} × $width",
                fontFamily = NotesFont,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Удалить",
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                fontFamily = NotesFont,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (rows.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val minCol = 112.dp
                val colWidth = if (minCol * width <= maxWidth) maxWidth / width else minCol
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(colWidth * width)) {
                        rows.forEachIndexed { rowIndex, row ->
                            val header = rowIndex == 0
                            Row(Modifier.fillMaxWidth()) {
                                row.forEachIndexed { colIndex, cell ->
                                    BasicTextField(
                                        value = cell,
                                        onValueChange = { value ->
                                            val next = rows.map { it.toMutableList() }
                                            next[rowIndex][colIndex] = value.replace('\n', ' ')
                                            onChange(table.copy(cells = next.map { it.toList() }))
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = NotesFont,
                                            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        cursorBrush = SolidColor(NotesPurple),
                                        modifier = Modifier
                                            .width(colWidth)
                                            .heightIn(min = 44.dp)
                                            .background(if (header) headerBg else Color.Transparent)
                                            .border(0.5.dp, border),
                                        decorationBox = { inner ->
                                            Box(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                                contentAlignment = Alignment.CenterStart,
                                            ) {
                                                if (cell.isEmpty() && header) {
                                                    Text(
                                                        "Заголовок",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        fontFamily = NotesFont,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                }
                                                inner()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TableAction("Строка", enabled = rows.size < TABLE_MAX_ROWS) {
                val cols = width.coerceAtLeast(1)
                onChange(table.copy(cells = rows + listOf(List(cols) { "" })))
            }
            TableAction("Столбец", enabled = width < TABLE_MAX_COLS) {
                val grown = if (rows.isEmpty()) {
                    listOf(listOf(""))
                } else {
                    rows.map { it + "" }
                }
                onChange(table.copy(cells = grown))
            }
        }
    }
}

@Composable
private fun TableAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        "+ $label",
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        fontFamily = NotesFont,
        color = if (enabled) NotesPurple else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
    )
}
