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

object MicRouting {
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
