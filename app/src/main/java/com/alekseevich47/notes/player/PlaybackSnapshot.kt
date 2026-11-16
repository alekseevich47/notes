package com.alekseevich47.notes.player

data class PlayerUi(
    val noteId: Long? = null,
    val title: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/**
 * Pure playback snapshot. The player reports a negative unset duration until the
 * timeline is ready, and the current position does not move unless something polls it.
 */
data class PlaybackReading(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
)

object PlaybackSnapshot {
    /** Same sentinel as androidx.media3.common.C.TIME_UNSET. Non-positive reports are unknown. */
    const val TIME_UNSET: Long = Long.MIN_VALUE + 1
    const val TICK_MS: Long = 100L

    fun knownDuration(reportedMs: Long): Long = if (reportedMs > 0L) reportedMs else 0L

    fun project(
        reportedPositionMs: Long,
        reportedDurationMs: Long,
        playing: Boolean,
        ended: Boolean,
        mediaMatches: Boolean,
        holdDuration: Boolean,
        seekOverrideMs: Long? = null,
    ): PlaybackReading {
        if (!mediaMatches) {
            return PlaybackReading(positionMs = 0L, durationMs = 0L, playing = false)
        }
        if (holdDuration) {
            return PlaybackReading(positionMs = 0L, durationMs = 0L, playing = playing && !ended)
        }
        val durationMs = knownDuration(reportedDurationMs)
        // A replay seek must win over the ended snap, or the slider stays at the end.
        val treatEnded = ended && seekOverrideMs == null
        val raw = seekOverrideMs ?: reportedPositionMs
        val positionMs = when {
            treatEnded && durationMs > 0L -> durationMs
            durationMs > 0L -> raw.coerceIn(0L, durationMs)
            else -> raw.coerceAtLeast(0L)
        }
        return PlaybackReading(
            positionMs = positionMs,
            durationMs = durationMs,
            playing = playing && !treatEnded,
        )
    }

    /**
     * Keep the reset (0 / 0) until this item's duration is distinguishable from the
     * previous note. A ready player that still reports the old length is not enough;
     * the timeline has to move on, or playback has to pass through an unset gap first.
     */
    fun stillHolding(
        holding: Boolean,
        reportedDurationMs: Long,
        staleDurationMs: Long,
        readyOrEnded: Boolean,
        timelineOrMedia: Boolean,
        sawGap: Boolean,
    ): Boolean {
        if (!holding) return false
        if (reportedDurationMs > 0L && staleDurationMs > 0L && reportedDurationMs != staleDurationMs) return false
        if (reportedDurationMs > 0L && staleDurationMs <= 0L && (readyOrEnded || timelineOrMedia)) return false
        if (sawGap && reportedDurationMs > 0L && (readyOrEnded || timelineOrMedia)) return false
        return true
    }

    fun opening(noteId: Long, title: String): PlayerUi {
        return PlayerUi(noteId = noteId, title = title, playing = false, positionMs = 0L, durationMs = 0L)
    }

    fun sameItem(currentMediaId: String?, noteId: Long): Boolean {
        return currentMediaId != null && currentMediaId == noteId.toString()
    }

    fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
        val cap = if (durationMs > 0L) durationMs else Long.MAX_VALUE
        return (positionMs + deltaMs).coerceIn(0L, cap)
    }

    fun fractionTarget(durationMs: Long, fraction: Float): Long? {
        if (durationMs <= 0L) return null
        return (durationMs * fraction.coerceIn(0f, 1f)).toLong()
    }

    fun fraction(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun clock(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(total / 60L, total % 60L)
    }

    /** Null stops the main-thread poll so a paused player does not wake the CPU. */
    fun pollIntervalMs(playing: Boolean): Long? = if (playing) TICK_MS else null

    fun replayFromStart(ended: Boolean): Boolean = ended
}
