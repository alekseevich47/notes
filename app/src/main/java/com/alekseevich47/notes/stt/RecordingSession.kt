package com.alekseevich47.notes.stt

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import com.alekseevich47.notes.data.audio.Pcm
import com.alekseevich47.notes.data.audio.SoftBeep
import com.alekseevich47.notes.data.audio.WavWriter
import com.alekseevich47.notes.data.download.ModelDownloadManager
import com.alekseevich47.notes.data.download.ModelKind
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.data.secure.RecognizerChoice
import com.alekseevich47.notes.data.secure.SecureSettingsStore
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.service.ModelWarmupService
import com.alekseevich47.notes.ui.widgets.WidgetUpdater
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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
    private val settings: SecureSettingsStore,
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
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var toneJob: Job? = null

    val isBusy: Boolean
        get() = _phase.value == MicPhase.RECORDING || _phase.value == MicPhase.PROCESSING

    fun requestStop() {
        stopRequested.set(true)
        AppLog.i(TAG, "stop requested phase=${_phase.value}")
    }

    suspend fun recordAndSave(): Long? {
        if (isBusy) {
            AppLog.i(TAG, "recordAndSave busy phase=${_phase.value}")
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
        var audioBase: String? = null
        var startedAt = 0L
        try {
            startedAt = SystemClock.elapsedRealtime()
            AppLog.i(TAG, "start requested")
            _phase.value = MicPhase.RECORDING
            AppLog.i(TAG, "phase RECORDING")
            pushWidgets()
            val recordingsDir = File(context.filesDir, "recordings")
            if (!recordingsDir.exists()) {
                check(recordingsDir.mkdirs() || recordingsDir.isDirectory) {
                    "Не удалось создать папку записей"
                }
            }
            val file = File(recordingsDir, "note-${System.currentTimeMillis()}.wav")
            audioBase = file.name
            val openPlatform = {
                PlatformTranscriber(
                    context = context,
                    onText = { value ->
                        _text.value = value
                        pushWidgets()
                    },
                    onHardError = { message -> _messages.tryEmit(message) },
                )
            }
            var toneSlot: ModelLoadSlot<OnlineRecognizer>? = null
            var toneLoad: Job? = null
            var platform: PlatformTranscriber? = null
            var borrowedTone: OnlineRecognizer? = null
            var freezeLiveText = false
            val captured = try {
                // The WAV starts as soon as the beep has been kicked off. A warm
                // T-one is borrowed; otherwise it loads beside the write. Cold
                // T-one and the system recognizer never construct T-one here.
                // If a task killer dropped the warmup process, start it again so
                // the ongoing notification shows. Do not wait for that load.
                if (
                    settings.warmupOnBoot.value &&
                    !WarmEngines.toneReady() &&
                    !WarmEngines.gigaReady()
                ) {
                    ModelWarmupService.start(context)
                }
                val toneInstalled = withContext(Dispatchers.IO) { models.isInstalled(ModelKind.T_ONE) }
                val toneEnabled = settings.isModelEnabled(ModelKind.T_ONE)
                when (
                    MicRouting.liveMode(
                        RecognizerChoice.effective(settings.recognizer.value, toneInstalled && toneEnabled),
                        settings.warmupOnBoot.value,
                    )
                ) {
                    LiveMode.TONE_COLD -> {
                        freezeLiveText = true
                        _text.value = MicRouting.COLD_TONE_TEXT
                        pushWidgets()
                        AppLog.i(TAG, "model ready elapsedMs=${elapsedSince(startedAt)} skipped=warmup")
                        AppLog.i(TAG, "route=hint")
                    }
                    LiveMode.SYSTEM -> {
                        AppLog.i(TAG, "model ready elapsedMs=${elapsedSince(startedAt)} skipped=recognizer")
                        AppLog.i(TAG, "route=platform")
                    }
                    LiveMode.TONE_WARM -> {
                        val warm = if (toneEnabled) WarmEngines.borrowTone() else null
                        if (warm != null) {
                            borrowedTone = warm
                            AppLog.i(TAG, "model ready elapsedMs=${elapsedSince(startedAt)} warm=1")
                        } else {
                            when (MicRouting.toneStartup(toneInstalled, toneEnabled)) {
                                ToneStartup.PARALLEL -> {
                                    val slot = ModelLoadSlot<OnlineRecognizer>()
                                    toneSlot = slot
                                    toneLoad = loadTone(slot, startedAt)
                                }
                                ToneStartup.SKIP -> {
                                    val elapsed = elapsedSince(startedAt)
                                    val why = if (toneInstalled) "disabled" else "absent"
                                    AppLog.i(TAG, "model ready elapsedMs=$elapsed skipped=$why")
                                    if (toneInstalled && !toneEnabled) AppLog.i(TAG, "route=disabled kind=T_ONE")
                                    AppLog.i(TAG, "route=platform")
                                }
                            }
                        }
                    }
                }
                SoftBeep.play()
                delay(BEEP_GAP_MS)
                if (toneSlot == null && borrowedTone == null && !freezeLiveText) platform = openPlatform()
                withContext(Dispatchers.IO) {
                    capture(file, startedAt, toneSlot, borrowedTone, platform, openPlatform, freezeLiveText)
                }
            } finally {
                borrowedTone?.let { WarmEngines.unborrowTone(it) }
                runCatching { platform?.stop() }
                abandonTone(toneSlot, toneLoad)
            }
            _phase.value = MicPhase.PROCESSING
            AppLog.i(TAG, "phase PROCESSING bytes=${file.length()}")
            _level.value = 0f
            pushWidgets()
            val finalized = finalize(file, captured)
            _text.value = finalized
            val document = NoteDocument(text = finalized.trim())
            noteId = repository.create(document = document, audioPath = file.absolutePath)
            _noteId.value = noteId
            _phase.value = MicPhase.DONE
            AppLog.i(TAG, "phase DONE bytes=${file.length()} durationMs=${elapsedSince(startedAt)}")
            pushWidgets()
            delay(800)
            _phase.value = MicPhase.IDLE
            AppLog.i(TAG, "phase IDLE")
            _level.value = 0f
            pushWidgets()
        } catch (error: Throwable) {
            Log.e(TAG, "recording failed", error)
            val elapsed = elapsedSince(startedAt)
            if (error is kotlinx.coroutines.CancellationException) {
                AppLog.i(TAG, "phase IDLE cancelled durationMs=$elapsed")
            } else {
                AppLog.e(TAG, "phase IDLE recording failed file=${audioBase ?: "-"} durationMs=$elapsed", error)
            }
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

    private fun loadTone(slot: ModelLoadSlot<OnlineRecognizer>, startedAt: Long): Job {
        toneJob?.cancel()
        val job = background.launch {
            val scope = this
            var owned: OnlineRecognizer? = null
            var reported = false
            try {
                SherpaLoadLock.mutex.withLock {
                    if (!scope.isActive) {
                        AppLog.i(TAG, "model ready elapsedMs=${elapsedSince(startedAt)} discarded=1")
                        reported = true
                        return@withLock
                    }
                    owned = runCatching {
                        SherpaEngines.onlineTone(models.directory(ModelKind.T_ONE))
                    }.onFailure { error ->
                        Log.e(TAG, "T-one load crashed", error)
                        AppLog.e(TAG, "tone load failed", error)
                    }.getOrNull()
                    val elapsed = elapsedSince(startedAt)
                    if (slot.publish(owned)) {
                        AppLog.i(TAG, "model ready elapsedMs=$elapsed ok=${owned != null}")
                        owned = null
                    } else {
                        AppLog.i(TAG, "model ready elapsedMs=$elapsed discarded=1")
                    }
                    reported = true
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                if (!reported) AppLog.i(TAG, "model ready elapsedMs=${elapsedSince(startedAt)} discarded=1")
                throw error
            } finally {
                runCatching { owned?.release() }
            }
        }
        toneJob = job
        return job
    }

    private fun abandonTone(slot: ModelLoadSlot<OnlineRecognizer>?, job: Job?) {
        val leftover = slot?.close()
        runCatching { leftover?.release() }
        job?.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun capture(
        file: File,
        startedAt: Long,
        tone: ModelLoadSlot<OnlineRecognizer>?,
        sharedTone: OnlineRecognizer?,
        platform: PlatformTranscriber?,
        openPlatform: () -> PlatformTranscriber,
        freezeLiveText: Boolean,
    ): String {
        var mic = openMic()
        AppLog.i(TAG, "mic opened elapsedMs=${elapsedSince(startedAt)} source=${mic.source}")
        val writer = WavWriter(file)
        var buffer = ByteArray(mic.bufferSize)
        var platformRef = platform
        var toneRecognizer: OnlineRecognizer? = null
        var stream: OnlineStream? = null
        var toneSettled = tone == null
        var toneAttached = false
        var liveRoute = when {
            freezeLiveText -> "hint"
            sharedTone != null || tone != null -> "tone"
            else -> "platform"
        }
        var committed = ""
        var smoothed = 0f
        var wrote = 0
        var strikes = 0
        var reopens = 0
        var platformStarted = false
        var platformRetired = false
        var heard = false
        if (!freezeLiveText && sharedTone != null) {
            val opened = runCatching { sharedTone.createStream() }
                .onFailure { error ->
                    Log.e(TAG, "T-one stream failed", error)
                    AppLog.e(TAG, "tone stream failed", error)
                }
                .getOrNull()
            if (opened != null) {
                toneRecognizer = sharedTone
                stream = opened
                toneAttached = true
                liveRoute = "tone"
                AppLog.i(TAG, "route=tone warm=1 elapsedMs=${elapsedSince(startedAt)}")
            } else {
                platformRef = platformRef ?: openPlatform()
                liveRoute = "platform"
                AppLog.i(TAG, "route=platform elapsedMs=${elapsedSince(startedAt)}")
                _messages.tryEmit("Модель T-one не открылась. Запись идёт без неё.")
            }
        }
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
                        platformRef?.stop()
                        _messages.tryEmit(
                            "Микрофон отдан системному распознаванию. Аудио пишется дальше — для живого текста установите T-one.",
                        )
                    }
                    if (reopens >= 2) {
                        AppLog.w(TAG, "mic stalled reopens=$reopens read=$read")
                        break
                    }
                    reopens++
                    val reopened = runCatching { reopen(mic) }
                    if (reopened.isFailure) {
                        val error = reopened.exceptionOrNull()
                        Log.e(TAG, "mic reopen failed", error)
                        AppLog.e(TAG, "mic reopen failed n=$reopens", error)
                        break
                    }
                    mic = reopened.getOrThrow()
                    buffer = ByteArray(mic.bufferSize)
                    AppLog.w(TAG, "mic reopened n=$reopens source=${mic.source}")
                    continue
                }
                strikes = 0
                writer.write(buffer, read)
                wrote += read
                if (!heard && read > 0) {
                    heard = true
                    AppLog.i(TAG, "first pcm elapsedMs=${elapsedSince(startedAt)}")
                }
                val floats = Pcm.toFloats(buffer, read)
                smoothed = smoothed * 0.65f + Pcm.level(floats) * 0.35f
                _level.value = smoothed
                if (!freezeLiveText && !toneSettled && tone != null) {
                    when (val claim = tone.claim()) {
                        ModelLoadSlot.Claim.Pending -> Unit
                        is ModelLoadSlot.Claim.Taken -> {
                            toneSettled = true
                            val recognizer = claim.value
                            val opened = recognizer?.let { ready ->
                                runCatching { ready.createStream() }
                                    .onFailure { error ->
                                        Log.e(TAG, "T-one stream failed", error)
                                        AppLog.e(TAG, "tone stream failed", error)
                                    }
                                    .getOrNull()
                            }
                            if (recognizer != null && opened != null) {
                                toneRecognizer = recognizer
                                stream = opened
                                toneAttached = true
                                liveRoute = "tone"
                                AppLog.i(TAG, "route=tone elapsedMs=${elapsedSince(startedAt)}")
                            } else {
                                if (recognizer != null && recognizer !== sharedTone) {
                                    runCatching { recognizer.release() }
                                }
                                platformRef = openPlatform()
                                liveRoute = "platform"
                                AppLog.i(TAG, "route=platform elapsedMs=${elapsedSince(startedAt)}")
                                _messages.tryEmit("Модель T-one не открылась. Запись идёт без неё.")
                            }
                        }
                    }
                }
                val activeRecognizer = toneRecognizer
                val activeStream = stream
                if (!freezeLiveText && activeRecognizer != null && activeStream != null) {
                    val fed = feedTone(activeRecognizer, activeStream, floats, finish = false, committed)
                    committed = fed.committed
                    if (fed.failed) {
                        runCatching { activeStream.release() }
                        stream = null
                        if (activeRecognizer !== sharedTone) {
                            runCatching { activeRecognizer.release() }
                        }
                        toneRecognizer = null
                    }
                }
                if (
                    !freezeLiveText &&
                    platformRef != null &&
                    !platformStarted &&
                    !platformRetired &&
                    wrote >= WARMUP_BYTES
                ) {
                    platformRef?.start()
                    platformStarted = true
                }
                pushWidgets()
            }
            val closingRecognizer = toneRecognizer
            val closingStream = stream
            if (!freezeLiveText && closingRecognizer != null && closingStream != null) {
                val fed = feedTone(closingRecognizer, closingStream, FloatArray(0), finish = true, committed)
                committed = fed.committed
            }
        } finally {
            runCatching { platformRef?.stop() }
            runCatching { mic.record.stop() }
            runCatching { mic.record.release() }
            runCatching { writer.close() }
            runCatching { stream?.release() }
            if (toneRecognizer != null && toneRecognizer !== sharedTone) {
                runCatching { toneRecognizer?.release() }
            }
            val audioMs = wrote * 1000L / (SAMPLE_RATE * 2)
            val attached = if (liveRoute == "tone") " attached=${if (toneAttached) 1 else 0}" else ""
            AppLog.i(
                TAG,
                "capture bytes=$wrote durationMs=$audioMs elapsedMs=${elapsedSince(startedAt)} route=$liveRoute$attached",
            )
        }
        if (freezeLiveText) {
            _text.value = MicRouting.liveLine(LiveMode.TONE_COLD, committed)
            return _text.value
        }
        return if (toneAttached) LiveTranscript.prefer(committed, _text.value) else _text.value
    }

    private data class ToneFeed(val committed: String, val failed: Boolean)

    /**
     * [committed] stays finalized text. The current hypothesis is only the
     * open segment and is replaced on every decode. It is appended once when
     * the stream endpoints or when capture finishes. [_text] (and therefore
     * the widget) shows the same join, never the partial written back into
     * [committed].
     */
    private fun feedTone(
        recognizer: OnlineRecognizer,
        stream: OnlineStream,
        floats: FloatArray,
        finish: Boolean,
        committed: String,
    ): ToneFeed {
        var next = LiveTranscript(committed = committed)
        var published = false
        val failed = runCatching {
            if (finish) {
                stream.inputFinished()
            } else {
                stream.acceptWaveform(floats, SAMPLE_RATE)
            }
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            val partial = recognizer.getResult(stream).text
            val endpoint = !finish && recognizer.isEndpoint(stream)
            next = next.onPartial(partial)
            if (endpoint || finish) next = next.onEndpoint()
            _text.value = next.display
            published = true
            if (endpoint) recognizer.reset(stream)
        }.onFailure { error ->
            Log.e(TAG, "T-one decode stopped", error)
            AppLog.e(TAG, "tone decode stopped", error)
        }.isFailure
        val committedOut = if (failed && !published) committed else next.committed
        return ToneFeed(committedOut, failed)
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
            return OpenMic(recorder, bufferSize, sourceLabel(source))
        }
        error(failure)
    }

    private fun sourceLabel(source: Int): String {
        return when (source) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "voice"
            MediaRecorder.AudioSource.MIC -> "mic"
            else -> "source-$source"
        }
    }

    private fun reopen(current: OpenMic): OpenMic {
        runCatching { current.record.stop() }
        current.record.release()
        return openMic()
    }

    private data class OpenMic(val record: AudioRecord, val bufferSize: Int, val source: String)

    private suspend fun finalize(file: File, live: String): String {
        val fallback = live.trim()
        val installed = models.isInstalled(ModelKind.GIGA_AM)
        val enabled = settings.isModelEnabled(ModelKind.GIGA_AM)
        if (installed && !enabled) AppLog.i(TAG, "route=disabled kind=GIGA_AM")
        if (!MicRouting.useGiga(installed, enabled) || !file.exists() || file.length() < 44) {
            return fallback
        }
        AppLog.i(TAG, "route=giga bytes=${file.length()}")
        val borrowed = WarmEngines.borrowGiga()
        val recognizer = if (borrowed != null) {
            borrowed
        } else {
            try {
                withContext(Dispatchers.Default) {
                    SherpaLoadLock.mutex.withLock {
                        SherpaEngines.offlineGiga(models.directory(ModelKind.GIGA_AM))
                    }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.e(TAG, "GigaAM init crashed", error)
                AppLog.e(TAG, "giga failed", error)
                null
            }
        }
        if (recognizer == null) {
            AppLog.w(TAG, "giga failed")
            return fallback
        }
        return try {
            val text = withContext(Dispatchers.Default) {
                SherpaLoadLock.mutex.withLock {
                    SherpaEngines.transcribeFile(recognizer, file.absolutePath)
                }
            }
            text.ifBlank { fallback }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e(TAG, "GigaAM decode crashed", error)
            AppLog.e(TAG, "giga failed", error)
            fallback
        } finally {
            if (borrowed != null) {
                WarmEngines.unborrowGiga(borrowed)
            } else {
                runCatching { recognizer.release() }
            }
        }
    }

    private var lastWidgetAt = 0L

    private fun pushWidgets() {
        val now = SystemClock.elapsedRealtime()
        val phase = _phase.value
        if (phase == MicPhase.RECORDING && now - lastWidgetAt < 120) return
        lastWidgetAt = now
        WidgetUpdater.push(context, phase, _level.value, _text.value)
    }

    private fun elapsedSince(startedAt: Long): Long {
        return if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt
    }

    private companion object {
        const val TAG = "RecordingSession"
        const val SAMPLE_RATE = 16_000
        const val WARMUP_BYTES = SAMPLE_RATE / 10 * 2
        const val BEEP_GAP_MS = 80L
    }
}
