package com.alekseevich47.notes.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.alekseevich47.notes.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())
    private var exo: ExoPlayer? = null
    private val _state = MutableStateFlow(PlayerUi())
    val state: StateFlow<PlayerUi> = _state.asStateFlow()
    private val listeners = mutableSetOf<() -> Unit>()
    private var active = false
    private var holdDuration = false
    private var staleDurationMs = 0L
    private var sawGap = false
    private var applyingMedia = false
    private var tickerPosted = false
    /** Play/pause written on the same call as play() and pause(), before ExoPlayer reports isPlaying. */
    private var commandedPlaying = false
    /** Replay seek is in flight, so a leftover ENDED must not drop the new play command. */
    private var replaying = false
    /** play() is preparing a new item, so a stale playWhenReady must not clear the command. */
    private var armingPlay = false
    /** When a transport pause (notification / session) cleared playWhenReady. */
    private var transportPausedAt = 0L
    private var audioPath: String? = null

    private val tick = Runnable {
        tickerPosted = false
        if (!active || !commandedPlaying) return@Runnable
        publish()
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun player(): ExoPlayer = exo ?: buildPlayer().also { exo = it }

    /**
     * Editor opened [noteId]. If another note is loaded, stop it now so the bar
     * cannot keep describing the previous note while this one is opening.
     */
    fun onEditorNote(noteId: Long) = onMain {
        val shown = _state.value.noteId ?: return@onMain
        if (shown == noteId) return@onMain
        releaseSession()
        audioPath = null
        _state.value = PlayerUi()
        notifyListeners()
    }

    /**
     * The note on screen. Describes that note (known duration, position 0, not
     * playing) unless this note is already the loaded session.
     */
    fun followOpenNote(noteId: Long, path: String?, title: String) = onMain {
        val file = path?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
        if (file == null) {
            releaseSession()
            audioPath = null
            if (_state.value != PlayerUi()) {
                _state.value = PlayerUi()
                notifyListeners()
            }
            return@onMain
        }
        if (PlaybackSnapshot.keepLoadedSession(noteId, _state.value.noteId, active && _state.value.engaged)) {
            audioPath = file.absolutePath
            return@onMain
        }
        if (PlaybackSnapshot.alreadyDescribed(noteId, _state.value.noteId, _state.value.engaged, _state.value.playing)) {
            audioPath = file.absolutePath
            return@onMain
        }
        releaseSession()
        audioPath = file.absolutePath
        _state.value = PlaybackSnapshot.opening(noteId, title, WavAudio.durationMs(file))
        notifyListeners()
    }

    fun share(context: Context, path: String? = null, onFinished: ((Boolean) -> Unit)? = null) {
        val wav = path?.takeIf { it.isNotBlank() } ?: audioPath
        val noteId = _state.value.noteId
        if (wav.isNullOrBlank()) {
            if (onFinished != null) onMain { onFinished(false) }
            return
        }
        NoteAudioShare.share(context, wav, noteId, onFinished)
    }

    fun prepareShare(context: Context, onReady: () -> Unit = {}) {
        val path = audioPath ?: return
        NoteAudioShare.prepare(context, path, _state.value.noteId, onReady)
    }

    fun play(noteId: Long, path: String, title: String) = onMain {
        val file = File(path)
        if (!file.isFile) {
            AppLog.w("NotePlayer", "start failed id=$noteId")
            active = false
            commandedPlaying = false
            replaying = false
            armingPlay = false
            holdDuration = false
            sawGap = false
            audioPath = null
            cancelTicker()
            exo?.pause()
            _state.value = PlayerUi()
            notifyListeners()
            return@onMain
        }
        audioPath = file.absolutePath
        // A preview writes noteId before any media is loaded. That is not a session.
        val sessionNoteId = if (active && _state.value.engaged) _state.value.noteId else null
        active = true
        val player = player()
        val timelineSame = PlaybackSnapshot.sameItem(player.currentMediaItem?.mediaId, noteId)
        val same = PlaybackSnapshot.sessionMatches(sessionNoteId, player.currentMediaItem?.mediaId, noteId)
        val ended = PlaybackSnapshot.endedOnTimeline(timelineSame, player.playbackState == Player.STATE_ENDED)
        if (PlaybackSnapshot.pauseInstead(same, commandedPlaying, ended)) {
            pauseAt(player, noteId, title)
            return@onMain
        }
        armingPlay = true
        try {
            var replay = false
            if (!same) {
                commandedPlaying = true
                replaying = false
                staleDurationMs = player.duration.takeIf { it > 0L } ?: 0L
                sawGap = false
                holdDuration = true
                cancelTicker()
                _state.value = PlaybackSnapshot.opening(noteId, title).copy(playing = true, engaged = true)
                notifyListeners()
                applyingMedia = true
                try {
                    player.setMediaItem(
                        MediaItem.Builder()
                            .setMediaId(noteId.toString())
                            .setUri(Uri.fromFile(file))
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                            .build(),
                    )
                    player.prepare()
                } finally {
                    applyingMedia = false
                }
                // Sync listener flushes inside prepare are ignored. If that call already
                // left the old timeline, the next publish can show the new duration.
                noteGap(player)
                settle(timelineOrMedia = true)
            } else if (PlaybackSnapshot.replayFromStart(ended)) {
                replay = true
                replaying = true
                commandedPlaying = true
                player.seekTo(0)
            }
            commandedPlaying = true
            transportPausedAt = 0L
            player.play()
            AppLog.i("NotePlayer", "start id=$noteId")
            publish(noteId, title, seekOverrideMs = if (replay) 0L else null)
        } finally {
            armingPlay = false
        }
    }

    fun toggle() = onMain {
        val player = exo ?: return@onMain
        if (!active) return@onMain
        val noteId = _state.value.noteId
        val timelineSame = noteId != null && PlaybackSnapshot.sameItem(player.currentMediaItem?.mediaId, noteId)
        val ended = PlaybackSnapshot.endedOnTimeline(timelineSame, player.playbackState == Player.STATE_ENDED)
        // ACTION_TOGGLE from the notification. The button the user saw is commandedPlaying.
        if (PlaybackSnapshot.pauseInstead(sessionMatches = true, commandedPlaying = commandedPlaying, ended = ended)) {
            pauseAt(player)
            return@onMain
        }
        if (!ended && PlaybackSnapshot.echoedTransportPause(transportPausedAt, SystemClock.uptimeMillis())) {
            transportPausedAt = 0L
            pauseAt(player)
            return@onMain
        }
        val replay = PlaybackSnapshot.replayFromStart(ended)
        if (replay) {
            replaying = true
            commandedPlaying = true
            player.seekTo(0)
        }
        commandedPlaying = true
        transportPausedAt = 0L
        player.play()
        AppLog.i("NotePlayer", "start id=${_state.value.noteId ?: -1}")
        publish(seekOverrideMs = if (replay) 0L else null)
    }

    private fun pauseAt(player: ExoPlayer, noteId: Long? = _state.value.noteId, title: String = _state.value.title) {
        commandedPlaying = false
        replaying = false
        player.pause()
        AppLog.i("NotePlayer", "pause id=${noteId ?: -1}")
        publish(noteId, title)
    }

    fun seekBy(deltaMs: Long) = onMain {
        val player = exo ?: return@onMain
        if (!active) return@onMain
        val duration = if (holdDuration) 0L else PlaybackSnapshot.knownDuration(player.duration)
        val target = PlaybackSnapshot.seekTarget(player.currentPosition, deltaMs, duration)
        player.seekTo(target)
        AppLog.i("NotePlayer", "seek id=${_state.value.noteId ?: -1}")
        publish(seekOverrideMs = target)
    }

    fun seekFraction(fraction: Float) = onMain {
        val player = exo ?: return@onMain
        if (!active) return@onMain
        val duration = if (holdDuration) 0L else PlaybackSnapshot.knownDuration(player.duration)
        val target = PlaybackSnapshot.fractionTarget(duration, fraction)
        if (target == null) {
            publish()
            return@onMain
        }
        player.seekTo(target)
        AppLog.i("NotePlayer", "seek id=${_state.value.noteId ?: -1}")
        publish(seekOverrideMs = target)
    }

    fun stop() = onMain {
        val id = _state.value.noteId
        val playing = active
        releaseSession()
        audioPath = null
        _state.value = PlayerUi()
        notifyListeners()
        if (playing || id != null) AppLog.i("NotePlayer", "stop id=${id ?: -1}")
    }

    fun release() = onMain {
        val id = _state.value.noteId
        val playing = active
        releaseSession()
        applyingMedia = false
        audioPath = null
        val player = exo
        exo = null
        player?.release()
        _state.value = PlayerUi()
        notifyListeners()
        if (playing) AppLog.i("NotePlayer", "stop id=${id ?: -1}")
    }

    private fun releaseSession() {
        active = false
        commandedPlaying = false
        replaying = false
        armingPlay = false
        holdDuration = false
        sawGap = false
        staleDurationMs = 0L
        transportPausedAt = 0L
        cancelTicker()
        val player = exo ?: return
        player.pause()
        player.stop()
        player.clearMediaItems()
    }

    private fun buildPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (applyingMedia || !active) return
                        settle(timelineOrMedia = false)
                        publish()
                    }

                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        if (applyingMedia || !active) return
                        settle(timelineOrMedia = true)
                        publish()
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (applyingMedia || !active) return
                        settle(timelineOrMedia = true)
                        publish()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (applyingMedia || !active) return
                        publish()
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                            AppLog.w("NotePlayer", "audio focus lost id=${_state.value.noteId ?: -1}")
                        }
                        // Notification and headset pause land here, often without ACTION_TOGGLE.
                        if (applyingMedia || !active) return
                        publish()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        AppLog.e("NotePlayer", "player error id=${_state.value.noteId ?: -1}", error)
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) {
                        if (applyingMedia || !active) return
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                            publish(seekOverrideMs = newPosition.positionMs)
                        } else {
                            publish()
                        }
                    }

                    override fun onEvents(player: Player, events: Player.Events) {
                        if (applyingMedia || !active) return
                        val timeline = events.containsAny(
                            Player.EVENT_TIMELINE_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                        )
                        val interesting = timeline || events.containsAny(
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_IS_PLAYING_CHANGED,
                            Player.EVENT_PLAY_WHEN_READY_CHANGED,
                            Player.EVENT_POSITION_DISCONTINUITY,
                        )
                        if (!interesting) return
                        // Runs after the specific callbacks, so a ready duration is visible here too.
                        settle(timelineOrMedia = timeline)
                        publish()
                    }
                })
            }
    }

    private fun noteGap(player: Player) {
        val ready = player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED
        if (!ready || player.duration <= 0L) sawGap = true
    }

    private fun settle(timelineOrMedia: Boolean) {
        if (applyingMedia || !active) return
        val player = exo ?: return
        noteGap(player)
        val ready = player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED
        holdDuration = PlaybackSnapshot.stillHolding(
            holding = holdDuration,
            reportedDurationMs = player.duration,
            staleDurationMs = staleDurationMs,
            readyOrEnded = ready,
            timelineOrMedia = timelineOrMedia,
            sawGap = sawGap,
        )
    }

    private fun publish(
        noteId: Long? = _state.value.noteId,
        title: String = _state.value.title,
        seekOverrideMs: Long? = null,
    ) {
        if (!active) return
        val player = exo ?: return
        val id = noteId
        val wasReplaying = replaying
        if (replaying && player.playbackState != Player.STATE_ENDED) replaying = false
        val wasCommanded = commandedPlaying
        val nextCommand = PlaybackSnapshot.playingCommand(
            commandedPlaying = wasCommanded,
            playWhenReady = player.playWhenReady,
            replaying = wasReplaying,
            armingPlay = armingPlay,
        )
        if (wasCommanded && !nextCommand) transportPausedAt = SystemClock.uptimeMillis()
        commandedPlaying = nextCommand
        val timelineMatches = id != null && PlaybackSnapshot.sameItem(player.currentMediaItem?.mediaId, id)
        val ended = PlaybackSnapshot.endedOnTimeline(
            timelineMatches,
            player.playbackState == Player.STATE_ENDED,
        ) && !replaying
        if (ended && seekOverrideMs == null) commandedPlaying = false
        val reading = PlaybackSnapshot.project(
            reportedPositionMs = player.currentPosition,
            reportedDurationMs = player.duration,
            playing = commandedPlaying,
            ended = ended,
            mediaMatches = PlaybackSnapshot.commandVisible(timelineMatches, holdDuration),
            holdDuration = holdDuration,
            seekOverrideMs = seekOverrideMs,
        )
        val next = PlayerUi(
            noteId = id,
            title = title,
            playing = reading.playing,
            positionMs = reading.positionMs,
            durationMs = reading.durationMs,
            engaged = true,
        )
        val previous = _state.value
        _state.value = next
        if (next != previous) notifyListeners()
        if (PlaybackSnapshot.pollIntervalMs(reading.playing) == null) cancelTicker() else scheduleTicker()
    }

    private fun scheduleTicker() {
        if (tickerPosted) return
        tickerPosted = true
        main.postDelayed(tick, PlaybackSnapshot.TICK_MS)
    }

    private fun cancelTicker() {
        tickerPosted = false
        main.removeCallbacks(tick)
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it.invoke() }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
