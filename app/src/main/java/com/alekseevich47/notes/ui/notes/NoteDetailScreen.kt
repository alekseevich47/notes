package com.alekseevich47.notes.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.domain.NoteBody
import com.alekseevich47.notes.ui.editor.RichNoteEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val repository: NotesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private var noteId: Long = -1
    val settings = repository.settings

    fun note(id: Long) = repository.observeNote(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(id: Long, title: String, body: NoteBody) = viewModelScope.launch {
        val existing = repository.getNote(id) ?: return@launch
        repository.updateNote(existing.copy(title = title, body = body))
    }

    fun verifyPin(pin: String): Boolean =
        PinHasher.matchesPin(pin, repository.settings.value.pinHash)

    fun playerFor(path: String?): ExoPlayer? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return ExoPlayer.Builder(context).build().also {
            it.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            it.prepare()
        }
    }
}

@Composable
fun NoteDetailRoute(
    noteId: Long,
    onBack: () -> Unit,
    onNeedPinSetup: () -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel(),
) {
    val noteFlow = remember(noteId) { viewModel.note(noteId) }
    val note by noteFlow.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf(NoteBody()) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(note) {
        val n = note ?: return@LaunchedEffect
        title = n.title
        body = n.body
        player?.release()
        player = viewModel.playerFor(n.audioPath)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(bottom = 100.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
        }
        RichNoteEditor(
            title = title,
            onTitleChange = {
                title = it
                viewModel.save(noteId, title, body)
            },
            body = body,
            onBodyChange = {
                body = it
                viewModel.save(noteId, title, body)
            },
            pinHash = settings.pinHash,
            onNeedPinSetup = onNeedPinSetup,
            verifyPin = viewModel::verifyPin,
            modifier = Modifier.weight(1f),
        )
        if (player != null) {
            Button(
                onClick = {
                    val p = player ?: return@Button
                    if (p.isPlaying) p.pause() else p.play()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Прослушать запись") }
        }
        Text(
            "Сохранить можно просто уйдя назад — изменения пишутся сразу",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
