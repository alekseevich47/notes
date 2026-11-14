package com.alekseevich47.notes.stt

import android.content.Context
import com.alekseevich47.notes.data.audio.AudioCapture
import com.alekseevich47.notes.data.audio.BeepPlayer
import com.alekseevich47.notes.data.download.ModelDownloader
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.domain.NoteBody
import com.alekseevich47.notes.domain.RecordingUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioCapture: AudioCapture,
    private val beepPlayer: BeepPlayer,
    private val stt: TwoPassSttEngine,
    private val repository: NotesRepository,
    private val models: ModelDownloader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    private var recordJob: Job? = null
    private var chunkJob: Job? = null
    private var wavFile: File? = null
    private var noteNumber: Int = 0

    fun canRecord(): Boolean = stt.ready || models.isInstalled(com.alekseevich47.notes.domain.ModelKind.T_ONE)

    fun toggle() {
        if (_state.value.active) stop() else start()
    }

    fun start() {
        if (_state.value.active || _state.value.processing) return
        scope.launch {
            beepPlayer.playStartBeep()
            noteNumber = repository.nextNoteNumber()
            val dir = File(context.filesDir, "audio").also { it.mkdirs() }
            val file = File(dir, "note_${noteNumber}_${System.currentTimeMillis()}.wav")
            wavFile = file
            _state.value = RecordingUiState(active = true, partialText = "")
            stt.startSession()
            chunkJob = scope.launch {
                audioCapture.chunks.collect { chunk ->
                    stt.acceptPcm(chunk.samples, AudioCapture.SAMPLE_RATE)
                }
            }
            scope.launch {
                stt.partials().collect { text ->
                    _state.update { it.copy(partialText = text, amplitude = audioCapture.amplitude.value) }
                }
            }
            scope.launch {
                audioCapture.amplitude.collect { amp ->
                    if (_state.value.active) _state.update { it.copy(amplitude = amp) }
                }
            }
            recordJob = scope.launch {
                runCatching { audioCapture.recordToWav(file) }
            }
        }
    }

    fun stop() {
        if (!_state.value.active) return
        scope.launch {
            beepPlayer.playStopBeep()
            _state.update { it.copy(active = false, processing = true) }
            audioCapture.stop()
            recordJob?.cancelAndJoin()
            chunkJob?.cancelAndJoin()
            val live = stt.finishUtterance()
            val file = wavFile
            val polished = if (file != null && repository.settings.value.gigaFinalizeEnabled) {
                stt.finalizeWithOffline(file)
            } else live
            val text = polished.ifBlank { live }
            val id = repository.createNote(
                title = "Заметка #$noteNumber",
                body = NoteBody(text = text),
                audioPath = file?.absolutePath,
                noteNumber = noteNumber,
            )
            _state.update {
                it.copy(
                    processing = false,
                    done = true,
                    finalText = text,
                    partialText = text,
                    noteId = id,
                )
            }
            // keep DONE briefly for animations; caller may clear
        }
    }

    fun clearDone() {
        _state.update { it.copy(done = false) }
    }

    fun resetSheet() {
        _state.value = RecordingUiState()
    }

    suspend fun updateDraft(noteId: Long, title: String, body: NoteBody) = withContext(Dispatchers.IO) {
        val existing = repository.getNote(noteId) ?: return@withContext
        repository.updateNote(existing.copy(title = title, body = body))
    }
}
