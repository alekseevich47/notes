package com.alekseevich47.notes.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerUi(
    val noteId: Long? = null,
    val title: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

@Singleton
class NotePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())
    private var exo: ExoPlayer? = null
    private val _state = MutableStateFlow(PlayerUi())
    val state: StateFlow<PlayerUi> = _state.asStateFlow()
    private val listeners = mutableSetOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun player(): ExoPlayer = exo ?: buildPlayer().also { exo = it }

    fun play(noteId: Long, path: String, title: String) = onMain {
        val file = File(path)
        if (!file.isFile) {
            _state.value = PlayerUi()
            notifyListeners()
            return@onMain
        }
        val player = player()
        val same = player.currentMediaItem?.mediaId == noteId.toString()
        if (!same) {
            player.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(noteId.toString())
                    .setUri(Uri.fromFile(file))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                    .build(),
            )
            player.prepare()
        }
        player.play()
        publish(noteId, title)
    }

    fun toggle() = onMain {
        val player = exo ?: return@onMain
        if (player.isPlaying) player.pause() else player.play()
        publish()
    }

    fun seekBy(deltaMs: Long) = onMain {
        val player = exo ?: return@onMain
        val duration = player.duration.coerceAtLeast(0L)
        val target = (player.currentPosition + deltaMs).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        player.seekTo(target)
        publish()
    }

    fun seekFraction(fraction: Float) = onMain {
        val player = exo ?: return@onMain
        val duration = player.duration
        if (duration > 0) player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        publish()
    }

    fun stop() = onMain {
        exo?.stop()
        exo?.clearMediaItems()
        _state.value = PlayerUi()
        notifyListeners()
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
                    override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
                    override fun onPlaybackStateChanged(playbackState: Int) = publish()
                })
            }
    }

    private fun publish(noteId: Long? = _state.value.noteId, title: String = _state.value.title) {
        val player = exo
        _state.value = PlayerUi(
            noteId = noteId,
            title = title,
            playing = player?.isPlaying == true,
            positionMs = player?.currentPosition ?: 0L,
            durationMs = player?.duration?.takeIf { it > 0 } ?: 0L,
        )
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it.invoke() }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
