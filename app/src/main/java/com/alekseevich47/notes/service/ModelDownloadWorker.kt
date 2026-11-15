package com.alekseevich47.notes.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alekseevich47.notes.data.download.DownloadOutcome
import com.alekseevich47.notes.data.download.ModelDownloadManager
import com.alekseevich47.notes.data.download.ModelKind
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloads: ModelDownloadManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val name = inputData.getString(KEY_KIND) ?: return Result.failure()
        val kind = runCatching { ModelKind.valueOf(name) }.getOrNull() ?: return Result.failure()
        return when (downloads.execute(kind)) {
            DownloadOutcome.DONE, DownloadOutcome.PAUSED, DownloadOutcome.STOPPED -> Result.success()
            DownloadOutcome.RETRY -> Result.retry()
            DownloadOutcome.FAILED -> Result.failure()
        }
    }

    companion object {
        const val KEY_KIND = "kind"
    }
}
