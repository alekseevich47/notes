package com.alekseevich47.notes

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.alekseevich47.notes.data.download.ModelDownloadManager
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.service.AppChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NotesApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var downloads: ModelDownloadManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLog.install(this)
        installCrashHandler()
        AppLog.logProcessStart()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLog.i("NotesApp", "process foreground")
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLog.i("NotesApp", "process background")
            }
        })
        AppChannels.ensure(this)
        downloads.recoverInterrupted()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { AppLog.e("NotesApp", "uncaught thread=${thread.name}", error) }
            if (previous != null) previous.uncaughtException(thread, error) else throw error
        }
    }
}
