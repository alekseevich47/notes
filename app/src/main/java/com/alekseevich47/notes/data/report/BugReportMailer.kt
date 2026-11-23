package com.alekseevich47.notes.data.report

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.alekseevich47.notes.BuildConfig
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.ui.settings.SettingsCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

data class PreparedReport(
    val subject: String,
    val body: String,
    val attachments: List<Uri>,
    val logAttached: Boolean,
)

/**
 * Builds a mail intent the user's own mail app sends. Nothing is uploaded
 * from this process. Screenshot bytes are decoded and re-encoded on [Dispatchers.IO].
 */
object BugReportMailer {
    private const val DIR_NAME = "bugreport"
    private const val LOG_NAME = "app.log"
    private const val MAX_SIDE = 1600
    private const val JPEG_START = 85
    private const val JPEG_FLOOR = 60
    private const val MAX_SHOT_BYTES = (1.5 * 1024 * 1024).toLong()
    private const val MAX_TOTAL_BYTES = 8L * 1024L * 1024L
    private const val STALE_MS = 24L * 60L * 60L * 1000L

    suspend fun prepare(
        context: Context,
        form: BugReportForm,
        screenshots: List<Uri>,
        withLog: Boolean,
    ): PreparedReport = withContext(Dispatchers.IO) {
        val info = deviceInfo()
        val body = BugReportMail.body(form, info)
        val subject = BugReportMail.subject(info.versionName)
        try {
            val prepared = assemble(context, subject, body, screenshots, withLog)
            logBranch("prepared", prepared.attachments.size, prepared.logAttached, body.length)
            prepared
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logBranch("failed", 0, withLog, body.length)
            throw error
        }
    }

