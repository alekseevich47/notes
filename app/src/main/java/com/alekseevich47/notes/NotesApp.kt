package com.alekseevich47.notes

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.alekseevich47.notes.data.download.ModelDownloadManager
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.service.AppChannels
import com.alekseevich47.notes.service.ModelWarmupService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NotesApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var downloads: ModelDownloadManager
    @Inject lateinit var applicationScope: CoroutineScope
    @Inject lateinit var notes: NotesRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLog.install(this)
        installCrashHandler()
        AppLog.logProcessStart()
        applicationScope.launch { notes.reindexSearchIfNeeded() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLog.i("NotesApp", "process foreground")
                // onCreate can run in the background (boot, widget). Android 12+
                // throws if a foreground service starts from there. onStart means
                // an activity is up, which is allowed. The service skips a load
                // it already has.
                ModelWarmupService.start(this@NotesApp)
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
