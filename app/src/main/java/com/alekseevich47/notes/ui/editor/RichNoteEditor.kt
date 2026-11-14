package com.alekseevich47.notes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.NoteBody
import com.alekseevich47.notes.domain.RichSpan
import com.alekseevich47.notes.domain.TableBlock
import com.alekseevich47.notes.ui.components.NotesDialog
import com.alekseevich47.notes.ui.components.PinDialog
import java.util.UUID

@Composable
fun RichNoteEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    body: NoteBody,
    onBodyChange: (NoteBody) -> Unit,
    pinHash: String?,
    onNeedPinSetup: () -> Unit,
    verifyPin: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var value by remember(body.text) {
        mutableStateOf(TextFieldValue(body.text, TextRange(body.text.length)))
    }
    var showTableDialog by remember { mutableStateOf(false) }
    var rows by remember { mutableIntStateOf(2) }
    var cols by remember { mutableIntStateOf(2) }
    var unlockHidden by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            placeholder = { Text("Название", style = MaterialTheme.typography.headlineMedium) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(12.dp))
        FormatToolbar(
            onBold = { onBodyChange(body.toggleSpan(value.selection) { it.copy(bold = !it.bold) }) },
            onItalic = { onBodyChange(body.toggleSpan(value.selection) { it.copy(italic = !it.italic) }) },
            onStrike = { onBodyChange(body.toggleSpan(value.selection) { it.copy(strike = !it.strike) }) },
            onUnderline = { onBodyChange(body.toggleSpan(value.selection) { it.copy(underline = !it.underline) }) },
            onHidden = {
                if (pinHash.isNullOrBlank()) onNeedPinSetup()
                else onBodyChange(body.toggleSpan(value.selection) { it.copy(hidden = true) })
            },
            onList = {
                val t = value.text
                val start = value.selection.min
                val lineStart = t.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
                if (!t.substring(lineStart).startsWith("1.")) {
                    val next = t.substring(0, lineStart) + "1. " + t.substring(lineStart)
                    val vf = TextFieldValue(next, TextRange(lineStart + 3))
                    value = vf
                    onBodyChange(body.copy(text = next))
                }
            },
            onTable = { showTableDialog = true },
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    val transformed = ListAutoFormat.onTextChanged(value.text, next.text, next.selection.start)
                    val vf = if (transformed != null) {
                        TextFieldValue(transformed.first, TextRange(transformed.second))
                    } else next
                    value = vf
                    onBodyChange(body.copy(text = vf.text))
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.text.isEmpty()) {
                        Text("Содержимое заметки…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                },
            )
        }

        if (body.spans.any { it.hidden }) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (unlockHidden) "Скрытый текст открыт" else "Есть скрытый текст — нажмите, чтобы открыть",
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        if (unlockHidden) return@clickable
                        askPin = true
                    }
                    .blur(if (unlockHidden) 0.dp else 6.dp)
                    .padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        body.tables.forEach { table ->
            Spacer(Modifier.height(12.dp))
            EditableTable(
                table = table,
                onChange = { updated ->
                    onBodyChange(body.copy(tables = body.tables.map { if (it.id == updated.id) updated else it }))
                },
            )
        }
    }

    if (showTableDialog) {
        NotesDialog(
            title = "Создать таблицу",
            onDismiss = { showTableDialog = false },
            confirmLabel = "Создать",
            onConfirm = {
                val cells = List(rows) { List(cols) { "" } }
                val table = TableBlock(UUID.randomUUID().toString(), rows, cols, cells)
                onBodyChange(body.copy(tables = body.tables + table))
                showTableDialog = false
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = rows.toString(),
                    onValueChange = { rows = it.toIntOrNull()?.coerceIn(1, 12) ?: rows },
                    label = { Text("Строки") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = cols.toString(),
                    onValueChange = { cols = it.toIntOrNull()?.coerceIn(1, 8) ?: cols },
                    label = { Text("Столбцы") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }

    if (askPin) {
        PinDialog(
            title = "Введите PIN",
            onDismiss = { askPin = false },
            onSubmit = {
                if (verifyPin(it)) {
                    unlockHidden = true
                    askPin = false
                }
            },
        )
    }
}

@Composable
private fun FormatToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    onUnderline: () -> Unit,
    onHidden: () -> Unit,
    onList: () -> Unit,
    onTable: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onBold) { Icon(Icons.Outlined.FormatBold, null) }
        IconButton(onClick = onItalic) { Icon(Icons.Outlined.FormatItalic, null) }
        IconButton(onClick = onStrike) { Icon(Icons.Outlined.FormatStrikethrough, null) }
        IconButton(onClick = onUnderline) { Icon(Icons.Outlined.FormatUnderlined, null) }
        IconButton(onClick = onHidden) { Icon(Icons.Outlined.VisibilityOff, null) }
        IconButton(onClick = onList) { Icon(Icons.Outlined.FormatListNumbered, null) }
        IconButton(onClick = onTable) { Icon(Icons.Outlined.GridOn, null) }
    }
}

@Composable
private fun EditableTable(table: TableBlock, onChange: (TableBlock) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
    ) {
        table.cells.forEachIndexed { r, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { c, cell ->
                    OutlinedTextField(
                        value = cell,
                        onValueChange = { v ->
                            val next = table.cells.mapIndexed { rr, rrRow ->
                                rrRow.mapIndexed { cc, old -> if (rr == r && cc == c) v else old }
                            }
                            onChange(table.copy(cells = next))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                    )
                }
            }
        }
    }
}

private fun NoteBody.toggleSpan(selection: TextRange, transform: (RichSpan) -> RichSpan): NoteBody {
    if (selection.collapsed) return this
    val start = selection.min
    val end = selection.max
    val base = RichSpan(start, end)
    return copy(spans = spans.filterNot { it.start == start && it.end == end } + transform(base))
}
