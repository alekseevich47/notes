package com.alekseevich47.notes.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.alekseevich47.notes.data.download.ModelDownloader
import com.alekseevich47.notes.domain.ModelKind
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pass1: sherpa T-one streaming when model installed; else SpeechRecognizer partials.
 * Pass2: GigaAM v3 e2e offline finalize for punctuation / digits / case.
 */
@Singleton
class TwoPassSttEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: ModelDownloader,
) : SttEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _partials = MutableStateFlow("")
    private var online: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var offline: OfflineRecognizer? = null
    private var speech: SpeechRecognizer? = null
    private val committed = StringBuilder()
    private var usingSherpa = false

    override val ready: Boolean
        get() = models.isInstalled(ModelKind.T_ONE) ||
            models.isInstalled(ModelKind.GIGA_AM) ||
            SpeechRecognizer.isRecognitionAvailable(context)

    fun sherpaOnlineReady(): Boolean = models.isInstalled(ModelKind.T_ONE)
    fun sherpaOfflineReady(): Boolean = models.isInstalled(ModelKind.GIGA_AM)

    override fun partials(): Flow<String> = _partials.asStateFlow()

    override suspend fun startSession() {
        withContext(Dispatchers.Main) {
            releaseSessionOnly()
            committed.clear()
            _partials.value = ""
            usingSherpa = false

            if (models.isInstalled(ModelKind.T_ONE)) {
                online = SherpaEngineFactory.createOnlineTone(models.modelDir(ModelKind.T_ONE))
                stream = online?.createStream()
                usingSherpa = stream != null
            }
            if (models.isInstalled(ModelKind.GIGA_AM)) {
                offline = SherpaEngineFactory.createOfflineGiga(models.modelDir(ModelKind.GIGA_AM))
            }
            if (!usingSherpa) {
                startSpeechRecognizer()
            }
            Log.i(TAG, "session start sherpaOnline=$usingSherpa offline=${offline != null}")
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
                    scope.launch(Dispatchers.Main) { restartSpeech() }
                }
                override fun onError(error: Int) {
                    scope.launch(Dispatchers.Main) { restartSpeech() }
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty().trim()
                    if (text.isNotBlank()) {
                        if (committed.isNotEmpty()) committed.append(' ')
                        committed.append(text)
                        publish(committed.toString())
                    }
                    scope.launch(Dispatchers.Main) { restartSpeech() }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    publish(join(committed.toString(), text))
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
        val recognizer = online ?: return@withContext
        val onlineStream = stream ?: return@withContext
        onlineStream.acceptWaveform(samples, sampleRate)
        while (recognizer.isReady(onlineStream)) {
            recognizer.decode(onlineStream)
        }
        val text = recognizer.getResult(onlineStream).text.trim()
        if (text.isNotBlank()) {
            publish(join(committed.toString(), text))
        }
        if (recognizer.isEndpoint(onlineStream)) {
            if (text.isNotBlank()) {
                if (committed.isNotEmpty()) committed.append(' ')
                committed.append(text)
            }
            recognizer.reset(onlineStream)
            publish(committed.toString())
        }
    }

    override suspend fun finishUtterance(): String = withContext(Dispatchers.Main) {
        speech?.stopListening()
        speech?.cancel()
        speech?.destroy()
        speech = null

        val recognizer = online
        val onlineStream = stream
        if (recognizer != null && onlineStream != null) {
            onlineStream.inputFinished()
            while (recognizer.isReady(onlineStream)) {
                recognizer.decode(onlineStream)
            }
            val text = recognizer.getResult(onlineStream).text.trim()
            if (text.isNotBlank()) {
                if (committed.isNotEmpty()) committed.append(' ')
                committed.append(text)
            }
        }
        val result = committed.toString().ifBlank { _partials.value }.trim()
        publish(result)
        result
    }

    override suspend fun finalizeWithOffline(wavFile: File): String = withContext(Dispatchers.Default) {
        val recognizer = offline
        if (recognizer != null && wavFile.exists()) {
            val polished = SherpaEngineFactory.decodeWavFile(recognizer, wavFile.absolutePath)
            if (polished.isNotBlank()) {
                publish(polished)
                return@withContext polished
            }
        }
        _partials.value.trim()
    }

    override fun release() {
        releaseSessionOnly()
    }

    private fun releaseSessionOnly() {
        speech?.destroy()
        speech = null
        stream?.release()
        stream = null
        online?.release()
        online = null
        offline?.release()
        offline = null
    }

    private fun publish(text: String) {
        _partials.value = text
    }

    private fun join(left: String, right: String): String = buildString {
        append(left)
        if (left.isNotBlank() && right.isNotBlank()) append(' ')
        append(right)
    }

    companion object {
        private const val TAG = "TwoPassStt"
    }
}
