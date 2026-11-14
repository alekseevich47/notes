package com.alekseevich47.notes.data.download

import android.content.Context
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.domain.ModelDownloadState
import com.alekseevich47.notes.domain.ModelKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val repository: NotesRepository,
) {
    private val root: File get() = File(context.filesDir, "models").also { it.mkdirs() }

    private val _states = MutableStateFlow(
        ModelKind.entries.map { kind ->
            ModelDownloadState(kind = kind, installed = isInstalled(kind))
        },
    )
    val states: StateFlow<List<ModelDownloadState>> = _states.asStateFlow()

    fun modelDir(kind: ModelKind): File = File(root, kind.folder)

    fun isInstalled(kind: ModelKind): Boolean {
        val dir = modelDir(kind)
        return when (kind) {
            ModelKind.T_ONE -> File(dir, "model.onnx").isFile && File(dir, "tokens.txt").isFile
            ModelKind.GIGA_AM ->
                File(dir, "encoder.int8.onnx").isFile &&
                    File(dir, "decoder.onnx").isFile &&
                    File(dir, "joiner.onnx").isFile &&
                    File(dir, "tokens.txt").isFile
        }
    }

    fun refresh() {
        _states.value = ModelKind.entries.map { kind ->
            ModelDownloadState(kind = kind, installed = isInstalled(kind))
        }
    }

    suspend fun download(kind: ModelKind) = withContext(Dispatchers.IO) {
        setState(kind) { it.copy(downloading = true, progress = 0f, error = null) }
        try {
            val url = urlFor(kind)
            val tmp = File(root, "${kind.folder}.download")
            if (tmp.exists()) tmp.deleteRecursively()
            tmp.mkdirs()
            val archive = File(tmp, "pack.bin")
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("empty body")
                val total = body.contentLength().coerceAtLeast(1L)
                body.byteStream().use { input ->
                    archive.outputStream().use { out ->
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                        var readTotal = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            readTotal += n
                            val p = (readTotal.toFloat() / total).coerceIn(0f, 1f)
                            setState(kind) { it.copy(progress = p * 0.85f) }
                        }
                    }
                }
            }
            val dest = modelDir(kind)
            if (dest.exists()) dest.deleteRecursively()
            dest.mkdirs()
            extractArchive(archive, dest)
            setState(kind) { it.copy(progress = 1f) }
            if (!isInstalled(kind)) error("Файлы модели не найдены после распаковки")
            repository.updateSettings { s ->
                when (kind) {
                    ModelKind.T_ONE -> s.copy(tOneInstalled = true)
                    ModelKind.GIGA_AM -> s.copy(gigaInstalled = true)
                }
            }
            setState(kind) { it.copy(installed = true, downloading = false, progress = 1f) }
            tmp.deleteRecursively()
        } catch (t: Throwable) {
            setState(kind) {
                it.copy(downloading = false, error = t.message ?: "Ошибка загрузки", progress = 0f)
            }
        }
    }

    private fun extractArchive(archive: File, dest: File) {
        // Support zip (preferred) and plain copy of known onnx layouts.
        if (archive.name.endsWith(".bin") || isZip(archive)) {
            ZipInputStream(archive.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.substringAfterLast('/')
                        if (name.isNotBlank() && !name.startsWith(".")) {
                            File(dest, name).outputStream().use { out -> zis.copyTo(out) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } else {
            archive.copyTo(File(dest, archive.name), overwrite = true)
        }
    }

    private fun isZip(file: File): Boolean {
        return file.inputStream().use { ins ->
            val sig = ByteArray(4)
            val n = ins.read(sig)
            n == 4 && sig[0] == 0x50.toByte() && sig[1] == 0x4b.toByte()
        }
    }

    private fun setState(kind: ModelKind, transform: (ModelDownloadState) -> ModelDownloadState) {
        _states.update { list ->
            list.map { if (it.kind == kind) transform(it) else it }
        }
    }

    private fun urlFor(kind: ModelKind): String = when (kind) {
        // Hosted release packs (zip of onnx + tokens). Override via BuildConfig later if needed.
        ModelKind.T_ONE ->
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-t-one-russian-2025-09-08.tar.bz2"
        ModelKind.GIGA_AM ->
            "https://huggingface.co/Alexxerm/gigaam-v3-e2e-rnnt-sherpa-onnx/resolve/main/encoder.int8.onnx"
    }

    companion object {
        // For GigaAM HF single-file URL we also need sibling files — download set below.
    }

    suspend fun downloadGigaBundle() = withContext(Dispatchers.IO) {
        val kind = ModelKind.GIGA_AM
        setState(kind) { it.copy(downloading = true, progress = 0f, error = null) }
        try {
            val dest = modelDir(kind)
            dest.mkdirs()
            val base = "https://huggingface.co/Alexxerm/gigaam-v3-e2e-rnnt-sherpa-onnx/resolve/main/"
            val files = listOf("encoder.int8.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt")
            files.forEachIndexed { index, name ->
                val out = File(dest, name)
                client.newCall(Request.Builder().url(base + name).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code} for $name")
                    val body = resp.body ?: error("empty $name")
                    body.byteStream().use { input -> out.outputStream().use { input.copyTo(it) } }
                }
                setState(kind) { it.copy(progress = (index + 1f) / files.size) }
            }
            repository.updateSettings { it.copy(gigaInstalled = true) }
            setState(kind) { it.copy(installed = true, downloading = false, progress = 1f) }
        } catch (t: Throwable) {
            setState(kind) {
                it.copy(downloading = false, error = t.message ?: "Ошибка загрузки")
            }
        }
    }

    suspend fun downloadToneBundle() = withContext(Dispatchers.IO) {
        val kind = ModelKind.T_ONE
        setState(kind) { it.copy(downloading = true, progress = 0f, error = null) }
        try {
            val dest = modelDir(kind)
            dest.mkdirs()
            // Direct raw files mirrored from HF pack when tar.bz2 is awkward on-device.
            val base = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-t-one-russian-2025-09-08/resolve/main/"
            val files = listOf("model.onnx", "tokens.txt")
            files.forEachIndexed { index, name ->
                val out = File(dest, name)
                client.newCall(Request.Builder().url(base + name).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code} for $name")
                    val body = resp.body ?: error("empty $name")
                    val total = body.contentLength().coerceAtLeast(1L)
                    var readTotal = 0L
                    body.byteStream().use { input ->
                        out.outputStream().use { outStream ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                outStream.write(buf, 0, n)
                                readTotal += n
                                val fileProgress = readTotal.toFloat() / total
                                val overall = (index + fileProgress) / files.size
                                setState(kind) { it.copy(progress = overall.coerceIn(0f, 1f)) }
                            }
                        }
                    }
                }
            }
            repository.updateSettings { it.copy(tOneInstalled = true) }
            setState(kind) { it.copy(installed = true, downloading = false, progress = 1f) }
        } catch (t: Throwable) {
            setState(kind) {
                it.copy(downloading = false, error = t.message ?: "Ошибка загрузки")
            }
        }
    }
}
