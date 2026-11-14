package com.alekseevich47.notes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                getString(R.string.recording_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NOTES,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    companion object {
        const val CHANNEL_RECORDING = "recording"
        const val CHANNEL_NOTES = "notes"
    }
}
