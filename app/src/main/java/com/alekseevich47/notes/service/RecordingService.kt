package com.alekseevich47.notes.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.alekseevich47.notes.MainActivity
import com.alekseevich47.notes.R
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.stt.RecordingSession
import com.alekseevich47.notes.ui.widgets.WidgetMotion
import com.alekseevich47.notes.ui.widgets.WidgetUpdater
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
class RecordingService : Service() {
    @Inject lateinit var session: RecordingSession

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private var userStop = false
    private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                userStop = true
                AppLog.i("RecordingService", "stop reason=ACTION_STOP")
                session.requestStop()
                return START_STICKY
            }
            ACTION_TOGGLE -> {
                if (session.isBusy) {
                    session.requestStop()
                    return START_STICKY
                }
            }
        }
        if (!hasMicPermission()) {
            AppLog.i("RecordingService", "mic permission denied")
            AppLog.i("RecordingService", "stop reason=missing permission")
            openAppForPermission()
            stopSelf()
            return START_NOT_STICKY
        }
        AppLog.i("RecordingService", "mic permission granted")
        startInForeground(recordingNotification("Идёт запись…"))
        if (job?.isActive != true) {
            acquireWake()
            job = scope.launch {
                val frames = launch {
                    while (isActive) {
                        val current = session.phase.value
                        when (current) {
                            MicPhase.RECORDING -> {
                                WidgetUpdater.pulse(this@RecordingService, session.level.value)
                                delay(64)
                            }
                            MicPhase.PROCESSING -> {
                                WidgetUpdater.pulse(this@RecordingService, 0f)
                                delay(WidgetMotion.DOT_FRAME_MS.toLong())
                            }
                            MicPhase.DONE -> {
                                WidgetUpdater.pulse(this@RecordingService, 0f)
                                delay(64)
                            }
                            MicPhase.IDLE -> delay(40)
                        }
                    }
                }
                val noteId = runCatching { session.recordAndSave() }.getOrNull()
                frames.cancel()
                releaseWake()
                if (noteId != null) {
                    notifyReady(noteId)
                }
                finished = true
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (job?.isActive == true && !userStop && !finished) {
            AppLog.w("RecordingService", "stop reason=process killed")
        }
        releaseWake()
        scope.cancel()
        super.onDestroy()
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun openAppForPermission() {
        val open = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_NEED_MIC, true)
        }
        startActivity(open)
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(ONGOING_ID, notification)
        }
    }

    private fun recordingNotification(text: String): Notification {
        ensureChannels()
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingFlags(),
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            pendingFlags(),
        )
        return NotificationCompat.Builder(this, AppChannels.RECORDING)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("Запись заметки")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Стоп", stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun notifyReady(noteId: Long) {
        ensureChannels()
        val open = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
            },
            pendingFlags(),
        )
        val notification = NotificationCompat.Builder(this, AppChannels.READY)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("Заметка готова")
            .setContentText(session.liveText.value.ifBlank { "Аудио сохранено" }.take(80))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        getSystemService(NotificationManager::class.java).notify(READY_ID, notification)
    }

    private fun ensureChannels() {
        AppChannels.ensure(this)
    }

    private fun acquireWake() {
        val manager = getSystemService(PowerManager::class.java)
        wake = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "notes:record").apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWake() {
        runCatching { if (wake?.isHeld == true) wake?.release() }
        wake = null
    }

    private fun pendingFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    }

    companion object {
        const val ACTION_START = "com.alekseevich47.notes.record.START"
        const val ACTION_STOP = "com.alekseevich47.notes.record.STOP"
        const val ACTION_TOGGLE = "com.alekseevich47.notes.record.TOGGLE"
        private const val ONGOING_ID = 41
        private const val READY_ID = 42
    }
}
