package com.alekseevich47.notes

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.alekseevich47.notes.data.download.ModelDownloadManager
import com.alekseevich47.notes.data.download.ModelKind
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.data.secure.NotesPrefs
import com.alekseevich47.notes.data.secure.SecureSettingsStore
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.log.exitReasonName
import com.alekseevich47.notes.log.processMarker
import com.alekseevich47.notes.service.AppChannels
import com.alekseevich47.notes.service.ModelWarmupService
import com.alekseevich47.notes.ui.theme.perfTierFor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class NotesApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var downloads: ModelDownloadManager
    @Inject lateinit var applicationScope: CoroutineScope
    @Inject lateinit var notes: NotesRepository
    @Inject lateinit var settings: SecureSettingsStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLog.install(this)
        installCrashHandler()
        AppLog.logProcessStart()
        applicationScope.launch(Dispatchers.IO) {
            val prefs = NotesPrefs.open(this@NotesApp)
            val session = prefs.getInt(NotesPrefs.LOG_SESSION, 0) + 1
            prefs.edit().putInt(NotesPrefs.LOG_SESSION, session).commit()
            AppLog.i("NotesApp", processMarker(android.os.Process.myPid(), session))
            try {
                AppLog.rewriteHeader(extendedProcessHeader())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.e("NotesApp", "header failed", error)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    logHistoricalExits(prefs)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLog.e("NotesApp", "exit reasons failed", error)
                }
            }
        }
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
        applicationScope.launch(Dispatchers.IO) {
            settings.normalizeRecognizer(
                downloads.isInstalled(ModelKind.T_ONE) && settings.isModelEnabled(ModelKind.T_ONE),
            )
        }
    }

    /**
     * Live memory, disk, models, and permissions. Called on [Dispatchers.IO]
     * so model stats and [ActivityManager] stay off the main thread.
     * Theme and the session counter stay in plain [NotesPrefs].
     */
    private fun extendedProcessHeader(): List<String> {
        val manager = getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(memory)
        val memoryClass = manager?.memoryClass ?: -1
        val lowRam = manager?.isLowRamDevice == true
        val tier = perfTierFor(
            lowRam = lowRam,
            sdkInt = Build.VERSION.SDK_INT,
            processors = Runtime.getRuntime().availableProcessors(),
            memoryClass = manager?.memoryClass ?: Int.MAX_VALUE,
        )
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val notificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val power = getSystemService(PowerManager::class.java)
        val batteryOptIgnored = runCatching {
            power?.isIgnoringBatteryOptimizations(packageName) == true
        }.getOrDefault(false)
        val theme = NotesPrefs.open(this).getString(NotesPrefs.THEME_MODE, null)?.takeIf { it.isNotBlank() }
            ?: "unset"
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return AppLog.processStartLines(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            release = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            incremental = Build.VERSION.INCREMENTAL,
            display = Build.DISPLAY,
            abis = Build.SUPPORTED_ABIS.joinToString(","),
            locale = Locale.getDefault().toLanguageTag(),
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
            debuggable = debuggable,
            pid = android.os.Process.myPid(),
            memoryClass = memoryClass,
            lowRam = lowRam,
            totalMemMb = memory.totalMem / (1024L * 1024L),
            availMemMb = memory.availMem / (1024L * 1024L),
            freeDiskMb = filesDir.usableSpace / (1024L * 1024L),
            perfTier = tier.name,
            toneInstalled = downloads.isInstalled(ModelKind.T_ONE),
            toneEnabled = settings.isModelEnabled(ModelKind.T_ONE),
            gigaInstalled = downloads.isInstalled(ModelKind.GIGA_AM),
            gigaEnabled = settings.isModelEnabled(ModelKind.GIGA_AM),
            recognizer = settings.recognizer.value,
            warmupOnBoot = settings.warmupOnBoot.value,
            micGranted = micGranted,
            notificationsGranted = notificationsGranted,
            batteryOptIgnored = batteryOptIgnored,
            timezone = java.util.TimeZone.getDefault().id.replace('/', '_'),
            theme = theme,
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun logHistoricalExits(prefs: SharedPreferences) {
        val manager = getSystemService(ActivityManager::class.java) ?: return
        val last = prefs.getLong(NotesPrefs.LAST_EXIT_LOGGED_TS, 0L)
        val fresh = manager.getHistoricalProcessExitReasons(packageName, 0, 5)
            .filter { it.timestamp > last }
            .sortedBy { it.timestamp }
        var newest = last
        for (info in fresh) {
            val desc = AppLog.sanitize(info.description.orEmpty()).ifBlank { "-" }
            AppLog.w(
                "ProcessExit",
                "reason=${exitReasonName(info.reason)} status=${info.status} importance=${info.importance} pssKb=${info.pss} rssKb=${info.rss} at=${info.timestamp} desc=$desc",
            )
            if (info.timestamp > newest) newest = info.timestamp
        }
        if (newest > last) {
            prefs.edit().putLong(NotesPrefs.LAST_EXIT_LOGGED_TS, newest).commit()
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { AppLog.e("NotesApp", "uncaught thread=${thread.name}", error) }
            if (previous != null) previous.uncaughtException(thread, error) else throw error
        }
    }
}
