package com.alekseevich47.notes.stt

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineToneCtcModelConfig
import com.k2fsa.sherpa.onnx.WaveReader
import java.io.File

object SherpaEngineFactory {
    private const val TAG = "SherpaEngine"

    init {
        // Ensure JNI libs from the AAR are loaded early.
        runCatching { System.loadLibrary("onnxruntime") }
        runCatching { System.loadLibrary("sherpa-onnx-c-api") }
        runCatching { System.loadLibrary("sherpa-onnx-cxx-api") }
        runCatching { System.loadLibrary("sherpa-onnx-jni") }
            .onFailure { Log.w(TAG, "preload jni: ${it.message}") }
    }

    fun createOnlineTone(modelDir: File): OnlineRecognizer? = runCatching {
        val model = File(modelDir, "model.onnx")
        val tokens = File(modelDir, "tokens.txt")
        require(model.isFile && tokens.isFile) { "T-one files missing in $modelDir" }

        val modelConfig = OnlineModelConfig().apply {
            toneCtc = OnlineToneCtcModelConfig(model = model.absolutePath)
            this.tokens = tokens.absolutePath
            numThreads = 2
            provider = "cpu"
            debug = false
        }
        val config = OnlineRecognizerConfig().apply {
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80, dither = 0f)
            this.modelConfig = modelConfig
            enableEndpoint = true
            decodingMethod = "greedy_search"
        }
        OnlineRecognizer(assetManager = null, config = config)
    }.onFailure { Log.e(TAG, "createOnlineTone failed", it) }.getOrNull()

    fun createOfflineGiga(modelDir: File): OfflineRecognizer? = runCatching {
        val encoder = File(modelDir, "encoder.int8.onnx")
        val decoder = File(modelDir, "decoder.onnx")
        val joiner = File(modelDir, "joiner.onnx")
        val tokens = File(modelDir, "tokens.txt")
        require(encoder.isFile && decoder.isFile && joiner.isFile && tokens.isFile) {
            "GigaAM files missing in $modelDir"
        }

        val modelConfig = OfflineModelConfig().apply {
            transducer = OfflineTransducerModelConfig(
                encoder = encoder.absolutePath,
                decoder = decoder.absolutePath,
                joiner = joiner.absolutePath,
            )
            this.tokens = tokens.absolutePath
            numThreads = 2
            provider = "cpu"
            modelType = "nemo_transducer"
            debug = false
        }
        val config = OfflineRecognizerConfig().apply {
            // GigaAM uses 64 mel bins
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 64, dither = 0f)
            this.modelConfig = modelConfig
            decodingMethod = "greedy_search"
        }
        OfflineRecognizer(assetManager = null, config = config)
    }.onFailure { Log.e(TAG, "createOfflineGiga failed", it) }.getOrNull()

    fun decodeWavFile(recognizer: OfflineRecognizer, wavPath: String): String = runCatching {
        val wave = WaveReader.readWave(wavPath)
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(wave.samples, wave.sampleRate)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }.onFailure { Log.e(TAG, "decodeWavFile failed", it) }.getOrDefault("")
}
