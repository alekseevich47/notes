package com.alekseevich47.notes.stt

/**
 * Where live text comes from while AudioRecord is writing the WAV.
 *
 * T-one reads the same PCM buffer as the file, so the microphone stays with
 * one client. SpeechRecognizer opens a second capture. On some OEMs (Tecno /
 * HiOS and a few others) that client takes the mic exclusively and AudioRecord
 * then returns errors or silence. When T-one is actually loaded we never start
 * SpeechRecognizer. Without it, AudioRecord starts first and SpeechRecognizer
 * is attached only after samples are flowing — and dropped again if it steals
 * the mic, so the WAV still finishes.
 *
 * [LiveMode.TONE_COLD] is the exception: warmup is off, so T-one is not loaded
 * and SpeechRecognizer is not started either. The live line stays
 * [MicRouting.COLD_TONE_TEXT].
 */
enum class LiveTranscriptRoute {
    TONE_ON_RECORD,
    PLATFORM_AFTER_RECORD,
}

/** Whether T-one may be constructed. Missing and disabled models never block the mic. */
enum class ToneStartup {
    PARALLEL,
    SKIP,
}

/**
 * Live-text policy for one recording.
 *
 * [SYSTEM] uses SpeechRecognizer and never loads T-one.
 * [TONE_WARM] borrows the warmup recognizer when the service holds one, and
 * otherwise loads T-one the way a recording did before the switch existed.
 * [TONE_COLD] keeps the WAV and shows [MicRouting.COLD_TONE_TEXT] without
 * loading T-one or appending speech to that sentence.
 */
enum class LiveMode {
    SYSTEM,
    TONE_WARM,
    TONE_COLD,
}

object MicRouting {
    const val COLD_TONE_TEXT =
        "Чтобы видеть текст во время записи, включите в настройках автозагрузку моделей или системное распознавание."

    fun liveMode(recognizer: String, warmupOnBoot: Boolean): LiveMode {
        return when {
            recognizer == "system" -> LiveMode.SYSTEM
            warmupOnBoot -> LiveMode.TONE_WARM
            else -> LiveMode.TONE_COLD
        }
    }

    /** Cold T-one never grows with recognized speech. Other modes pass the text through. */
    fun liveLine(mode: LiveMode, recognized: String): String {
        return if (mode == LiveMode.TONE_COLD) COLD_TONE_TEXT else recognized
    }

    fun route(toneReady: Boolean): LiveTranscriptRoute {
        return if (toneReady) {
            LiveTranscriptRoute.TONE_ON_RECORD
        } else {
            LiveTranscriptRoute.PLATFORM_AFTER_RECORD
        }
    }

    fun toneStartup(installed: Boolean, enabled: Boolean): ToneStartup {
        return if (installed && enabled) ToneStartup.PARALLEL else ToneStartup.SKIP
    }

    /** Disabled GigaAM keeps the live transcript. The files stay on disk. */
    fun useGiga(installed: Boolean, enabled: Boolean): Boolean = installed && enabled
}
