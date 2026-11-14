package com.alekseevich47.notes.ui.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.domain.NoteBody
import com.alekseevich47.notes.service.RecordingService
import com.alekseevich47.notes.stt.RecordingController
import com.alekseevich47.notes.ui.editor.RichNoteEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(
    val controller: RecordingController,
    private val repository: NotesRepository,
) : ViewModel() {
    val state = controller.state
    val settings = repository.settings

    fun save(noteId: Long, title: String, body: NoteBody) {
        viewModelScope.launch { controller.updateDraft(noteId, title, body) }
    }

    suspend fun loadTitle(noteId: Long): String =
        repository.getNote(noteId)?.title ?: "Заметка"

    fun verifyPin(pin: String): Boolean =
        PinHasher.matchesPin(pin, repository.settings.value.pinHash)
}

@Composable
fun RecordSheet(
    visible: Boolean,
    onClose: () -> Unit,
    onNeedModels: () -> Unit,
    onNeedPinSetup: () -> Unit,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val editing = state.noteId != null && !state.active && !state.processing

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 3 },
        exit = fadeOut() + slideOutVertically { it / 3 },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text(
                when {
                    state.active -> "Идёт запись…"
                    state.processing -> "Обработка…"
                    editing -> "Редактирование"
                    else -> "Диктовка"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))

            if (editing) {
                var title by remember(state.noteId) { mutableStateOf("Заметка") }
                var body by remember(state.noteId) { mutableStateOf(NoteBody(text = state.finalText)) }
                LaunchedEffect(state.noteId, state.finalText) {
                    val id = state.noteId ?: return@LaunchedEffect
                    title = viewModel.loadTitle(id)
                    body = NoteBody(text = state.finalText.ifBlank { body.text })
                }
                RichNoteEditor(
                    title = title,
                    onTitleChange = {
                        title = it
                        state.noteId?.let { id -> viewModel.save(id, title, body) }
                    },
                    body = body,
                    onBodyChange = {
                        body = it
                        state.noteId?.let { id -> viewModel.save(id, title, body) }
                    },
                    pinHash = settings.pinHash,
                    onNeedPinSetup = onNeedPinSetup,
                    verifyPin = viewModel::verifyPin,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        state.noteId?.let { viewModel.save(it, title, body) }
                        viewModel.controller.clearDone()
                        viewModel.controller.resetSheet()
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Готово") }
            } else {
                Text(
                    text = state.partialText.ifBlank { "Говорите…" },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (!viewModel.controller.canRecord() && !state.active) {
                            onNeedModels()
                        } else if (state.active) {
                            context.startService(
                                Intent(context, RecordingService::class.java)
                                    .setAction(RecordingService.ACTION_STOP),
                            )
                        } else if (!state.processing) {
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, RecordingService::class.java)
                                    .setAction(RecordingService.ACTION_START),
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (state.active) "Стоп" else if (state.processing) "…" else "Старт")
                }
            }
        }
    }
}
