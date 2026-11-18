package com.alekseevich47.notes

import com.alekseevich47.notes.player.PlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSnapshotTest {
    @Test
    fun unsetDurationIsZero() {
        assertEquals(0L, PlaybackSnapshot.knownDuration(PlaybackSnapshot.TIME_UNSET))
        assertEquals(0L, PlaybackSnapshot.knownDuration(0L))
        assertEquals(0L, PlaybackSnapshot.knownDuration(-1L))
        assertEquals(12_500L, PlaybackSnapshot.knownDuration(12_500L))
    }

    @Test
    fun openingADifferentNoteDropsThePreviousPositionAndDuration() {
        val opened = PlaybackSnapshot.opening(noteId = 4L, title = "Note")
        assertEquals(4L, opened.noteId)
        assertEquals(0L, opened.positionMs)
        assertEquals(0L, opened.durationMs)
        assertFalse(opened.playing)

        val leaked = PlaybackSnapshot.project(
            reportedPositionMs = 40_000L,
            reportedDurationMs = 90_000L,
            playing = true,
            ended = false,
            mediaMatches = false,
            holdDuration = true,
        )
        assertEquals(0L, leaked.positionMs)
        assertEquals(0L, leaked.durationMs)
        assertFalse(leaked.playing)
    }

    @Test
    fun holdHidesThePreviousDurationUntilTheNewTimelineOrReady() {
        assertTrue(
            PlaybackSnapshot.stillHolding(
                holding = true,
                reportedDurationMs = 90_000L,
                staleDurationMs = 90_000L,
                readyOrEnded = false,
                timelineOrMedia = true,
                sawGap = false,
            ),
        )
        assertTrue(
            PlaybackSnapshot.stillHolding(
                holding = true,
                reportedDurationMs = 90_000L,
                staleDurationMs = 90_000L,
                readyOrEnded = true,
                timelineOrMedia = false,
                sawGap = false,
            ),
        )
        assertTrue(
            PlaybackSnapshot.stillHolding(
                holding = true,
                reportedDurationMs = PlaybackSnapshot.TIME_UNSET,
                staleDurationMs = 90_000L,
                readyOrEnded = false,
                timelineOrMedia = true,
                sawGap = true,
            ),
        )
        val held = PlaybackSnapshot.project(
            reportedPositionMs = 12_000L,
            reportedDurationMs = 90_000L,
            playing = true,
            ended = false,
            mediaMatches = true,
            holdDuration = true,
        )
        assertEquals(0L, held.positionMs)
        assertEquals(0L, held.durationMs)
        assertTrue(held.playing)
    }

    @Test
    fun readyOrANewTimelinePublishesTheRealDuration() {
        assertFalse(
            PlaybackSnapshot.stillHolding(
                holding = true,
                reportedDurationMs = 30_000L,
                staleDurationMs = 90_000L,
                readyOrEnded = true,
                timelineOrMedia = false,
                sawGap = false,
            ),
        )
        assertFalse(
            PlaybackSnapshot.stillHolding(
                holding = true,
                reportedDurationMs = 30_000L,
                staleDurationMs = 90_000L,
                readyOrEnded = false,
                timelineOrMedia = true,
                sawGap = false,
            ),
        )
        assertFalse(
            PlaybackSnapshot.stillHolding(
                holding = true,
                reportedDurationMs = 90_000L,
                staleDurationMs = 90_000L,
                readyOrEnded = true,
                timelineOrMedia = false,
                sawGap = true,
            ),
        )
        val ready = PlaybackSnapshot.project(
            reportedPositionMs = 0L,
            reportedDurationMs = 30_000L,
            playing = false,
            ended = false,
            mediaMatches = true,
            holdDuration = false,
        )
        assertEquals(0L, ready.positionMs)
        assertEquals(30_000L, ready.durationMs)
        assertEquals("0:00", PlaybackSnapshot.clock(ready.positionMs))
        assertEquals("0:30", PlaybackSnapshot.clock(ready.durationMs))
    }

    @Test
    fun playingPositionAdvancesOnEachPoll() {
        val first = reading(position = 1_000L, playing = true)
        val next = reading(position = 1_100L, playing = true)
        assertEquals(1_000L, first.positionMs)
        assertEquals(1_100L, next.positionMs)
        assertEquals(10_000L, next.durationMs)
        assertTrue(next.playing)
        assertEquals(PlaybackSnapshot.TICK_MS, PlaybackSnapshot.pollIntervalMs(playing = true))
        assertNull(PlaybackSnapshot.pollIntervalMs(playing = false))
    }

    @Test
    fun seekUpdatesPositionImmediatelyAndClamps() {
        val jumped = PlaybackSnapshot.project(
            reportedPositionMs = 1_000L,
            reportedDurationMs = 10_000L,
            playing = true,
            ended = false,
            mediaMatches = true,
            holdDuration = false,
            seekOverrideMs = 4_500L,
        )
        assertEquals(4_500L, jumped.positionMs)
        assertEquals(4_500L, PlaybackSnapshot.seekTarget(1_000L, 3_500L, 10_000L))
        assertEquals(0L, PlaybackSnapshot.seekTarget(1_000L, -5_000L, 10_000L))
        assertEquals(10_000L, PlaybackSnapshot.seekTarget(9_000L, 5_000L, 10_000L))
        assertEquals(2_500L, PlaybackSnapshot.fractionTarget(10_000L, 0.25f))
        assertNull(PlaybackSnapshot.fractionTarget(0L, 0.5f))
    }

    @Test
    fun endedPlaybackRestsAtTheEndUntilReplaySeeksToStart() {
        val ended = reading(position = 9_800L, playing = false, ended = true)
        assertEquals(10_000L, ended.positionMs)
        assertEquals(10_000L, ended.durationMs)
        assertFalse(ended.playing)
        assertEquals(1f, PlaybackSnapshot.fraction(ended.positionMs, ended.durationMs), 0.0001f)
        assertNull(PlaybackSnapshot.pollIntervalMs(ended.playing))

        assertTrue(PlaybackSnapshot.replayFromStart(ended = true))
        assertFalse(PlaybackSnapshot.replayFromStart(ended = false))
        val replay = PlaybackSnapshot.project(
            reportedPositionMs = 10_000L,
            reportedDurationMs = 10_000L,
            playing = true,
            ended = true,
            mediaMatches = true,
            holdDuration = false,
            seekOverrideMs = 0L,
        )
        assertEquals(0L, replay.positionMs)
        assertEquals(10_000L, replay.durationMs)
        assertTrue(replay.playing)
        assertEquals(0f, PlaybackSnapshot.fraction(replay.positionMs, replay.durationMs), 0.0001f)
    }

    @Test
    fun clockMatchesThePlayerReadout() {
        assertEquals("0:00", PlaybackSnapshot.clock(0L))
        assertEquals("0:00", PlaybackSnapshot.clock(PlaybackSnapshot.TIME_UNSET))
        assertEquals("1:01", PlaybackSnapshot.clock(61_000L))
        assertEquals("0:01", PlaybackSnapshot.clock(1_999L))
    }

    @Test
    fun sameMediaIdDoesNotLookLikeANewNote() {
        assertTrue(PlaybackSnapshot.sameItem("8", 8L))
        assertFalse(PlaybackSnapshot.sameItem("8", 9L))
        assertFalse(PlaybackSnapshot.sameItem(null, 8L))
    }

    @Test
    fun playCommandShowsPauseBeforeExoReportsIsPlaying() {
        assertTrue(PlaybackSnapshot.sessionMatches(sessionNoteId = 4L, currentMediaId = null, noteId = 4L))
        assertTrue(PlaybackSnapshot.sessionMatches(sessionNoteId = null, currentMediaId = "4", noteId = 4L))
        assertFalse(PlaybackSnapshot.sessionMatches(sessionNoteId = 4L, currentMediaId = "4", noteId = 5L))
        assertFalse(PlaybackSnapshot.endedOnTimeline(timelineMatches = false, playbackEnded = true))

        val started = PlaybackSnapshot.project(
            reportedPositionMs = 40_000L,
            reportedDurationMs = 90_000L,
            playing = true,
            ended = false,
            mediaMatches = PlaybackSnapshot.commandVisible(timelineMatches = false, holdingDuration = true),
            holdDuration = true,
        )
        assertTrue(started.playing)
        assertEquals(0L, started.positionMs)
        assertEquals(0L, started.durationMs)
        assertEquals(PlaybackSnapshot.TICK_MS, PlaybackSnapshot.pollIntervalMs(started.playing))
    }

    @Test
    fun secondPlayPausesAndAPausedToggleKeepsThePosition() {
        assertTrue(
            PlaybackSnapshot.pauseInstead(sessionMatches = true, commandedPlaying = true, ended = false),
        )
        assertFalse(
            PlaybackSnapshot.pauseInstead(sessionMatches = true, commandedPlaying = true, ended = true),
        )
        assertFalse(
            PlaybackSnapshot.pauseInstead(sessionMatches = true, commandedPlaying = false, ended = false),
        )
        assertFalse(
            PlaybackSnapshot.pauseInstead(sessionMatches = false, commandedPlaying = true, ended = false),
        )

        val paused = reading(position = 4_200L, playing = false)
        assertFalse(paused.playing)
        assertEquals(4_200L, paused.positionMs)
        assertFalse(PlaybackSnapshot.replayFromStart(ended = false))

        val resumed = reading(position = 4_200L, playing = true)
        assertTrue(resumed.playing)
        assertEquals(4_200L, resumed.positionMs)
        assertEquals(10_000L, resumed.durationMs)
    }

    @Test
    fun openingKeepsAKnownDurationAtRest() {
        val opened = PlaybackSnapshot.opening(noteId = 4L, title = "Note", durationMs = 30_000L)
        assertEquals(4L, opened.noteId)
        assertEquals("Note", opened.title)
        assertEquals(0L, opened.positionMs)
        assertEquals(30_000L, opened.durationMs)
        assertFalse(opened.playing)
        assertFalse(opened.engaged)
    }

    @Test
    fun openNoteKeepsTheLoadedSessionAndResetsAnyOther() {
        assertTrue(PlaybackSnapshot.keepLoadedSession(openNoteId = 4L, sessionNoteId = 4L, engaged = true))
        assertFalse(PlaybackSnapshot.keepLoadedSession(openNoteId = 5L, sessionNoteId = 4L, engaged = true))
        assertFalse(PlaybackSnapshot.keepLoadedSession(openNoteId = 4L, sessionNoteId = 4L, engaged = false))
        assertTrue(
            PlaybackSnapshot.alreadyDescribed(
                openNoteId = 5L,
                sessionNoteId = 5L,
                engaged = false,
                playing = false,
            ),
        )
        assertFalse(
            PlaybackSnapshot.alreadyDescribed(
                openNoteId = 5L,
                sessionNoteId = 4L,
                engaged = false,
                playing = false,
            ),
        )
    }

    @Test
    fun transportPauseClearsThePlayingCommand() {
        assertFalse(
            PlaybackSnapshot.playingCommand(
                commandedPlaying = true,
                playWhenReady = false,
                replaying = false,
            ),
        )
        assertTrue(
            PlaybackSnapshot.playingCommand(
                commandedPlaying = true,
                playWhenReady = true,
                replaying = false,
            ),
        )
        assertTrue(
            PlaybackSnapshot.playingCommand(
                commandedPlaying = true,
                playWhenReady = false,
                replaying = true,
            ),
        )
        assertTrue(
            PlaybackSnapshot.playingCommand(
                commandedPlaying = true,
                playWhenReady = false,
                replaying = false,
                armingPlay = true,
            ),
        )
        assertTrue(PlaybackSnapshot.echoedTransportPause(pausedAtMs = 1_000L, nowMs = 1_100L))
        assertFalse(PlaybackSnapshot.echoedTransportPause(pausedAtMs = 1_000L, nowMs = 1_400L))
        assertFalse(PlaybackSnapshot.echoedTransportPause(pausedAtMs = 0L, nowMs = 1_100L))
    }

    private fun reading(
        position: Long,
        playing: Boolean,
        ended: Boolean = false,
    ) = PlaybackSnapshot.project(
        reportedPositionMs = position,
        reportedDurationMs = 10_000L,
        playing = playing,
        ended = ended,
        mediaMatches = true,
        holdDuration = false,
    )
}
