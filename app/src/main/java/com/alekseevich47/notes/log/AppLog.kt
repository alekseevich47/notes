package com.alekseevich47.notes.log

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.alekseevich47.notes.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Diagnostic log at `filesDir/logs/app.log`.
 *
 * The file rotates once it reaches [MAX_BYTES] (1.5 MB, inside the 1–2 MB window)
 * by dropping the oldest lines and keeping the newest half.
 *
 * Each process start rewrites a device header at the top of the file. Later
 * lines are appended under it, and rotation keeps that header in place so it
 * is not buried after older lines.
 *
 * Never pass note titles, note bodies, transcripts, PIN digits, or audio bytes.
 * An audio filename (basename only) is acceptable on error lines; absolute paths
 * are reduced to that basename before they are written.
 */
object AppLog {
    const val MAX_BYTES = 1_500_000L
    const val FILE_NAME = "app.log"

    private const val DIR_NAME = "logs"
    private const val MAX_MESSAGE = 240
    private const val CHOOSER_TITLE = "Поделиться логом"
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val URL = Regex("""https?://\S+""")
    private val ABS_PATH = Regex("""(?:[A-Za-z]:)?(?:/[\w.\-]+)+""")
    private val PIN_DIGITS = Regex("""(?i)(\bpin\b[^0-9]{0,16})(\d{4,8})\b""")
    private val lock = ReentrantLock()
    private val headerBegin = "===== notes log header =====\n".toByteArray(Charsets.UTF_8)
    private val headerEnd = "===== end header =====\n".toByteArray(Charsets.UTF_8)

    private var directory: File? = null
    private var maxBytes: Long = MAX_BYTES
    private var logcat: Boolean = true
    private var headerBlock: String = ""

    fun install(context: Context) {
        install(File(context.applicationContext.filesDir, DIR_NAME))
    }

    internal fun install(directory: File, maxBytes: Long = MAX_BYTES, logcat: Boolean = true) {
        lock.withLock {
            directory.mkdirs()
            this.directory = directory
            this.maxBytes = maxBytes.coerceIn(64L, 2_000_000L)
            this.logcat = logcat
            this.headerBlock = ""
        }
    }

    fun file(): File? = lock.withLock { directory?.let { File(it, FILE_NAME) } }

    /** Device and build facts at process start. No note text, paths, or audio. */
    fun logProcessStart() {
        val lines = processStartLines(
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
        )
        rewriteHeader(lines)
        if (lock.withLock { logcat }) {
            lines.forEach { line -> mirrorLogcat("I", "NotesApp", sanitize(line)) }
        }
    }

