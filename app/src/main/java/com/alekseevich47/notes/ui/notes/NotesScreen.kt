package com.alekseevich47.notes.ui.notes

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.domain.Note
import com.alekseevich47.notes.ui.components.LiquidGlassPlate
import com.alekseevich47.notes.ui.components.NotesDateRangePickerModal
import com.alekseevich47.notes.ui.theme.NotesMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class NotesFilter(val query: String = "", val fromMs: Long? = null, val toMs: Long? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(NotesFilter())
    val notes = filter.flatMapLatest { f ->
        repository.observeNotes(f.query, f.fromMs, f.toMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun setRange(from: Long?, to: Long?) { filter.value = filter.value.copy(fromMs = from, toMs = to) }
    fun currentQuery(): String = filter.value.query
}

@Composable
fun NotesRoute(
    hazeState: HazeState,
    onOpenNote: (Long) -> Unit,
    onCreateNote: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Заметки",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.setQuery(it)
                    },
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Поиск") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                IconButton(onClick = { showDate = true }) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = "Календарь")
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(note = note, onClick = { onOpenNote(note.id) })
                }
            }
        }

        LiquidGlassPlate(
            hazeState = hazeState,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 96.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            IconButton(onClick = onCreateNote, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "Добавить")
            }
        }
    }

    if (showDate) {
        NotesDateRangePickerModal(
            onDismiss = { showDate = false },
            onConfirm = { from, to -> viewModel.setRange(from, to) },
        )
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(note.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(fmt.format(Date(note.createdAt)), color = NotesMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            note.body.text.ifBlank { "—" }.take(160),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
        )
    }
}
