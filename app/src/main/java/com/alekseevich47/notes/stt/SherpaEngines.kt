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

object SherpaEngines {
    private const val TAG = "SherpaEngines"

    fun onlineTone(modelDir: File): OnlineRecognizer? = runCatching {
        val model = File(modelDir, "model.onnx")
        val tokens = File(modelDir, "tokens.txt")
        require(model.isFile && tokens.isFile)
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80, dither = 0f),
            modelConfig = OnlineModelConfig(
                toneCtc = OnlineToneCtcModelConfig(model = model.absolutePath),
                tokens = tokens.absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
        )
        OnlineRecognizer(assetManager = null, config = config)
    }.onFailure { Log.e(TAG, "T-one init failed", it) }.getOrNull()

    fun offlineGiga(modelDir: File): OfflineRecognizer? = runCatching {
        val encoder = File(modelDir, "encoder.int8.onnx")
        val decoder = File(modelDir, "decoder.onnx")
        val joiner = File(modelDir, "joiner.onnx")
        val tokens = File(modelDir, "tokens.txt")
        require(encoder.isFile && decoder.isFile && joiner.isFile && tokens.isFile)
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 64, dither = 0f),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    joiner = joiner.absolutePath,
                ),
                tokens = tokens.absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "nemo_transducer",
            ),
            decodingMethod = "greedy_search",
        )
        OfflineRecognizer(assetManager = null, config = config)
    }.onFailure { Log.e(TAG, "GigaAM init failed", it) }.getOrNull()

    fun transcribeFile(recognizer: OfflineRecognizer, wavPath: String): String = runCatching {
        val wave = WaveReader.readWave(wavPath)
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(wave.samples, wave.sampleRate)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }.onFailure { Log.e(TAG, "GigaAM decode failed", it) }.getOrDefault("")
}