    /**
     * Replace the header at the top of the log file. Body lines that were
     * already written stay after the new header. A previous header is removed
     * so process starts do not stack.
     */
    internal fun rewriteHeader(lines: List<String>) {
        val block = buildString {
            append("===== notes log header =====\n")
            lines.forEach { line ->
                append(sanitize(line))
                append('\n')
            }
            append("===== end header =====\n")
        }
        lock.withLock {
            headerBlock = block
            val dir = directory ?: return@withLock
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, FILE_NAME)
            val existing = if (file.isFile) file.readBytes() else ByteArray(0)
            val body = stripHeader(existing)
            FileOutputStream(file, false).use { output ->
                output.write(block.toByteArray(Charsets.UTF_8))
                output.write(body)
            }
        }
    }

    internal fun processStartLines(
        manufacturer: String,
        brand: String,
        model: String,
        device: String,
        release: String,
        sdk: Int,
        incremental: String,
        display: String,
        abis: String,
        locale: String,
        versionName: String,
        versionCode: Int,
        buildType: String? = null,
        debuggable: Boolean? = null,
        pid: Int? = null,
        memoryClass: Int? = null,
        lowRam: Boolean? = null,
        totalMemMb: Long? = null,
        availMemMb: Long? = null,
        freeDiskMb: Long? = null,
        perfTier: String? = null,
        toneInstalled: Boolean? = null,
        toneEnabled: Boolean? = null,
        gigaInstalled: Boolean? = null,
        gigaEnabled: Boolean? = null,
        recognizer: String? = null,
        warmupOnBoot: Boolean? = null,
        micGranted: Boolean? = null,
        notificationsGranted: Boolean? = null,
        batteryOptIgnored: Boolean? = null,
        timezone: String? = null,
        theme: String? = null,
    ): List<String> {
        val lines = mutableListOf(
            "onCreate manufacturer=$manufacturer brand=$brand model=$model device=$device",
            "android release=$release sdk=$sdk incremental=$incremental",
            "android display=$display",
            "abis=$abis locale=$locale versionName=$versionName versionCode=$versionCode",
        )
        buildType?.let { lines += "buildType=$it" }
        debuggable?.let { lines += "debuggable=$it" }
        pid?.let { lines += "pid=$it" }
        memoryClass?.let { lines += "memoryClass=$it" }
        lowRam?.let { lines += "lowRam=$it" }
        totalMemMb?.let { lines += "totalMemMb=$it" }
        availMemMb?.let { lines += "availMemMb=$it" }
        freeDiskMb?.let { lines += "freeDiskMb=$it" }
        perfTier?.let { lines += "perfTier=$it" }
        toneInstalled?.let { lines += "toneInstalled=$it" }
        toneEnabled?.let { lines += "toneEnabled=$it" }
        gigaInstalled?.let { lines += "gigaInstalled=$it" }
        gigaEnabled?.let { lines += "gigaEnabled=$it" }
        recognizer?.let { lines += "recognizer=$it" }
        warmupOnBoot?.let { lines += "warmupOnBoot=$it" }
        micGranted?.let { lines += "micGranted=$it" }
        notificationsGranted?.let { lines += "notificationsGranted=$it" }
        batteryOptIgnored?.let { lines += "batteryOptIgnored=$it" }
        timezone?.let { lines += "timezone=$it" }
        theme?.let { lines += "theme=$it" }
        return lines
    }

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String) = write("W", tag, message, null)

    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    fun share(context: Context) {
        val current = file() ?: error("Журнал ещё не готов")
        val bytes = if (current.isFile) current.length() else 0L
        i("AppLog", "share bytes=$bytes")
        val ready = file() ?: error("Журнал ещё не готов")
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                ready,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, FILE_NAME, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, CHOOSER_TITLE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = send.clipData
            }
            val activity = context.findActivity()
            if (activity == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            (activity ?: context).startActivity(chooser)
        } catch (error: Throwable) {
            e("AppLog", "share failed", error)
            throw error
        }
    }

    internal fun sanitize(raw: String): String {
        var text = raw.replace(Regex("[\\r\\n\\t]+"), " ").trim()
        text = URL.replace(text, "[url]")
        text = ABS_PATH.replace(text) { match ->
            val base = match.value.substringAfterLast('/')
            if ('.' in base) base else "[path]"
        }
        text = PIN_DIGITS.replace(text) { match -> match.groupValues[1] + "****" }
        if (text.length > MAX_MESSAGE) text = text.take(MAX_MESSAGE) + "…[truncated]"
        return text
    }

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val safeTag = sanitize(tag).ifBlank { "App" }
        val safeMessage = sanitize(message)
        val line = buildString {
            append(LocalDateTime.now().format(STAMP))
            append(' ')
            append(level)
            append('/')
            append(safeTag)
            append(' ')
            append(safeMessage)
            if (error != null) {
                append('\n')
                append(renderError(error))
            }
            append('\n')
        }
        var mirror = false
        lock.withLock {
            val dir = directory ?: return@withLock
            mirror = logcat
            try {
                append(dir, line)
            } catch (_: Throwable) {
                mirror = false
            }
        }
        if (mirror) mirrorLogcat(level, safeTag, safeMessage)
    }

    private fun append(dir: File, line: String) {
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, FILE_NAME)
        val payload = line.toByteArray(Charsets.UTF_8)
        if (file.isFile && file.length() + payload.size > maxBytes) rotate(file)
        FileOutputStream(file, true).use { it.write(payload) }
    }

    private fun rotate(file: File) {
        val raw = if (file.isFile) file.readBytes() else ByteArray(0)
        val bytes = stripHeader(raw)
        val keepFrom = (bytes.size - (maxBytes / 2).toInt()).coerceAtLeast(0)
        var cut = keepFrom
        while (cut < bytes.size && bytes[cut] != '\n'.code.toByte()) cut++
        if (cut < bytes.size) cut++ else cut = bytes.size
        val marker = "----- rotated, older lines dropped -----\n".toByteArray(Charsets.UTF_8)
        val header = headerBlock.toByteArray(Charsets.UTF_8)
        FileOutputStream(file, false).use { output ->
            if (header.isNotEmpty()) output.write(header)
            output.write(marker)
            if (cut < bytes.size) output.write(bytes, cut, bytes.size - cut)
        }
    }

    private fun stripHeader(bytes: ByteArray): ByteArray {
        if (bytes.size < headerBegin.size || !bytes.startsWith(headerBegin)) return bytes
        val end = bytes.indexOfSlice(headerEnd)
        if (end < 0) return ByteArray(0)
        val from = end + headerEnd.size
        if (from >= bytes.size) return ByteArray(0)
        return bytes.copyOfRange(from, bytes.size)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (index in prefix.indices) {
            if (this[index] != prefix[index]) return false
        }
        return true
    }

    private fun ByteArray.indexOfSlice(needle: ByteArray): Int {
        if (needle.isEmpty() || size < needle.size) return -1
        val last = size - needle.size
        for (start in 0..last) {
            var match = true
            for (index in needle.indices) {
                if (this[start + index] != needle[index]) {
                    match = false
                    break
                }
            }
            if (match) return start
        }
        return -1
    }

    private fun renderError(error: Throwable): String {
        val lines = ArrayList<String>(12)
        val seen = HashSet<Throwable>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 4 && seen.add(current)) {
            val detail = current.message?.let(::sanitize).orEmpty()
            lines += if (detail.isBlank()) current.javaClass.name else "${current.javaClass.name}: $detail"
            if (depth == 0) {
                current.stackTrace.take(8).forEach { frame ->
                    lines += "  at ${frame.className}.${frame.methodName}:${frame.lineNumber}"
                }
            }
            current = current.cause
            depth++
        }
        return lines.joinToString("\n")
    }

    private fun mirrorLogcat(level: String, tag: String, message: String) {
        runCatching {
            when (level) {
                "E" -> android.util.Log.e(tag, message)
                "W" -> android.util.Log.w(tag, message)
                else -> android.util.Log.i(tag, message)
            }
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        repeat(6) {
            if (current is Activity) return current
            if (current !is ContextWrapper) return null
            current = current.baseContext ?: return null
        }
        return null
    }
}

