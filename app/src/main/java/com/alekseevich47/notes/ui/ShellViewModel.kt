package com.alekseevich47.notes.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.service.PlaybackService
import com.alekseevich47.notes.service.RecordingService
import com.alekseevich47.notes.stt.RecordingSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Screen { LIST, SETTINGS, EDITOR }

data class ShellUi(
    val screen: Screen = Screen.LIST,
    val editorId: Long? = null,
)

@HiltViewModel
class ShellViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NotesRepository,
    val session: RecordingSession,
) : ViewModel() {
    private val _ui = MutableStateFlow(ShellUi())
    val ui: StateFlow<ShellUi> = _ui.asStateFlow()
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        viewModelScope.launch {
            session.messages.collect { message -> _toast.value = message }
        }
    }

    fun showList() = _ui.update { it.copy(screen = Screen.LIST) }
    fun showSettings() = _ui.update { it.copy(screen = Screen.SETTINGS) }

    fun openEditor(id: Long) = _ui.update { it.copy(screen = Screen.EDITOR, editorId = id) }

    fun createManual() {
        viewModelScope.launch {
            val id = repository.create()
            openEditor(id)
        }
    }

    fun toast(message: String) {
        _toast.value = message
    }

    fun dismissToast() {
        _toast.value = null
    }

    fun toggleRecording() {
        if (session.phase.value == MicPhase.RECORDING) {
            val stop = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP)
            context.startService(stop)
        } else if (!session.isBusy) {
            val start = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START)
            ContextCompat.startForegroundService(context, start)
        }
    }

    fun play(noteId: Long, path: String, title: String) {
        val intent = Intent(context, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_PLAY)
            .putExtra(PlaybackService.EXTRA_NOTE_ID, noteId)
            .putExtra(PlaybackService.EXTRA_PATH, path)
            .putExtra(PlaybackService.EXTRA_TITLE, title)
        ContextCompat.startForegroundService(context, intent)
    }

    fun onNoteReady(id: Long?) {
        if (id != null && session.phase.value == MicPhase.DONE) openEditor(id)
    }
}
