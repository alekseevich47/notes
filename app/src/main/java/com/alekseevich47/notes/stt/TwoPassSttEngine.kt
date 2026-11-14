package com.alekseevich47.notes.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.alekseevich47.notes.data.download.ModelDownloader
import com.alekseevich47.notes.domain.ModelKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pass1: sherpa T-one streaming when available, else Android SpeechRecognizer for low-latency partials.
 * Pass2: GigaAM v3 e2e offline finalize when model + native available.
 */
@Singleton
class TwoPassSttEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: ModelDownloader,
) : SttEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _partials = MutableStateFlow("")
    private var onlineRecognizer: Any? = null
    private var onlineStream: Any? = null
    private var offlineRecognizer: Any? = null
    private var speech: SpeechRecognizer? = null
    private var committed = StringBuilder()
    private var lastPartial = ""

    override val ready: Boolean
        get() = models.isInstalled(ModelKind.T_ONE) || SpeechRecognizer.isRecognitionAvailable(context)

    override fun partials(): Flow<String> = _partials.asStateFlow()

    override suspend fun startSession() = withContext(Dispatchers.Main) {
        committed.clear()
        lastPartial = ""
        _partials.value = ""
        onlineRecognizer = null
        onlineStream = null
        offlineRecognizer = null

        if (models.isInstalled(ModelKind.T_ONE) && SherpaBridge.isNativeAvailable()) {
            onlineRecognizer = SherpaBridge.createOnlineTone(models.modelDir(ModelKind.T_ONE))
            onlineStream = onlineRecognizer?.let { SherpaBridge.onlineCreateStream(it) }
        }
        if (models.isInstalled(ModelKind.GIGA_AM) && SherpaBridge.isNativeAvailable()) {
            offlineRecognizer = SherpaBridge.createOfflineGiga(models.modelDir(ModelKind.GIGA_AM))
        }
        if (onlineStream == null) {
            startSpeechRecognizer()
        }
    }

    private fun startSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    // restart for continuous dictation
                    scope.launch(Dispatchers.Main) { restartSpeech() }
                }
                override fun onError(error: Int) {
                    scope.launch(Dispatchers.Main) { restartSpeech() }
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        if (committed.isNotEmpty()) committed.append(' ')
                        committed.append(text.trim())
                        lastPartial = ""
                        _partials.value = committed.toString()
                    }
                    scope.launch(Dispatchers.Main) { restartSpeech() }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    lastPartial = text
                    val shown = buildString {
                        append(committed)
                        if (committed.isNotEmpty() && text.isNotBlank()) append(' ')
                        append(text)
                    }
                    _partials.value = shown
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        restartSpeech()
    }

    private fun restartSpeech() {
        val sr = speech ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { sr.startListening(intent) }
    }

    override suspend fun acceptPcm(samples: FloatArray, sampleRate: Int) = withContext(Dispatchers.Default) {
        val recognizer = onlineRecognizer ?: return@withContext
        val stream = onlineStream ?: return@withContext
        SherpaBridge.onlineAcceptWaveform(stream, sampleRate, samples)
        SherpaBridge.onlineDecode(recognizer, stream)
        val text = SherpaBridge.onlineGetText(recognizer, stream).trim()
        if (text.isNotBlank()) {
            lastPartial = text
            _partials.value = buildString {
                append(committed)
                if (committed.isNotEmpty()) append(' ')
                append(text)
            }
        }
        if (SherpaBridge.onlineIsEndpoint(recognizer, stream)) {
            if (text.isNotBlank()) {
                if (committed.isNotEmpty()) committed.append(' ')
                committed.append(text)
            }
            SherpaBridge.onlineReset(recognizer, stream)
            lastPartial = ""
            _partials.value = committed.toString()
        }
    }

    override suspend fun finishUtterance(): String = withContext(Dispatchers.Main) {
        speech?.stopListening()
        speech?.cancel()
        speech?.destroy()
        speech = null
        val online = onlineRecognizer
        val stream = onlineStream
        if (online != null && stream != null) {
            val text = SherpaBridge.onlineGetText(online, stream).trim()
            if (text.isNotBlank()) {
                if (committed.isNotEmpty()) committed.append(' ')
                committed.append(text)
            }
        }
        val result = committed.toString().ifBlank { _partials.value }.trim()
        _partials.value = result
        result
    }

    override suspend fun finalizeWithOffline(wavFile: File): String = withContext(Dispatchers.Default) {
        val offline = offlineRecognizer
        if (offline != null && wavFile.exists()) {
            val polished = SherpaBridge.offlineDecodeFile(offline, wavFile.absolutePath).trim()
            if (polished.isNotBlank()) {
                _partials.value = polished
                return@withContext polished
            }
        }
        finishUtterance()
    }

    override fun release() {
        speech?.destroy()
        speech = null
        onlineRecognizer = null
        onlineStream = null
        offlineRecognizer = null
    }
}