    fun launch(context: Context, prepared: PreparedReport) {
        val hasAttachments = prepared.attachments.isNotEmpty()
        val send = Intent(
            if (hasAttachments) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND,
        ).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SettingsCopy.EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, prepared.subject)
            putExtra(Intent.EXTRA_TEXT, prepared.body)
            if (hasAttachments) {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(prepared.attachments))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipOf(context, prepared.attachments)?.let { clipData = it }
            }
        }
        val chooser = Intent.createChooser(send, "Сообщить об ошибке").apply {
            if (hasAttachments) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData?.let { clipData = it }
            if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
            logBranch("opened", prepared.attachments.size, prepared.logAttached, prepared.body.length)
        } catch (error: ActivityNotFoundException) {
            copyAddress(context)
            logBranch("no mail app", prepared.attachments.size, prepared.logAttached, prepared.body.length)
            throw error
        }
    }

    /** Deletes `cache/bugreport` files older than one day. Safe to call at process start. */
    fun purgeStale(context: Context) {
        try {
            val dir = File(context.cacheDir, DIR_NAME)
            if (!dir.isDirectory) return
            val cutoff = System.currentTimeMillis() - STALE_MS
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AppLog.w("BugReport", "purge failed")
        }
    }

    /**
     * Decodes [uri] so the long side is at most [maxSide]. Uses [BitmapFactory.Options.inSampleSize]
     * before the pixel decode. Returns null when the bytes cannot be decoded.
     * Call from [Dispatchers.IO], never from composition.
     */
    internal fun decodeBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        var hold: Bitmap? = null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null
            hold = decoded
            val fitted = scaleDown(decoded, maxSide)
            hold = fitted
            val oriented = applyOrientation(fitted, readOrientation(context, uri))
            hold = null
            return oriented
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        } catch (_: OutOfMemoryError) {
            return null
        } finally {
            val leftover = hold
            if (leftover != null && !leftover.isRecycled) leftover.recycle()
        }
    }

    private suspend fun assemble(
        context: Context,
        subject: String,
        body: String,
        screenshots: List<Uri>,
        withLog: Boolean,
    ): PreparedReport {
        val app = context.applicationContext
        val dir = File(app.cacheDir, DIR_NAME)
        if (!dir.isDirectory && !dir.mkdirs()) error("bugreport cache unavailable")
        clearReportFiles(dir)
        val log = copyLog(app, dir, withLog)
        var total = log?.length() ?: 0L
        val streams = ArrayList<Uri>(BugReportMail.MAX_SHOTS + 1)
        var considered = 0
        var written = 0
        val seen = HashSet<Uri>(screenshots.size)
        for (uri in screenshots) {
            coroutineContext.ensureActive()
            if (!seen.add(uri)) continue
            if (considered >= BugReportMail.MAX_SHOTS) break
            considered++
            val dest = File(dir, "shot_${written + 1}.jpg")
            val size = writeShot(app, uri, dest)
            if (size <= 0L) {
                dest.delete()
                continue
            }
            if (total + size > MAX_TOTAL_BYTES) {
                dest.delete()
                AppLog.w("BugReport", "screenshot skipped")
                continue
            }
            val content = runCatching { uriFor(app, dest) }.getOrNull()
            if (content == null) {
                dest.delete()
                AppLog.w("BugReport", "screenshot skipped")
                continue
            }
            streams.add(content)
            written++
            total += size
        }
        var logAttached = false
        if (log != null) {
            val content = runCatching { uriFor(app, log) }.getOrNull()
            if (content != null) {
                streams.add(content)
                logAttached = true
            } else {
                log.delete()
            }
        }
        return PreparedReport(
            subject = subject,
            body = body,
            attachments = streams,
            logAttached = logAttached,
        )
    }

    private fun copyLog(context: Context, dir: File, withLog: Boolean): File? {
        if (!withLog) return null
        val source = AppLog.file()
        if (source == null || !source.isFile) return null
        val dest = File(dir, LOG_NAME)
        return try {
            source.copyTo(dest, overwrite = true)
            if (!dest.isFile || dest.length() > MAX_TOTAL_BYTES) {
                dest.delete()
                null
            } else {
                dest
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            dest.delete()
            null
        }
    }

    /**
     * Re-encoding through [Bitmap.compress] writes a new JPEG and does not copy
     * EXIF (no GPS, no camera model). Orientation is baked into the pixels first.
     * Returns the file size, or 0 when the image cannot be used.
     */
    private fun writeShot(context: Context, uri: Uri, dest: File): Long {
        val bitmap = decodeBitmap(context, uri, MAX_SIDE)
        if (bitmap == null) {
            AppLog.w("BugReport", "screenshot skipped")
            return 0L
        }
        try {
            var quality = JPEG_START
            var size: Long
            while (true) {
                FileOutputStream(dest).use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                        dest.delete()
                        AppLog.w("BugReport", "screenshot skipped")
                        return 0L
                    }
                }
                size = dest.length()
                if (size <= MAX_SHOT_BYTES || quality <= JPEG_FLOOR) break
                quality = (quality - 5).coerceAtLeast(JPEG_FLOOR)
            }
            if (size > MAX_SHOT_BYTES) {
                dest.delete()
                AppLog.w("BugReport", "screenshot skipped")
                return 0L
            }
            return size
        } catch (error: CancellationException) {
            dest.delete()
            throw error
        } catch (_: Exception) {
            dest.delete()
            AppLog.w("BugReport", "screenshot skipped")
            return 0L
        } catch (_: OutOfMemoryError) {
            dest.delete()
            AppLog.w("BugReport", "screenshot skipped")
            return 0L
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun scaleDown(source: Bitmap, maxSide: Int): Bitmap {
        val longSide = maxOf(source.width, source.height)
        if (longSide <= maxSide || longSide <= 0) return source
        val scale = maxSide.toFloat() / longSide.toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        if (scaled != source) source.recycle()
        return scaled
    }

    private fun readOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = orientationMatrix(orientation) ?: return source
        val turned = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (turned != source) source.recycle()
        return turned
    }

    private fun orientationMatrix(orientation: Int): Matrix? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return null
        }
        return matrix
    }

    private fun clearReportFiles(dir: File) {
        dir.listFiles()?.forEach { file ->
            val name = file.name
            val shot = name.startsWith("shot_") && name.endsWith(".jpg")
            if (file.isFile && (shot || name == LOG_NAME)) file.delete()
        }
    }

    private fun uriFor(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun clipOf(context: Context, uris: List<Uri>): ClipData? {
        if (uris.isEmpty()) return null
        val clip = ClipData.newUri(context.contentResolver, "bugreport", uris.first())
        for (index in 1 until uris.size) {
            clip.addItem(ClipData.Item(uris[index]))
        }
        return clip
    }

    private fun copyAddress(context: Context) {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        manager.setPrimaryClip(ClipData.newPlainText("email", SettingsCopy.EMAIL))
    }

    private fun deviceInfo(): BugReportInfo {
        return BugReportInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
            release = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
        )
    }

    private fun logBranch(branch: String, attachments: Int, log: Boolean, textLength: Int) {
        AppLog.i("BugReport", "$branch attachments=$attachments log=$log text=$textLength")
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
