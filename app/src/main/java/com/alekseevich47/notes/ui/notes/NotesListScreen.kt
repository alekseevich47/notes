package com.alekseevich47.notes.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.domain.Note
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.ui.components.DayRange
import com.alekseevich47.notes.ui.components.LiquidGlassPlate
import com.alekseevich47.notes.ui.components.formatDay
import com.alekseevich47.notes.ui.theme.NotesFont
import com.alekseevich47.notes.ui.theme.NotesMuted
import com.alekseevich47.notes.ui.theme.NotesPurple
import dev.chrisbanes.haze.HazeState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NotesListScreen(
    notes: List<Note>,
    query: String,
    range: DayRange?,
    hazeState: HazeState,
    onQuery: (String) -> Unit,
    onCalendar: () -> Unit,
    onClearRange: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Text("Заметки", style = MaterialTheme.typography.displaySmall, fontFamily = NotesFont)
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
                    contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(note, onClick = { onOpen(note.id) })
                    }
                }
            }
        }
        LiquidGlassPlate(
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 134.dp)
                .size(60.dp),
            shape = CircleShape,
            elevation = 10.dp,
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

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Text(note.title, style = MaterialTheme.typography.titleMedium, fontFamily = NotesFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            NoteDocuments.preview(note.document),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            formatStamp(note.updatedAt),
            style = MaterialTheme.typography.labelMedium,
            color = NotesMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun rangeLabel(range: DayRange): String {
    return if (range.start == range.end) formatDay(range.start) else formatDay(range.start) + " — " + formatDay(range.end)
}

private fun formatStamp(epoch: Long): String {
    val date = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
    return formatter.format(date)
}
