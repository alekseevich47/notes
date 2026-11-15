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
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
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
            val toneInstalled = models.isInstalled(ModelKind.T_ONE)
            val online = if (toneInstalled) {
                withContext(Dispatchers.Default) { SherpaEngines.onlineTone(toneDir) }
            } else {
                null
            }
            if (toneInstalled && online == null) {
                _messages.tryEmit("Модель T-one не открылась. Запись идёт без неё.")
            }
            // A loaded T-one model owns the mic together with the WAV writer.
            // SpeechRecognizer is a second client and is not started in that case.
            val platform = if (MicRouting.route(online != null) == LiveTranscriptRoute.PLATFORM_AFTER_RECORD) {
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
            val captured = try {
                withContext(Dispatchers.IO) { capture(file, online, platform) }
            } finally {
                withContext(Dispatchers.Main) { platform?.stop() }
                runCatching { online?.release() }
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
    private fun capture(file: File, online: OnlineRecognizer?, platform: PlatformTranscriber?): String {
        var mic = openMic()
        val writer = WavWriter(file)
        var buffer = ByteArray(mic.bufferSize)
        var tone = online
        var stream = tone?.let { recognizer ->
            runCatching { recognizer.createStream() }
                .onFailure { Log.e(TAG, "T-one stream failed", it) }
                .getOrNull()
        }
        if (stream == null) tone = null
        var committed = ""
        var smoothed = 0f
        var wrote = 0
        var strikes = 0
        var reopens = 0
        var platformStarted = false
        var platformRetired = false
        try {
            while (!stopRequested.get()) {
                val read = mic.record.read(buffer, 0, buffer.size)
                val recording = mic.record.recordingState == AudioRecord.RECORDSTATE_RECORDING
                if (read < 0 || !recording) {
                    strikes++
                    if (strikes < 3) continue
                    strikes = 0
                    if (platformStarted && !platformRetired) {
                        platformRetired = true
                        platformStarted = false
                        platform?.stop()
                        _messages.tryEmit(
                            "Микрофон отдан системному распознаванию. Аудио пишется дальше — для живого текста установите T-one.",
                        )
                    }
                    if (reopens >= 2) break
                    reopens++
                    val reopened = runCatching { reopen(mic) }
                    if (reopened.isFailure) {
                        Log.e(TAG, "mic reopen failed", reopened.exceptionOrNull())
                        break
                    }
                    mic = reopened.getOrThrow()
                    buffer = ByteArray(mic.bufferSize)
                    continue
                }
                strikes = 0
                writer.write(buffer, read)
                wrote += read
                val floats = Pcm.toFloats(buffer, read)
                smoothed = smoothed * 0.65f + Pcm.level(floats) * 0.35f
                _level.value = smoothed
                if (tone != null && stream != null) {
                    val fed = feedTone(tone, stream, floats, finish = false, committed)
                    committed = fed.committed
                    if (fed.failed) {
                        runCatching { stream.release() }
                        stream = null
                        tone = null
                    }
                }
                if (
                    platform != null &&
                    !platformStarted &&
                    !platformRetired &&
                    wrote >= WARMUP_BYTES
                ) {
                    platform.start()
                    platformStarted = true
                }
                pushWidgets()
            }
            if (tone != null && stream != null) {
                val fed = feedTone(tone, stream, FloatArray(0), finish = true, committed)
                committed = fed.committed
            }
        } finally {
            if (platformStarted) platform?.stop()
            runCatching { mic.record.stop() }
            runCatching { mic.record.release() }
            writer.close()
            stream?.let { runCatching { it.release() } }
        }
        return if (online != null) committed.ifBlank { _text.value } else _text.value
    }

    private data class ToneFeed(val committed: String, val failed: Boolean)

    private fun feedTone(
        recognizer: OnlineRecognizer,
        stream: OnlineStream,
        floats: FloatArray,
        finish: Boolean,
        committed: String,
    ): ToneFeed {
        var next = committed
        val failed = runCatching {
            if (finish) {
                stream.inputFinished()
            } else {
                stream.acceptWaveform(floats, SAMPLE_RATE)
            }
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            val partial = recognizer.getResult(stream).text.trim()
            if (!finish && recognizer.isEndpoint(stream)) {
                if (partial.isNotBlank()) {
                    next = listOf(next, partial).filter { it.isNotBlank() }.joinToString(" ")
                }
                recognizer.reset(stream)
                _text.value = next
            } else if (partial.isNotBlank() || next.isNotBlank()) {
                next = listOf(next, partial).filter { it.isNotBlank() }.joinToString(" ")
                if (!finish) _text.value = next
            }
        }.onFailure { Log.e(TAG, "T-one decode stopped", it) }.isFailure
        return ToneFeed(next, failed)
    }

    @SuppressLint("MissingPermission")
    private fun openMic(): OpenMic {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        var failure = "Не удалось открыть микрофон"
        for (source in sources) {
            val min = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (min <= 0) {
                failure = "Микрофон недоступен"
                continue
            }
            val bufferSize = max(min, SAMPLE_RATE / 5 * 2)
            val recorder = AudioRecord(
                source,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                continue
            }
            val started = runCatching { recorder.startRecording() }.isSuccess &&
                recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (!started) {
                recorder.release()
                failure = "Микрофон занят"
                continue
            }
            return OpenMic(recorder, bufferSize)
        }
        error(failure)
    }

    private fun reopen(current: OpenMic): OpenMic {
        runCatching { current.record.stop() }
        current.record.release()
        return openMic()
    }

    private data class OpenMic(val record: AudioRecord, val bufferSize: Int)

    private suspend fun finalize(file: File, live: String): String {
        val fallback = live.trim()
        if (!models.isInstalled(ModelKind.GIGA_AM) || !file.exists() || file.length() < 44) {
            return fallback
        }
        val recognizer = try {
            withContext(Dispatchers.Default) {
                SherpaEngines.offlineGiga(models.directory(ModelKind.GIGA_AM))
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e(TAG, "GigaAM init crashed", error)
            null
        } ?: return fallback
        return try {
            val text = withContext(Dispatchers.Default) {
                SherpaEngines.transcribeFile(recognizer, file.absolutePath)
            }
            text.ifBlank { fallback }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e(TAG, "GigaAM decode crashed", error)
            fallback
        } finally {
            runCatching { recognizer.release() }
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
        const val WARMUP_BYTES = SAMPLE_RATE / 10 * 2
    }
}
