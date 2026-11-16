package com.alekseevich47.notes.ui.widgets

import com.alekseevich47.notes.domain.MicPhase
import kotlin.math.sin

/**
 * Motion for the home-screen widgets. RemoteViews has no animator, so the
 * recording service samples these often: mic zoom and purple glow follow the
 * audio level, and the processing dots step through a dense overlapping wave.
 */
object WidgetMotion {
    const val DOT_FRAMES = 24
    const val FADE_FRAMES = 6
    const val DOT_PHASE = 0.16f
    const val SAVED_NOTE_PROMPT = "Нажмите на микрофон, чтобы создать заметку"

    fun dotFrame(frame: Int): Int = frame.mod(DOT_FRAMES)

    /** -1..1 lift for dot [index]. Neighbouring dots share the wave with a phase offset. */
    fun dotLift(frame: Int, index: Int): Float {
        val cycle = frame.mod(DOT_FRAMES) / DOT_FRAMES.toFloat() - index * DOT_PHASE
        return sin(cycle * 2.0 * Math.PI).toFloat()
    }

    fun recordingScale(level: Float): Float = 1f + smoothstep(level) * 0.12f

    fun glowAlpha(level: Float): Float = 0.22f + smoothstep(level) * 0.78f

    fun glowScale(level: Float): Float = 1f + smoothstep(level) * 0.08f

    /** Glide toward [target] so sparse audio levels still look continuous. */
    fun easeLevel(current: Float, target: Float): Float {
        val from = unit(current)
        val to = unit(target)
        return from + (to - from) * 0.62f
    }

    fun wordFadeAlpha(fadeFrame: Int): Float {
        val eased = smoothstep(fadeProgress(fadeFrame))
        return 0.72f + 0.28f * eased
    }

    fun wordSlideDp(fadeFrame: Int): Float {
        val eased = smoothstep(fadeProgress(fadeFrame))
        return (1f - eased) * 4f
    }

    fun shouldResetFade(previous: List<String>, next: List<String>): Boolean = previous != next

    fun charsForWidth(widgetWidthDp: Int): Int {
        val textDp = (widgetWidthDp - 62).coerceAtLeast(96)
        return (textDp / 7.4f).toInt().coerceIn(12, 80)
    }

    /**
     * Pack [text] into lines. A word that does not fit the current line moves
     * intact onto the next line.
     */
    fun wrapWords(text: String, maxChars: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val limit = maxChars.coerceAtLeast(4)
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.trim().split(Regex("\\s+"))) {
            if (current.isEmpty()) {
                current = word
                continue
            }
            val candidate = "$current $word"
            if (candidate.length > limit) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    fun linesFor(phase: MicPhase, text: String, widgetWidthDp: Int): List<String> {
        if (phase == MicPhase.IDLE || phase == MicPhase.DONE) return listOf(SAVED_NOTE_PROMPT)
        return wrapWords(text, charsForWidth(widgetWidthDp))
    }

    fun colorWithAlpha(color: Int, alpha: Float): Int {
        val channel = (alpha.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (channel shl 24)
    }

    private fun fadeProgress(fadeFrame: Int): Float {
        return fadeFrame.coerceIn(0, FADE_FRAMES) / FADE_FRAMES.toFloat()
    }

    private fun smoothstep(level: Float): Float {
        val t = unit(level)
        return t * t * (3f - 2f * t)
    }

    private fun unit(value: Float): Float {
        if (value.isNaN() || value.isInfinite()) return 0f
        return value.coerceIn(0f, 1f)
    }
}
