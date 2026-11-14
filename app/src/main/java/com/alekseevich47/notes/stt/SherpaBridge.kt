package com.alekseevich47.notes.stt

import android.util.Log
import java.io.File
import java.lang.reflect.Constructor

/**
 * Reflection bridge to sherpa-onnx Java/Kotlin API so the app compiles
 * without hard-linking the AAR. When native AAR + models are present,
 * streaming T-one and offline GigaAM are used.
 */
object SherpaBridge {
    private const val TAG = "SherpaBridge"

    @Volatile private var nativeOk: Boolean? = null

    fun isNativeAvailable(): Boolean {
        nativeOk?.let { return it }
        val ok = runCatching {
            System.loadLibrary("sherpa-onnx-jni")
            true
        }.getOrElse {
            Log.i(TAG, "sherpa-onnx-jni not loaded: ${it.message}")
            false
        }
        nativeOk = ok
        return ok
    }

    fun createOnlineTone(modelDir: File): Any? {
        if (!isNativeAvailable()) return null
        return runCatching {
            val configCls = Class.forName("com.k2fsa.sherpa.onnx.OnlineRecognizerConfig")
            val modelCfgCls = Class.forName("com.k2fsa.sherpa.onnx.OnlineModelConfig")
            val toneCls = Class.forName("com.k2fsa.sherpa.onnx.OnlineToneCtcModelConfig")
            val tone = toneCls.getDeclaredConstructor().newInstance()
            toneCls.getField("model").set(tone, File(modelDir, "model.onnx").absolutePath)

            val modelCfg = modelCfgCls.getDeclaredConstructor().newInstance()
            runCatching { modelCfgCls.getField("tokens").set(modelCfg, File(modelDir, "tokens.txt").absolutePath) }
            runCatching { modelCfgCls.getMethod("setTokens", String::class.java).invoke(modelCfg, File(modelDir, "tokens.txt").absolutePath) }
            runCatching { modelCfgCls.getField("numThreads").setInt(modelCfg, 2) }
            runCatching { modelCfgCls.getField("provider").set(modelCfg, "cpu") }
            runCatching { modelCfgCls.getField("tOneCtc").set(modelCfg, tone) }
            runCatching { modelCfgCls.getField("toneCtc").set(modelCfg, tone) }

            val cfg = configCls.getDeclaredConstructor().newInstance()
            runCatching { configCls.getField("modelConfig").set(cfg, modelCfg) }
            runCatching { configCls.getField("decodingMethod").set(cfg, "greedy_search") }

            val recognizerCls = Class.forName("com.k2fsa.sherpa.onnx.OnlineRecognizer")
            recognizerCls.getDeclaredConstructor(configCls).newInstance(cfg)
        }.onFailure { Log.e(TAG, "createOnlineTone failed", it) }.getOrNull()
    }

    fun createOfflineGiga(modelDir: File): Any? {
        if (!isNativeAvailable()) return null
        return runCatching {
            val configCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizerConfig")
            val modelCfgCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineModelConfig")
            val transducerCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig")

            val transducer = transducerCls.getDeclaredConstructor().newInstance()
            transducerCls.getField("encoder").set(transducer, File(modelDir, "encoder.int8.onnx").absolutePath)
            transducerCls.getField("decoder").set(transducer, File(modelDir, "decoder.onnx").absolutePath)
            transducerCls.getField("joiner").set(transducer, File(modelDir, "joiner.onnx").absolutePath)

            val modelCfg = modelCfgCls.getDeclaredConstructor().newInstance()
            modelCfgCls.getField("transducer").set(modelCfg, transducer)
            modelCfgCls.getField("tokens").set(modelCfg, File(modelDir, "tokens.txt").absolutePath)
            runCatching { modelCfgCls.getField("numThreads").setInt(modelCfg, 2) }
            runCatching { modelCfgCls.getField("provider").set(modelCfg, "cpu") }
            runCatching { modelCfgCls.getField("modelType").set(modelCfg, "nemo_transducer") }

            val cfg = configCls.getDeclaredConstructor().newInstance()
            configCls.getField("modelConfig").set(cfg, modelCfg)
            runCatching { configCls.getField("featConfig").let { f ->
                // feature_dim=64 for GigaAM when available
            } }

            val recognizerCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
            recognizerCls.getDeclaredConstructor(configCls).newInstance(cfg)
        }.onFailure { Log.e(TAG, "createOfflineGiga failed", it) }.getOrNull()
    }

    fun onlineCreateStream(recognizer: Any): Any? = runCatching {
        recognizer.javaClass.getMethod("createStream").invoke(recognizer)
    }.getOrNull()

    fun onlineAcceptWaveform(stream: Any, sampleRate: Int, samples: FloatArray) {
        runCatching {
            stream.javaClass.getMethod("acceptWaveform", FloatArray::class.java, Int::class.javaPrimitiveType)
                .invoke(stream, samples, sampleRate)
        }.onFailure {
            runCatching {
                stream.javaClass.methods.firstOrNull { it.name == "acceptWaveform" }?.invoke(stream, samples, sampleRate)
            }
        }
    }

    fun onlineDecode(recognizer: Any, stream: Any) {
        while (runCatching {
            recognizer.javaClass.getMethod("isReady", stream.javaClass).invoke(recognizer, stream) as Boolean
        }.getOrDefault(false)) {
            recognizer.javaClass.getMethod("decode", stream.javaClass).invoke(recognizer, stream)
        }
    }

    fun onlineGetText(recognizer: Any, stream: Any): String = runCatching {
        val result = recognizer.javaClass.getMethod("getResult", stream.javaClass).invoke(recognizer, stream)
        result.javaClass.getField("text").get(result) as? String ?: result.toString()
    }.getOrDefault("")

    fun onlineIsEndpoint(recognizer: Any, stream: Any): Boolean = runCatching {
        recognizer.javaClass.getMethod("isEndpoint", stream.javaClass).invoke(recognizer, stream) as Boolean
    }.getOrDefault(false)

    fun onlineReset(recognizer: Any, stream: Any) {
        runCatching { recognizer.javaClass.getMethod("reset", stream.javaClass).invoke(recognizer, stream) }
    }

    fun offlineDecodeFile(recognizer: Any, wavPath: String): String = runCatching {
        val wave = Class.forName("com.k2fsa.sherpa.onnx.WaveReader")
            .getMethod("readWave", String::class.java).invoke(null, wavPath)
        val samples = wave.javaClass.getMethod("getSamples").invoke(wave) as FloatArray
        val sr = wave.javaClass.getMethod("getSampleRate").invoke(wave) as Int
        val stream = recognizer.javaClass.getMethod("createStream").invoke(recognizer)
        stream.javaClass.getMethod("acceptWaveform", FloatArray::class.java, Int::class.javaPrimitiveType)
            .invoke(stream, samples, sr)
        recognizer.javaClass.getMethod("decode", stream.javaClass).invoke(recognizer, stream)
        val result = recognizer.javaClass.getMethod("getResult", stream.javaClass).invoke(recognizer, stream)
        result.javaClass.getField("text").get(result) as? String ?: ""
    }.onFailure { Log.e(TAG, "offlineDecodeFile failed", it) }.getOrDefault("")
}
