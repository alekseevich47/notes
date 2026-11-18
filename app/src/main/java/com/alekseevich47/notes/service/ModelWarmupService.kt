package com.alekseevich47.notes.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.alekseevich47.notes.MainActivity
import com.alekseevich47.notes.R
import com.alekseevich47.notes.data.download.ModelDownloadManager
import com.alekseevich47.notes.data.download.ModelKind
import com.alekseevich47.notes.data.secure.NotesPrefs
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.stt.SherpaEngines
import com.alekseevich47.notes.stt.SherpaLoadLock
import com.alekseevich47.notes.stt.WarmEngines
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Loads T-one and GigaAM once and keeps the process alive so the next recording
 * can borrow those recognizers. The service type is specialUse: this is not a
 * microphone capture, and dataSync would be stopped after a few hours on
 * Android 15. The ongoing notification is low priority.
 */
@AndroidEntryPoint
class ModelWarmupService : Service() {
    @Inject lateinit var models: ModelDownloadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var epoch: Int = WarmLifetime.NONE
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startInForeground()) return START_NOT_STICKY
        val enabled = NotesPrefs.open(this).getBoolean(NotesPrefs.WARMUP_ON_BOOT, false)
        if (!enabled) {
            AppLog.i(TAG, "stop pref off")
            stopWarm()
            return START_NOT_STICKY
        }
        if (job?.isActive != true) {
            val ticket = lifetime.open()
            epoch = ticket
            job = scope.launch {
                runCatching { warm(ticket) }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        AppLog.e(TAG, "warmup failed", error)
                    }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        lifetime.close(epoch) { WarmEngines.retireAll() }
        scope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int) {
        AppLog.w(TAG, "warmup timeout")
        stopWarm()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        onTimeout(startId)
    }

    private suspend fun warm(ticket: Int) {
        var tone: OnlineRecognizer? = null
        var giga: OfflineRecognizer? = null
        try {
            SherpaLoadLock.mutex.withLock {
                tone = if (models.isInstalled(ModelKind.T_ONE)) {
                    SherpaEngines.onlineTone(models.directory(ModelKind.T_ONE))
                } else {
                    null
                }
                giga = if (models.isInstalled(ModelKind.GIGA_AM)) {
                    SherpaEngines.offlineGiga(models.directory(ModelKind.GIGA_AM))
                } else {
                    null
                }
            }
            tone?.let { created ->
                tone = null
                lifetime.publish(ticket, created, { WarmEngines.offerTone(it) }, OnlineRecognizer::release)
            }
            giga?.let { created ->
                giga = null
                lifetime.publish(ticket, created, { WarmEngines.offerGiga(it) }, OfflineRecognizer::release)
            }
            AppLog.i(TAG, "ready tone=${WarmEngines.toneReady()} giga=${WarmEngines.gigaReady()}")
        } finally {
            runCatching { tone?.release() }
            runCatching { giga?.release() }
        }
    }

    private fun stopWarm() {
        lifetime.close(epoch) { WarmEngines.retireAll() }
        job?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground(): Boolean {
        val notification = buildNotification()
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (error: Throwable) {
            AppLog.e(TAG, "foreground failed", error)
            stopSelf()
            false
        }
    }

    private fun buildNotification(): Notification {
        AppChannels.ensure(this)
        val open = PendingIntent.getActivity(
            this,
            8,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AppChannels.WARMUP)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(WarmupNotice.TEXT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val TAG = "Warmup"
        private const val NOTIFICATION_ID = 44
        private val lifetime = WarmLifetime()

        fun start(context: Context) {
            val intent = Intent(context, ModelWarmupService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ModelWarmupService::class.java))
        }
    }
}
