package com.alekseevich47.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alekseevich47.notes.domain.NoteTables
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesPurple

@Composable
fun AppDialog(
    title: String,
    body: String? = null,
    confirm: String,
    dismiss: String? = "Отмена",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(22.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = NotesFont)
            if (!body.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (dismiss != null) {
                    Text(
                        dismiss,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        fontFamily = NotesFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    confirm,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    fontFamily = NotesFont,
                    fontWeight = FontWeight.SemiBold,
                    color = if (destructive) MaterialTheme.colorScheme.error else NotesPurple,
                )
            }
        }
    }
}

@Composable
fun PinDialog(
    title: String,
    subtitle: String,
    error: String? = null,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var digits by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = NotesFont)
            Spacer(Modifier.height(6.dp))
            Text(
                error ?: subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (index < digits.length) NotesPurple else MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(width = 78.dp, height = 52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (key.isEmpty()) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = key.isNotEmpty()) {
                                    digits = when (key) {
                                        "⌫" -> digits.dropLast(1)
                                        else -> if (digits.length < 4) digits + key else digits
                                    }
                                    if (digits.length == 4 && key != "⌫") {
                                        onSubmit(digits)
                                        digits = ""
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(key, fontFamily = NotesFont, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Закрыть",
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NotesFont,
            )
        }
    }
}

@Composable
fun TableSizeDialog(
    onCreate: (rows: Int, cols: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var rows by remember { mutableStateOf(2) }
    var cols by remember { mutableStateOf(2) }
    val maxRows = NoteTables.MAX_ROWS
    val maxCols = NoteTables.MAX_COLS
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Таблица",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = NotesFont,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Отметьте размер, как в сообщении",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NotesFont,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "$rows × $cols",
                fontFamily = NotesFont,
                fontWeight = FontWeight.SemiBold,
                color = NotesPurple,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(maxRows) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(maxCols) { col ->
                            val selected = row < rows && col < cols
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (selected) NotesPurple else MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .clickable {
                                        rows = row + 1
                                        cols = col + 1
                                    },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Отмена",
                    modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp),
                    fontFamily = NotesFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Вставить",
                    modifier = Modifier.clickable { onCreate(rows, cols) }.padding(12.dp),
                    fontFamily = NotesFont,
                    fontWeight = FontWeight.SemiBold,
                    color = NotesPurple,
                )
            }
        }
    }
}
