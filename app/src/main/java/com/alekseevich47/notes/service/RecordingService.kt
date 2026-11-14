package com.alekseevich47.notes.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alekseevich47.notes.MainActivity
import com.alekseevich47.notes.NotesApp
import com.alekseevich47.notes.R
import com.alekseevich47.notes.stt.RecordingController
import com.alekseevich47.notes.ui.widgets.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {
    @Inject lateinit var controller: RecordingController
    @Inject lateinit var widgetUpdater: WidgetUpdater

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_TOGGLE -> {
                startForeground(NOTIF_ID, buildRecordingNotification())
                if (!controller.state.value.active && !controller.state.value.processing) {
                    controller.start()
                } else if (controller.state.value.active) {
                    controller.stop()
                }
                collectJob?.cancel()
                collectJob = scope.launch {
                    controller.state.collectLatest { state ->
                        widgetUpdater.publish(state)
                        if (state.processing || state.active) {
                            NotificationManagerCompat.from(this@RecordingService)
                                .notify(NOTIF_ID, buildRecordingNotification(state.partialText))
                        }
                        if (state.done && state.noteId != null) {
                            notifyReady(state.noteId!!, state.finalText)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            }
            ACTION_STOP -> {
                if (controller.state.value.active) controller.stop()
            }
        }
        return START_STICKY
    }

    private fun buildRecordingNotification(text: String = ""): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotesApp.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_mic_outline)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(text.ifBlank { getString(R.string.recording_notification_text) })
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun notifyReady(noteId: Long, preview: String) {
        val open = PendingIntent.getActivity(
            this,
            noteId.toInt(),
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_NOTE
                putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NotesApp.CHANNEL_NOTES)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle(getString(R.string.note_ready_title))
            .setContentText(preview.take(80).ifBlank { "Открыть заметку" })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(READY_ID_BASE + noteId.toInt(), notification)
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.alekseevich47.notes.RECORD_START"
        const val ACTION_STOP = "com.alekseevich47.notes.RECORD_STOP"
        const val ACTION_TOGGLE = "com.alekseevich47.notes.RECORD_TOGGLE"
        private const val NOTIF_ID = 42
        private const val READY_ID_BASE = 1000
    }
}
