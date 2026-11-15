package com.alekseevich47.notes.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * System ru-RU recognizer used when the T-one model is not installed.
 * Final chunks are kept and listening restarts so a long note does not stop at the first pause.
 */
class PlatformTranscriber(
    context: Context,
    private val onText: (String) -> Unit,
    private val onHardError: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var committed = ""
    private var partial = ""

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onHardError("Системное распознавание недоступно. Скачайте модель T-one.")
            return
        }
        running = true
        main.post { listen() }
    }

    fun stop() {
        running = false
        main.post {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }
    }

    private fun listen() {
        if (!running) return
        val speech = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            recognizer = it
            it.setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8_000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6_000)
        }
        runCatching { speech.startListening(intent) }
            .onFailure { onHardError("Не удалось запустить распознавание") }
    }

    private fun publish() {
        onText(listOf(committed, partial).filter { it.isNotBlank() }.joinToString(" ").trim())
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (!running) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT,
                -> main.postDelayed({ listen() }, 250)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    onHardError("Нет доступа к микрофону для распознавания")
                else -> main.postDelayed({ listen() }, 400)
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                committed = listOf(committed, text).filter { it.isNotBlank() }.joinToString(" ")
                partial = ""
                publish()
            }
            if (running) main.postDelayed({ listen() }, 200)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            publish()
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
