package com.alekseevich47.notes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.alekseevich47.notes.MainActivity
import com.alekseevich47.notes.R
import com.alekseevich47.notes.player.NotePlayer
import com.alekseevich47.notes.player.PlayerUi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : Service() {
    @Inject lateinit var notePlayer: NotePlayer

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private val refresh = { show(notePlayer.state.value) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        session = MediaSession.Builder(this, notePlayer.player())
            .setId("notes-playback")
            .setSessionActivity(openPending(null))
            .build()
        notePlayer.addListener(refresh)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
                val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                if (noteId > 0 && path.isNotBlank()) notePlayer.play(noteId, path, title)
            }
            ACTION_TOGGLE -> notePlayer.toggle()
            ACTION_REWIND -> notePlayer.seekBy(-10_000)
            ACTION_FORWARD -> notePlayer.seekBy(10_000)
            ACTION_STOP -> {
                notePlayer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        show(notePlayer.state.value)
        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        notePlayer.removeListener(refresh)
        session?.release()
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private fun show(state: PlayerUi) {
        if (state.noteId == null) return
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        if (state.playing && ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive) {
                    delay(800)
                    val current = notePlayer.state.value
                    if (current.playing) {
                        val manager = getSystemService(NotificationManager::class.java)
                        manager.notify(NOTIF_ID, buildNotification(current))
                    }
                }
            }
        }
        if (!state.playing) ticker?.cancel()
    }

    private fun buildNotification(state: PlayerUi): Notification {
        val mediaSession = session
        val open = openPending(state.noteId)
        val style = if (mediaSession != null) {
            MediaStyleNotificationHelper.MediaStyle(mediaSession).setShowActionsInCompactView(0, 1, 2)
        } else {
            null
        }
        val playIcon = if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playLabel = if (state.playing) "Пауза" else "Слушать"
        val duration = state.durationMs.coerceAtLeast(1L)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(state.title.ifBlank { "Заметка" })
            .setContentText(format(state.positionMs) + " / " + format(state.durationMs))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(state.playing)
            .setProgress(duration.toInt().coerceAtLeast(1), state.positionMs.coerceAtMost(duration).toInt(), state.durationMs <= 0)
            .addAction(android.R.drawable.ic_media_rew, "−10 с", action(ACTION_REWIND, 11))
            .addAction(playIcon, playLabel, action(ACTION_TOGGLE, 12))
            .addAction(android.R.drawable.ic_media_ff, "+10 с", action(ACTION_FORWARD, 13))
            .setDeleteIntent(action(ACTION_STOP, 14))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (style != null) builder.setStyle(style)
        return builder.build()
    }

    private fun openPending(noteId: Long?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (noteId != null) putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
        }
        return PendingIntent.getActivity(this, 15, intent, pendingFlags())
    }

    private fun action(action: String, code: Int): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(this, code, intent, pendingFlags())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    private fun pendingFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private fun format(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }

    companion object {
        const val ACTION_PLAY = "com.alekseevich47.notes.play.PLAY"
        const val ACTION_TOGGLE = "com.alekseevich47.notes.play.TOGGLE"
        const val ACTION_REWIND = "com.alekseevich47.notes.play.REWIND"
        const val ACTION_FORWARD = "com.alekseevich47.notes.play.FORWARD"
        const val ACTION_STOP = "com.alekseevich47.notes.play.STOP"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL = "notes_playback"
        private const val NOTIF_ID = 43
    }
}
