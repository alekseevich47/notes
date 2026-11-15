package com.alekseevich47.notes.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.service.ModelDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadStatus { NOT_INSTALLED, DOWNLOADING, PAUSED, FAILED, INSTALLED }

data class ModelCardState(
    val kind: ModelKind,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val error: String? = null,
)

enum class DownloadOutcome { DONE, PAUSED, STOPPED, RETRY, FAILED }

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val gate = Mutex()
    private val controls = java.util.concurrent.ConcurrentHashMap<ModelKind, Control>()
    private val root: File get() = File(context.filesDir, "models").also { it.mkdirs() }

    private val _cards = MutableStateFlow(ModelKind.entries.map { snapshot(it) })
    val cards: StateFlow<List<ModelCardState>> = _cards.asStateFlow()

    private enum class Control { RUN, PAUSE, STOP }

    fun directory(kind: ModelKind): File = File(root, kind.folder).also { it.mkdirs() }

    fun isInstalled(kind: ModelKind): Boolean {
        val dir = directory(kind)
        return ModelCatalog.files(kind).all { spec ->
            val file = File(dir, spec.name)
            file.isFile && file.length() > 0L
        }
    }

    fun refresh() {
        _cards.value = ModelKind.entries.map { snapshot(it) }
    }

    fun recoverInterrupted() {
        AppLog.i(TAG, "recover begin")
        ModelKind.entries.forEach { kind ->
            if (isInstalled(kind)) {
                writeStatus(kind, DownloadStatus.INSTALLED, null)
                return@forEach
            }
            val status = readStatus(kind)
            val partial = hasPartial(kind)
            if (status == DownloadStatus.DOWNLOADING || (status == DownloadStatus.FAILED && partial)) {
                controls[kind] = Control.RUN
                AppLog.i(TAG, "resume $kind recover")
                enqueue(kind, ExistingWorkPolicy.KEEP)
            }
        }
        refresh()
        AppLog.i(TAG, "recover end")
    }

    fun start(kind: ModelKind) {
        if (isInstalled(kind)) {
            publish(kind, DownloadStatus.INSTALLED, 1f, null)
            AppLog.i(TAG, "start $kind skipped installed")
            return
        }
        val resuming = readStatus(kind) == DownloadStatus.PAUSED || hasPartial(kind)
        controls[kind] = Control.RUN
        writeStatus(kind, DownloadStatus.DOWNLOADING, null)
        publish(kind, DownloadStatus.DOWNLOADING, currentProgress(kind), null)
        AppLog.i(TAG, if (resuming) "resume $kind" else "start $kind")
        enqueue(kind, ExistingWorkPolicy.REPLACE)
    }

    fun pause(kind: ModelKind) {
        controls[kind] = Control.PAUSE
        writeStatus(kind, DownloadStatus.PAUSED, null)
        publish(kind, DownloadStatus.PAUSED, currentProgress(kind), null)
        AppLog.i(TAG, "pause $kind")
        WorkManager.getInstance(context).cancelUniqueWork(workName(kind))
    }

    fun stop(kind: ModelKind) {
        controls[kind] = Control.STOP
        deleteIncomplete(kind)
        writeStatus(kind, DownloadStatus.NOT_INSTALLED, null)
        publish(kind, DownloadStatus.NOT_INSTALLED, 0f, null)
        AppLog.i(TAG, "stop $kind")
        WorkManager.getInstance(context).cancelUniqueWork(workName(kind))
    }

    fun deleteInstalled(kind: ModelKind) {
        controls[kind] = Control.STOP
        directory(kind).deleteRecursively()
        writeStatus(kind, DownloadStatus.NOT_INSTALLED, null)
        publish(kind, DownloadStatus.NOT_INSTALLED, 0f, null)
        AppLog.i(TAG, "delete $kind")
        WorkManager.getInstance(context).cancelUniqueWork(workName(kind))
    }

    suspend fun execute(kind: ModelKind): DownloadOutcome = gate.withLock {
        if (controls[kind] == Control.PAUSE) {
            AppLog.i(TAG, "paused $kind")
            return DownloadOutcome.PAUSED
        }
        if (controls[kind] == Control.STOP) {
            AppLog.i(TAG, "stopped $kind")
            return DownloadOutcome.STOPPED
        }
        if (isInstalled(kind)) {
            publish(kind, DownloadStatus.INSTALLED, 1f, null)
            writeStatus(kind, DownloadStatus.INSTALLED, null)
            AppLog.i(TAG, "done $kind")
            return DownloadOutcome.DONE
        }
        controls.putIfAbsent(kind, Control.RUN)
        writeStatus(kind, DownloadStatus.DOWNLOADING, null)
        runDownload(kind)
    }

    private suspend fun runDownload(kind: ModelKind): DownloadOutcome = withContext(Dispatchers.IO) {
        val specs = ModelCatalog.files(kind)
        var attempt = 0
        while (true) {
            try {
                for (spec in specs) {
                    when (val halted = haltIfNeeded(kind)) {
                        null -> downloadFile(kind, spec, specs)
                        else -> return@withContext halted
                    }
                    haltIfNeeded(kind)?.let { return@withContext it }
                }
                if (!isInstalled(kind)) error("Файлы модели не собрались")
                publish(kind, DownloadStatus.INSTALLED, 1f, null)
                writeStatus(kind, DownloadStatus.INSTALLED, null)
                AppLog.i(TAG, "done $kind")
                return@withContext DownloadOutcome.DONE
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (controls[kind] == Control.PAUSE) {
                    AppLog.i(TAG, "paused $kind")
                    return@withContext DownloadOutcome.PAUSED
                }
                if (controls[kind] == Control.STOP) {
                    AppLog.i(TAG, "stopped $kind")
                    return@withContext DownloadOutcome.STOPPED
                }
                attempt++
                if (attempt >= 5) {
                    val message = error.message ?: "Сеть прервалась"
                    publish(kind, DownloadStatus.FAILED, currentProgress(kind), message)
                    writeStatus(kind, DownloadStatus.FAILED, message)
                    AppLog.e(TAG, "fail $kind", error)
                    return@withContext if (error is IOException) DownloadOutcome.RETRY else DownloadOutcome.FAILED
                }
                AppLog.w(TAG, "retry $kind attempt=$attempt ${error.javaClass.simpleName}")
                kotlinx.coroutines.delay(700L * attempt)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        DownloadOutcome.FAILED
    }

    private fun haltIfNeeded(kind: ModelKind): DownloadOutcome? {
        return when (controls[kind]) {
            Control.PAUSE -> {
                publish(kind, DownloadStatus.PAUSED, currentProgress(kind), null)
                writeStatus(kind, DownloadStatus.PAUSED, null)
                AppLog.i(TAG, "paused $kind")
                DownloadOutcome.PAUSED
            }
            Control.STOP -> {
                deleteIncomplete(kind)
                publish(kind, DownloadStatus.NOT_INSTALLED, 0f, null)
                writeStatus(kind, DownloadStatus.NOT_INSTALLED, null)
                AppLog.i(TAG, "stopped $kind")
                DownloadOutcome.STOPPED
            }
            else -> null
        }
    }

    private fun downloadFile(kind: ModelKind, spec: ModelFile, all: List<ModelFile>) {
        val dir = directory(kind)
        val dest = File(dir, spec.name)
        if (dest.isFile && dest.length() > 0L) {
            publishBundle(kind, all)
            return
        }
        val partial = File(dir, spec.name + ".partial")
        val existing = if (partial.exists()) partial.length() else 0L
        val knownTotal = readTotal(partial)
        val plan = DownloadResume.plan(existing, knownTotal)
        if (plan.action == DownloadResume.Action.COMPLETE) {
            if (!partial.renameTo(dest)) partial.copyTo(dest, overwrite = true)
            partial.delete()
            publishBundle(kind, all)
            return
        }
        val request = Request.Builder()
            .url(spec.url)
            .header("User-Agent", "LoomixxNotes/1.0")
            .apply { plan.rangeHeader?.let { header("Range", it) } }
            .build()
        val call = client.newCall(request)
        try {
            call.execute().use { response ->
                if (response.code == 416) {
                    partial.delete()
                    clearTotal(partial)
                    error("Range отклонён, повтор с начала")
                }
                if (!response.isSuccessful && response.code != 206) {
                    error("HTTP ${response.code} для ${spec.name}")
                }
                val resumed = plan.action == DownloadResume.Action.RESUME
                val append = DownloadResume.isPartialContent(response.code) && !DownloadResume.shouldRewriteFromStart(response.code, resumed)
                if (DownloadResume.shouldRewriteFromStart(response.code, resumed)) {
                    partial.delete()
                }
                val total = when {
                    response.code == 206 -> DownloadResume.parseTotal(response.header("Content-Range"))
                    else -> response.body?.contentLength()?.takeIf { it > 0L }
                }
                if (total != null) writeTotal(partial, total)
                val body = response.body ?: error("Пустой ответ ${spec.name}")
                var written = if (append && partial.exists()) partial.length() else 0L
                java.io.FileOutputStream(partial, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            when (controls[kind]) {
                                Control.PAUSE, Control.STOP -> return
                                else -> Unit
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            publishBundle(kind, all, spec.name, written, total)
                        }
                    }
                }
                val expected = total ?: readTotal(partial)
                if (expected != null && partial.length() < expected) {
                    error("Файл ${spec.name} загружен не полностью")
                }
                if (!partial.renameTo(dest)) {
                    partial.copyTo(dest, overwrite = true)
                    partial.delete()
                }
                clearTotal(partial)
            }
        } finally {
            call.cancel()
        }
        publishBundle(kind, all)
    }

    private fun publishBundle(
        kind: ModelKind,
        all: List<ModelFile>,
        activeName: String? = null,
        activeBytes: Long = 0L,
        activeTotal: Long? = null,
    ) {
        val dir = directory(kind)
        val downloaded = mutableListOf<Long>()
        val totals = mutableListOf<Long?>()
        all.forEach { spec ->
            val finalFile = File(dir, spec.name)
            val partial = File(dir, spec.name + ".partial")
            val bytes = when {
                finalFile.isFile -> finalFile.length()
                spec.name == activeName -> activeBytes
                partial.exists() -> partial.length()
                else -> 0L
            }
            val total = when {
                spec.name == activeName && activeTotal != null -> activeTotal
                finalFile.isFile -> finalFile.length()
                else -> readTotal(partial)
            }
            downloaded += bytes
            totals += total
        }
        publish(kind, DownloadStatus.DOWNLOADING, DownloadResume.aggregate(downloaded, totals), null)
    }

    private fun currentProgress(kind: ModelKind): Float {
        val specs = ModelCatalog.files(kind)
        val dir = directory(kind)
        val downloaded = specs.map { spec ->
            val finalFile = File(dir, spec.name)
            val partial = File(dir, spec.name + ".partial")
            when {
                finalFile.isFile -> finalFile.length()
                partial.exists() -> partial.length()
                else -> 0L
            }
        }
        val totals = specs.map { spec ->
            val finalFile = File(dir, spec.name)
            if (finalFile.isFile) finalFile.length() else readTotal(File(dir, spec.name + ".partial"))
        }
        return DownloadResume.aggregate(downloaded, totals)
    }

    private fun snapshot(kind: ModelKind): ModelCardState {
        if (isInstalled(kind)) return ModelCardState(kind, DownloadStatus.INSTALLED, 1f)
        val status = readStatus(kind)
        val progress = currentProgress(kind)
        val resolved = when {
            status == DownloadStatus.PAUSED -> DownloadStatus.PAUSED
            status == DownloadStatus.FAILED -> DownloadStatus.FAILED
            status == DownloadStatus.DOWNLOADING -> DownloadStatus.DOWNLOADING
            progress > 0f -> DownloadStatus.PAUSED
            else -> DownloadStatus.NOT_INSTALLED
        }
        return ModelCardState(kind, resolved, progress, readError(kind))
    }

    private fun publish(kind: ModelKind, status: DownloadStatus, progress: Float, error: String?) {
        _cards.update { list ->
            list.map { card ->
                if (card.kind == kind) card.copy(status = status, progress = progress, error = error) else card
            }
        }
    }

    private fun enqueue(kind: ModelKind, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_KIND to kind.name))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                20,
                java.util.concurrent.TimeUnit.SECONDS,
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(kind), policy, request)
    }

    private fun workName(kind: ModelKind) = "model-download-${kind.name}"

    private fun statusFile(kind: ModelKind) = File(root, "${kind.folder}.status")

    private fun writeStatus(kind: ModelKind, status: DownloadStatus, error: String?) {
        statusFile(kind).writeText(status.name + "\n" + (error ?: ""))
    }

    private fun readStatus(kind: ModelKind): DownloadStatus {
        val file = statusFile(kind)
        if (!file.isFile) return DownloadStatus.NOT_INSTALLED
        val name = file.readLines().firstOrNull().orEmpty()
        return runCatching { DownloadStatus.valueOf(name) }.getOrDefault(DownloadStatus.NOT_INSTALLED)
    }

    private fun readError(kind: ModelKind): String? {
        val lines = statusFile(kind).takeIf { it.isFile }?.readLines().orEmpty()
        return lines.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun hasPartial(kind: ModelKind): Boolean {
        val dir = directory(kind)
        return ModelCatalog.files(kind).any { spec ->
            File(dir, spec.name + ".partial").exists() || File(dir, spec.name).isFile
        }
    }

    private fun deleteIncomplete(kind: ModelKind) {
        val dir = directory(kind)
        ModelCatalog.files(kind).forEach { spec ->
            File(dir, spec.name).delete()
            File(dir, spec.name + ".partial").delete()
            File(dir, spec.name + ".partial.total").delete()
        }
    }

    private fun totalSidecar(partial: File) = File(partial.parentFile, partial.name + ".total")

    private fun writeTotal(partial: File, total: Long) {
        totalSidecar(partial).writeText(total.toString())
    }

    private fun readTotal(partial: File): Long? {
        val file = totalSidecar(partial)
        if (!file.isFile) return null
        return file.readText().trim().toLongOrNull()
    }

    private fun clearTotal(partial: File) {
        totalSidecar(partial).delete()
    }

    private companion object {
        const val TAG = "ModelDownload"
    }
}