/** Body line that separates one process from the next. No note text. */
internal fun processMarker(pid: Int, session: Int): String {
    return "===== process start pid=$pid session=$session ====="
}

/**
 * Stable name for `ApplicationExitInfo` reason codes. The integers are the
 * public API values (stubs do not inline them). Call only on API 30+.
 * Unknown codes keep the numeric value.
 */
internal fun exitReasonName(code: Int): String = when (code) {
    0 -> "UNKNOWN" // REASON_UNKNOWN
    1 -> "EXIT_SELF" // REASON_EXIT_SELF
    2 -> "SIGNALED" // REASON_SIGNALED
    3 -> "LOW_MEMORY" // REASON_LOW_MEMORY
    4 -> "CRASH" // REASON_CRASH
    5 -> "CRASH_NATIVE" // REASON_CRASH_NATIVE
    6 -> "ANR" // REASON_ANR
    7 -> "INITIALIZATION_FAILURE" // REASON_INITIALIZATION_FAILURE
    8 -> "PERMISSION_CHANGE" // REASON_PERMISSION_CHANGE
    9 -> "EXCESSIVE_RESOURCE_USAGE" // REASON_EXCESSIVE_RESOURCE_USAGE
    10 -> "USER_REQUESTED" // REASON_USER_REQUESTED
    11 -> "USER_STOPPED" // REASON_USER_STOPPED
    12 -> "DEPENDENCY_DIED" // REASON_DEPENDENCY_DIED
    13 -> "OTHER" // REASON_OTHER
    14 -> "FREEZER" // REASON_FREEZER
    15 -> "PACKAGE_STATE_CHANGE" // REASON_PACKAGE_STATE_CHANGE
    16 -> "PACKAGE_UPDATED" // REASON_PACKAGE_UPDATED
    else -> "REASON_$code"
}
