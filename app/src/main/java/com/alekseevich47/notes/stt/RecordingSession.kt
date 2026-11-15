package com.alekseevich47.notes.stt

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.alekseevich47.notes.data.audio.Pcm
import com.alekseevich47.notes.data.audio.SoftBeep
import com.alekseevich47.notes.data.audio.WavWriter
import com.alekseevich47.notes.data.download.ModelDownloadManager
import com.alekseevich47.notes.data.download.ModelKind
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.ui.widgets.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class RecordingSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NotesRepository,
    private val models: ModelDownloadManager,
) {
    private val _phase = MutableStateFlow(MicPhase.IDLE)
    private val _level = MutableStateFlow(0f)
    private val _text = MutableStateFlow("")
    private val _noteId = MutableStateFlow<Long?>(null)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val phase: StateFlow<MicPhase> = _phase.asStateFlow()
    val level: StateFlow<Float> = _level.asStateFlow()
    val liveText: StateFlow<String> = _text.asStateFlow()
    val lastNoteId: StateFlow<Long?> = _noteId.asStateFlow()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val stopRequested = AtomicBoolean(false)
    private var active: CompletableDeferred<Unit>? = null

    val isBusy: Boolean
        get() = _phase.value == MicPhase.RECORDING || _phase.value == MicPhase.PROCESSING

    fun requestStop() {
        stopRequested.set(true)
    }

    suspend fun recordAndSave(): Long? {
        if (isBusy) {
            active?.await()
            return _noteId.value
        }
        val gate = CompletableDeferred<Unit>()
        active = gate
        stopRequested.set(false)
        _noteId.value = null
        _text.value = ""
        _level.value = 0f
        var noteId: Long? = null
        try {
            _phase.value = MicPhase.RECORDING
            pushWidgets()
            withContext(Dispatchers.Default) { SoftBeep.play() }
            delay(140)
            val file = File(context.filesDir, "recordings/note-${System.currentTimeMillis()}.wav")
            val toneDir = models.directory(ModelKind.T_ONE)
            val online = if (models.isInstalled(ModelKind.T_ONE)) {
                withContext(Dispatchers.Default) { SherpaEngines.onlineTone(toneDir) }
            } else {
                null
            }
            val platform = if (online == null) {
                PlatformTranscriber(
                    context = context,
                    onText = { value ->
                        _text.value = value
                        pushWidgets()
                    },
                    onHardError = { message -> _messages.tryEmit(message) },
                )
            } else {
                null
            }
            platform?.start()
            val captured = try {
                withContext(Dispatchers.IO) { capture(file, online) }
            } finally {
                withContext(Dispatchers.Main) { platform?.stop() }
                online?.release()
            }
            _phase.value = MicPhase.PROCESSING
            _level.value = 0f
            pushWidgets()
            val finalized = finalize(file, captured)
            _text.value = finalized
            val document = NoteDocument(text = finalized.trim())
            noteId = repository.create(document = document, audioPath = file.absolutePath)
            _noteId.value = noteId
            _phase.value = MicPhase.DONE
            pushWidgets()
            delay(800)
            _phase.value = MicPhase.IDLE
            _level.value = 0f
            pushWidgets()
        } catch (error: Throwable) {
            Log.e(TAG, "recording failed", error)
            _messages.tryEmit(error.message ?: "Не удалось записать заметку")
            _phase.value = MicPhase.IDLE
            _level.value = 0f
            pushWidgets()
        } finally {
            gate.complete(Unit)
            active = null
            stopRequested.set(false)
        }
        return noteId
    }

    @SuppressLint("MissingPermission")
    private fun capture(file: File, online: com.k2fsa.sherpa.onnx.OnlineRecognizer?): String {
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) error("Микрофон недоступен")
        val bufferSize = max(min, SAMPLE_RATE / 5 * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("Не удалось открыть микрофон")
        }
        val writer = WavWriter(file)
        val stream = online?.createStream()
        val buffer = ByteArray(bufferSize)
        var committed = ""
        var smoothed = 0f
        try {
            recorder.startRecording()
            while (!stopRequested.get()) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                writer.write(buffer, read)
                val floats = Pcm.toFloats(buffer, read)
                smoothed = smoothed * 0.65f + Pcm.level(floats) * 0.35f
                _level.value = smoothed
                if (online != null && stream != null) {
                    stream.acceptWaveform(floats, SAMPLE_RATE)
                    while (online.isReady(stream)) online.decode(stream)
                    val partial = online.getResult(stream).text.trim()
                    if (online.isEndpoint(stream)) {
                        if (partial.isNotBlank()) {
                            committed = listOf(committed, partial).filter { it.isNotBlank() }.joinToString(" ")
                        }
                        online.reset(stream)
                        _text.value = committed
                    } else {
                        _text.value = listOf(committed, partial).filter { it.isNotBlank() }.joinToString(" ")
                    }
                }
                pushWidgets()
            }
            if (online != null && stream != null) {
                stream.inputFinished()
                while (online.isReady(stream)) online.decode(stream)
                val tail = online.getResult(stream).text.trim()
                if (tail.isNotBlank()) {
                    committed = listOf(committed, tail).filter { it.isNotBlank() }.joinToString(" ")
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            writer.close()
            stream?.release()
        }
        return if (online != null) committed.ifBlank { _text.value } else _text.value
    }

    private suspend fun finalize(file: File, live: String): String {
        if (!models.isInstalled(ModelKind.GIGA_AM) || !file.exists() || file.length() < 44) {
            return live.trim()
        }
        val recognizer = withContext(Dispatchers.Default) {
            SherpaEngines.offlineGiga(models.directory(ModelKind.GIGA_AM))
        } ?: return live.trim()
        return try {
            val text = withContext(Dispatchers.Default) {
                SherpaEngines.transcribeFile(recognizer, file.absolutePath)
            }
            text.ifBlank { live.trim() }
        } finally {
            recognizer.release()
        }
    }

    private var lastWidgetAt = 0L

    private fun pushWidgets() {
        val now = android.os.SystemClock.elapsedRealtime()
        val phase = _phase.value
        if (phase == MicPhase.RECORDING && now - lastWidgetAt < 120) return
        lastWidgetAt = now
        WidgetUpdater.push(context, phase, _level.value, _text.value)
    }

    private companion object {
        const val TAG = "RecordingSession"
        const val SAMPLE_RATE = 16_000
    }
}
