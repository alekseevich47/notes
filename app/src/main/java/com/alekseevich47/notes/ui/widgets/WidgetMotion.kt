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
    /** One processing-dot frame. A full cycle is this times [DOT_FRAMES]. */
    const val DOT_FRAME_MS = 40
    const val FADE_FRAMES = 6
    const val DOT_PHASE = 0.16f
    const val SAVED_NOTE_PROMPT = "Нажмите на микрофон, чтобы создать заметку"
    const val LIVE_TEXT_OFF =
        "Чтобы видеть текст во время записи, включите в настройках автозагрузку моделей или системное распознавание."
    const val STATUS_LOADING = "Загрузка..."
    const val STATUS_RECORDING = "Запись..."
    const val STATUS_PROCESSING = "Обработка..."

    /** 4×1 mic button edge as a fraction of the row's inner height. */
    const val WIDE_MIC_HEIGHT_FRACTION = 0.70f
    const val PREFS_NAME = "notes_widget_prefs"
    const val PREF_WARMUP = "warmup_on_boot"
    const val PREF_RECOGNIZER = "recognizer"
    const val PREF_MODEL_LOADING = "model_loading"

    fun dotFrame(frame: Int): Int = frame.mod(DOT_FRAMES)

    /** -1..1 lift for dot [index]. Neighbouring dots share the wave with a phase offset. */
    fun dotLift(frame: Int, index: Int): Float {
        val cycle = frame.mod(DOT_FRAMES) / DOT_FRAMES.toFloat() - index * DOT_PHASE
        return sin(cycle * 2.0 * Math.PI).toFloat()
    }

    /** Audible zoom. 1.0 at silence, about 1.18 at full level, eased by [smoothstep]. */
    fun recordingScale(level: Float): Float = 1f + smoothstep(level) * 0.18f

    fun glowAlpha(level: Float): Float = 0.22f + smoothstep(level) * 0.78f

    /** Stays outside the plate so the halo is still visible when the mic zooms to ~1.18. */
    fun glowScale(level: Float): Float = 1.06f + smoothstep(level) * 0.18f

    /**
     * Low-pass toward [target]. A single tick only covers part of the gap, so a
     * noisy level glides instead of flickering.
     */
    fun easeLevel(current: Float, target: Float): Float {
        val from = unit(current)
        val to = unit(target)
        return from + (to - from) * 0.42f
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
        // The 4×1 mic takes a square chunk of the row, so the text column is narrower.
        val textDp = (widgetWidthDp - 108).coerceAtLeast(64)
        return (textDp / 8.2f).toInt().coerceIn(8, 80)
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

    /**
     * Live text is off only for the on-device recognizer without boot warmup.
     * A missing recognizer is `tone` and a missing warmup flag is false.
     */
    fun liveTextEnabled(recognizer: String?, warmupOnBoot: Boolean): Boolean {
        val mode = recognizer?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "tone"
        return mode != "tone" || warmupOnBoot
    }

    fun modelLoading(prefFlag: Boolean, statusLines: List<String>): Boolean {
        if (prefFlag) return true
        return statusLines.any { it.trim().equals("DOWNLOADING", ignoreCase = true) }
    }

    /** Caption under the 1×1 circle. Null hides the line. */
    fun statusLabel(phase: MicPhase, modelLoading: Boolean): String? = when (phase) {
        MicPhase.DONE -> null
        MicPhase.PROCESSING -> STATUS_PROCESSING
        MicPhase.RECORDING -> if (modelLoading) STATUS_LOADING else STATUS_RECORDING
        MicPhase.IDLE -> if (modelLoading) STATUS_LOADING else null
    }

    /**
     * What the 4×1 text block shows. Live text off always replaces the transcript
     * with [LIVE_TEXT_OFF]. Otherwise idle/done is the prompt and an active
     * recording shows the latest wrapped lines.
     */
    fun displayText(phase: MicPhase, transcript: String, widgetWidthDp: Int, liveText: Boolean, maxLines: Int = 3): String {
        if (!liveText) return LIVE_TEXT_OFF
        if (phase == MicPhase.IDLE || phase == MicPhase.DONE) return SAVED_NOTE_PROMPT
        val lines = wrapWords(transcript, charsForWidth(widgetWidthDp))
        if (lines.isEmpty()) return ""
        return lines.takeLast(maxLines.coerceAtLeast(1)).joinToString("\n")
    }

    fun textMuted(phase: MicPhase, liveText: Boolean): Boolean {
        if (!liveText) return true
        return phase == MicPhase.IDLE || phase == MicPhase.DONE
    }

    /** Outgoing alpha then incoming alpha. They sum to 1 so shared glyphs do not blink. */
    fun crossfade(fadeFrame: Int): Pair<Float, Float> {
        val incoming = smoothstep(fadeProgress(fadeFrame))
        return (1f - incoming) to incoming
    }

    /**
     * How to paint the 4×1 block.
     *
     * An append keeps [base] solid and fades only the new tail, so a stream of
     * partials never blanks the line. When older lines drop out of the window,
     * [outgoing] is that previous block and the two layers crossfade in place.
     */
    data class BlockPaint(
        val incoming: String,
        val solidUntil: Int,
        val suffixAlpha: Float,
        val outgoing: String,
        val outgoingAlpha: Float,
    )

    fun blockPaint(base: String, incoming: String, frame: Int): BlockPaint {
        if (base == incoming || frame >= FADE_FRAMES) {
            return BlockPaint(incoming, incoming.length, 1f, "", 0f)
        }
        val eased = smoothstep(fadeProgress(frame))
        if (incoming.startsWith(base)) {
            return BlockPaint(incoming, base.length, 0.35f + 0.65f * eased, "", 0f)
        }
        val (outAlpha, inAlpha) = crossfade(frame)
        return BlockPaint(incoming, 0, inAlpha, base, outAlpha)
    }

    /**
     * Square edge for the 1×1 plate: the smaller of the cell width and the height
     * left after the status line. Zero when the host has not reported a size yet.
     */
    fun smallMicSideDp(widthDp: Int, heightDp: Int, labelDp: Int): Int {
        if (widthDp <= 0 || heightDp <= 0) return 0
        val innerW = (widthDp - 6).coerceAtLeast(1)
        val innerH = (heightDp - 4 - labelDp.coerceAtLeast(0)).coerceAtLeast(1)
        return minOf(innerW, innerH)
    }

    /** Square 4×1 mic, large enough to read as a button rather than a glyph. */
    fun wideMicSideDp(widthDp: Int, heightDp: Int): Int {
        if (heightDp <= 0) return 0
        val inner = (heightDp - 8).coerceAtLeast(1)
        val side = (inner * WIDE_MIC_HEIGHT_FRACTION).toInt().coerceIn(1, inner)
        if (widthDp <= 0) return side
        val widthCap = (widthDp * 0.46f).toInt().coerceAtLeast(1)
        return side.coerceAtMost(widthCap)
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
