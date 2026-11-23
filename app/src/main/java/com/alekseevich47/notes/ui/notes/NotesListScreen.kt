package com.alekseevich47.notes.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.NoteListItem
import com.alekseevich47.notes.ui.components.AppDialog
import com.alekseevich47.notes.ui.components.DayRange
import com.alekseevich47.notes.ui.components.LiquidGlassPlate
import com.alekseevich47.notes.ui.components.formatDay
import com.alekseevich47.notes.ui.editor.SpoilerPreview
import com.alekseevich47.notes.ui.editor.noteCardContentDescription
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesMuted
import com.alekseevich47.notes.ui.theme.NotesPurple
import dev.chrisbanes.haze.HazeState

@Composable
fun NotesListScreen(
    notes: List<NoteListItem>,
    query: String,
    range: DayRange?,
    hazeState: HazeState,
    onQuery: (String) -> Unit,
    onCalendar: () -> Unit,
    onClearRange: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onDeleteSelected: (Set<Long>) -> Unit = {},
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = selecting) {
        selecting = false
        selected = emptySet()
    }
    val fabBottom by animateDpAsState(134.dp + bottomInset, tween(240), label = "list-fab")
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (selecting) {
                    IconButton(onClick = {
                        selecting = false
                        selected = emptySet()
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Отменить выбор")
                    }
                }
                Text(
                    "Заметки",
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = NotesFont,
                    modifier = Modifier.weight(1f),
                )
                if (selecting) {
                    IconButton(onClick = { if (selected.isNotEmpty()) confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(
                    query = query,
                    onQuery = onQuery,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onCalendar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = "Календарь", tint = if (range == null) MaterialTheme.colorScheme.onSurface else NotesPurple)
                }
            }
            if (range != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onClearRange)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(rangeLabel(range), fontFamily = NotesFont, color = NotesPurple, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Outlined.Close, contentDescription = "Сбросить", tint = NotesPurple, modifier = Modifier.padding(start = 4.dp).size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (notes.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Пока пусто", style = MaterialTheme.typography.titleMedium, fontFamily = NotesFont)
                    Text(
                        "Нажмите микрофон или плюс",
                        color = NotesMuted,
                        fontFamily = NotesFont,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp + bottomInset),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(notes, key = { it.id }, contentType = { "note" }) { note ->
                        NoteCard(
                            note = note,
                            selecting = selecting,
                            checked = note.id in selected,
                            onClick = {
                                if (selecting) {
                                    selected = if (note.id in selected) selected - note.id else selected + note.id
                                } else {
                                    onOpen(note.id)
                                }
                            },
                            onLongClick = {
                                selecting = true
                                selected = selected + note.id
                            },
                        )
                    }
                }
            }
        }
        LiquidGlassPlate(
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = fabBottom)
                .size(60.dp),
            shape = CircleShape,
            blur = false,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(onClick = onCreate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Новая заметка", tint = NotesPurple)
            }
        }
        if (confirmDelete) {
            AppDialog(
                title = if (selected.size == 1) "Удалить заметку?" else "Удалить заметки?",
                body = "Аудио тоже будет удалено с устройства.",
                confirm = "Удалить",
                destructive = true,
                onConfirm = {
                    val ids = selected
                    confirmDelete = false
                    selecting = false
                    selected = emptySet()
                    onDeleteSelected(ids)
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = NotesMuted)
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = NotesFont,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Поиск", color = NotesMuted, fontFamily = NotesFont)
                    }
                    inner()
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: NoteListItem,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (selecting) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = NotesPurple),
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .clearAndSetSemantics {
                    contentDescription = noteCardContentDescription(note.title, note.preview)
                },
        ) {
        Text(note.title, style = MaterialTheme.typography.titleMedium, fontFamily = NotesFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
        SpoilerPreview(
            parts = note.preview,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NotesFont),
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            formatStamp(note.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = NotesMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
        }
    }
}

private fun rangeLabel(range: DayRange): String {
    return if (range.start == range.end) formatDay(range.start) else formatDay(range.start) + " — " + formatDay(range.end)
}
