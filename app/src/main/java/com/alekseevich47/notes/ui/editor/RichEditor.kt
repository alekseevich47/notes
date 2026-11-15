package com.alekseevich47.notes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
        if (document.marks.any { it.style == MarkStyle.HIDDEN } && !hiddenUnlocked) {
            Text(
                "Скрытый текст закрыт. Нажмите и введите PIN.",
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
                val edit = ListAutoFormat.afterEdit(previous, next.text, next.selection.end)
                val selection = if (edit.text == next.text) next.selection else TextRange(edit.cursor)
                field = next.copy(text = edit.text, selection = selection)
                val hiddenHit = document.marks.any { mark ->
                    mark.style == MarkStyle.HIDDEN && mark.start < selection.end && mark.end > selection.start
                }
                if (hiddenHit && !hiddenUnlocked) onRequestUnlock()
                onDocument(
                    document.copy(
                        text = edit.text,
                        marks = MarkEdits.shift(document.marks, previous, edit.text),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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

private class NoteVisual(
    private val marks: List<TextMark>,
    private val unlocked: Boolean,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val chars = raw.toCharArray()
        if (!unlocked) {
            marks.filter { it.style == MarkStyle.HIDDEN }.forEach { mark ->
                val from = mark.start.coerceIn(0, chars.size)
                val to = mark.end.coerceIn(0, chars.size)
                for (index in from until to) {
                    if (chars[index] != '\n') chars[index] = '•'
                }
            }
        }
        val styled = buildAnnotatedString {
            append(String(chars))
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
        SpanStyle(color = Color(0xFF8E86A3), background = Color(0x338E86A3))
    }
}

@Composable
fun TableGrid(
    table: NoteTable,
    onChange: (NoteTable) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Таблица",
                modifier = Modifier.padding(8.dp),
                fontFamily = NotesFont,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Удалить",
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDelete)
                    .padding(8.dp),
                fontFamily = NotesFont,
                color = MaterialTheme.colorScheme.error,
            )
        }
        table.cells.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { colIndex, cell ->
                    BasicTextField(
                        value = cell,
                        onValueChange = { value ->
                            val next = table.cells.map { it.toMutableList() }.toMutableList()
                            next[rowIndex][colIndex] = value
                            onChange(table.copy(cells = next.map { it.toList() }))
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = NotesFont,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}
