package com.alekseevich47.notes.player

data class PlayerUi(
    val noteId: Long? = null,
    val title: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Media is loaded for this note. A preview of the open note is not engaged. */
    val engaged: Boolean = false,
)

/**
 * The last playback notification that was actually posted.
 * [wallMs] is a monotonic clock reading taken at that post.
 */
data class NotificationPost(
    val noteId: Long,
    val title: String,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val wallMs: Long,
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
    /** Two deliveries of one notification tap, not a second press. */
    const val TRANSPORT_ECHO_MS: Long = 200L

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
            // The timeline still belongs to the previous item. Drop its clock.
            // Callers pass mediaMatches for an explicit play/pause command via commandVisible,
            // including while the new duration is held at 0, so this branch does not eat that command.
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

    fun opening(noteId: Long, title: String, durationMs: Long = 0L): PlayerUi {
        return PlayerUi(
            noteId = noteId,
            title = title,
            playing = false,
            positionMs = 0L,
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    /**
     * The note already loaded in the player. Opening that same note must not
     * reset position or the play/pause button.
     */
    fun keepLoadedSession(openNoteId: Long, sessionNoteId: Long?, engaged: Boolean): Boolean {
        return engaged && sessionNoteId == openNoteId
    }

    /** The bar already describes this note at rest, so a second bind can leave it alone. */
    fun alreadyDescribed(openNoteId: Long, sessionNoteId: Long?, engaged: Boolean, playing: Boolean): Boolean {
        return !engaged && !playing && sessionNoteId == openNoteId
    }

    /**
     * The in-app icon follows the player transport. A notification pause clears
     * playWhenReady on the player without going through the app's own pause call.
     * A replay seek still owns the command until play() lands, and so does the
     * gap while a new item is being prepared.
     */
    fun playingCommand(
        commandedPlaying: Boolean,
        playWhenReady: Boolean,
        replaying: Boolean,
        armingPlay: Boolean = false,
    ): Boolean {
        if (replaying || armingPlay) return commandedPlaying
        return playWhenReady
    }

    /**
     * A media-session pause and the notification's ACTION_TOGGLE can both arrive
     * for one tap. The second one shows up just after the transport already stopped.
     */
    fun echoedTransportPause(pausedAtMs: Long, nowMs: Long, windowMs: Long = TRANSPORT_ECHO_MS): Boolean {
        if (pausedAtMs <= 0L) return false
        val delta = nowMs - pausedAtMs
        return delta in 0L..windowMs
    }

    fun sameItem(currentMediaId: String?, noteId: Long): Boolean {
        return currentMediaId != null && currentMediaId == noteId.toString()
    }

    /**
     * The note already loaded for this session. [currentMediaId] lags setMediaItem,
     * but the session id is written on the same frame as play().
     */
    fun sessionMatches(sessionNoteId: Long?, currentMediaId: String?, noteId: Long): Boolean {
        return sessionNoteId == noteId || sameItem(currentMediaId, noteId)
    }

    /**
     * A second play for the note that is already running pauses at the current position.
     * Ended playback is not running: the next play starts again from 0.
     */
    fun pauseInstead(sessionMatches: Boolean, commandedPlaying: Boolean, ended: Boolean): Boolean {
        return sessionMatches && commandedPlaying && !ended
    }

    /** Stale ENDED from the previous item is not the end of this note. */
    fun endedOnTimeline(timelineMatches: Boolean, playbackEnded: Boolean): Boolean {
        return timelineMatches && playbackEnded
    }

    /**
     * Show the explicit play/pause command while the new item's duration is still held at 0.
     * Passing this as [project]'s mediaMatches keeps that command on the same frame as play() and pause().
     */
    fun commandVisible(timelineMatches: Boolean, holdingDuration: Boolean): Boolean {
        return timelineMatches || holdingDuration
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

    /**
     * True when playback position is the anchor plus wall-clock time, within
     * [toleranceMs]. A seek falls outside that window.
     */
    fun playbackClockSteady(
        anchorPositionMs: Long,
        anchorWallMs: Long,
        positionMs: Long,
        nowWallMs: Long,
        toleranceMs: Long = NOTIFICATION_DRIFT_MS,
    ): Boolean {
        val expected = anchorPositionMs + (nowWallMs - anchorWallMs)
        val drift = positionMs - expected
        val absDrift = if (drift < 0L) -drift else drift
        return absDrift <= toleranceMs
    }

    /**
     * Steady playback must not post again. The shade seek bar advances from
     * the media session's position, duration, and speed. A new note, title,
     * play/pause, duration, or seek does post.
     */
    fun shouldPostPlaybackNotification(previous: NotificationPost?, next: NotificationPost): Boolean {
        if (previous == null) return true
        if (previous.noteId != next.noteId) return true
        if (previous.title != next.title) return true
        if (previous.playing != next.playing) return true
        if (previous.durationMs != next.durationMs) return true
        if (!next.playing) return previous.positionMs != next.positionMs
        return !playbackClockSteady(
            anchorPositionMs = previous.positionMs,
            anchorWallMs = previous.wallMs,
            positionMs = next.positionMs,
            nowWallMs = next.wallMs,
        )
    }

    const val NOTIFICATION_DRIFT_MS: Long = 1_200L

    /** Null stops the main-thread poll so a paused player does not wake the CPU. */
    fun pollIntervalMs(playing: Boolean): Long? = if (playing) TICK_MS else null

    fun replayFromStart(ended: Boolean): Boolean = ended
}
