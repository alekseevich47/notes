package com.alekseevich47.notes.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.alekseevich47.notes.R

/**
 * Stable channel ids. Creating them at process start makes the rationale
 * visible in system settings before the first recording or playback.
 */
object AppChannels {
    const val RECORDING = "notes_recording"
    const val READY = "notes_ready"
    const val PLAYBACK = "notes_playback"
    const val WARMUP = "notes_warmup"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                RECORDING,
                context.getString(R.string.channel_recording),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_recording_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                READY,
                context.getString(R.string.channel_ready),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_ready_desc)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PLAYBACK,
                context.getString(R.string.channel_playback),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_playback_desc)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                WARMUP,
                context.getString(R.string.channel_warmup),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_warmup_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }
}
