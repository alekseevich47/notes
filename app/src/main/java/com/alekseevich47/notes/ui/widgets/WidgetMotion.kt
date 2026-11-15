package com.alekseevich47.notes.ui.widgets

/**
 * Frame tables for the home-screen widgets. RemoteViews has no animator, so the
 * service steps these by hand: mic pulse, processing dot wave, check pop, and
 * the 4×2 transcript fade.
 */
object WidgetMotion {
    const val FRAME_COUNT = 6

    fun dotFrame(frame: Int): Int = frame.mod(FRAME_COUNT)

    fun recordingScale(frame: Int, level: Float): Float {
        val wave = floatArrayOf(0.96f, 1.0f, 1.05f, 1.08f, 1.02f, 0.98f)
        return wave[frame.mod(FRAME_COUNT)] + level.coerceIn(0f, 1f) * 0.07f
    }

    fun recordingAlpha(frame: Int, level: Float): Float {
        val wave = floatArrayOf(0.82f, 0.9f, 1f, 0.96f, 0.88f, 0.84f)
        val floor = 0.78f + level.coerceIn(0f, 1f) * 0.22f
        return (floor * wave[frame.mod(FRAME_COUNT)]).coerceIn(0.72f, 1f)
    }

    fun processingScale(frame: Int): Float {
        val wave = floatArrayOf(0.92f, 0.97f, 1.04f, 1.08f, 1.0f, 0.94f)
        return wave[frame.mod(FRAME_COUNT)]
    }

    fun doneScale(frame: Int): Float {
        val pop = floatArrayOf(0.70f, 0.86f, 1.08f, 1.02f, 1f, 1f)
        return pop[frame.mod(FRAME_COUNT)]
    }

    /**
     * Older lines are a fixed fraction of the newest line. The newest line
     * eases from dim to opaque across [FRAME_COUNT] steps, so a rolled
     * transcript fades in instead of popping.
     */
    fun transcriptAlpha(line: Int, count: Int, fadeFrame: Int): Float {
        if (count <= 0 || line !in 0 until count) return 0f
        val steps = floatArrayOf(0.40f, 0.56f, 0.70f, 0.82f, 0.93f, 1f)
        val newest = steps[fadeFrame.coerceIn(0, steps.lastIndex)]
        val depth = count - 1 - line
        val factors = floatArrayOf(1f, 0.68f, 0.46f, 0.28f, 0.16f)
        val factor = factors.getOrElse(depth) { 0.12f }
        return newest * factor
    }

    fun shouldResetFade(previous: List<String>, next: List<String>): Boolean {
        if (previous == next) return false
        if (previous.size != next.size) return true
        return previous.dropLast(1) != next.dropLast(1)
    }

    fun colorWithAlpha(color: Int, alpha: Float): Int {
        val channel = (alpha.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (channel shl 24)
    }
}
