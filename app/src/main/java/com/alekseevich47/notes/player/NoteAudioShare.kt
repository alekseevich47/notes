package com.alekseevich47.notes.player

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.alekseevich47.notes.log.AppLog
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WAV PCM to M4A (AAC). Android has no built-in MP3 encoder; AAC in MPEG-4 plays
 * on Android, iOS, and Windows. Logs never include note text.
 */
object NoteAudioShare {
    private const val TAG = "NoteShare"
    private const val CHOOSER_TITLE = "Поделиться записью"
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "note-audio-share").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)

    private fun cached(context: Context, noteId: Long?, wavPath: String): File? {
        val wav = File(wavPath)
        if (!wav.isFile) return null
        val out = File(File(context.cacheDir, "share"), WavAudio.shareFileName(noteId))
        if (!out.isFile || out.length() <= 0L || out.lastModified() < wav.lastModified()) return null
        return out
    }

    fun prepare(context: Context, wavPath: String, noteId: Long?, onReady: () -> Unit) {
        val app = context.applicationContext
        if (cached(app, noteId, wavPath) != null) {
            main.post(onReady)
            return
        }
        io.execute {
            runCatching { ensure(app, wavPath, noteId) }
                .onFailure { error -> AppLog.e(TAG, "share prepare failed", error) }
            main.post(onReady)
        }
    }

    fun share(context: Context, wavPath: String, noteId: Long?, onFinished: ((Boolean) -> Unit)? = null) {
        if (!busy.compareAndSet(false, true)) return
        val app = context.applicationContext
        io.execute {
            val file = runCatching { ensure(app, wavPath, noteId) }.getOrElse { error ->
                AppLog.e(TAG, "share failed", error)
                null
            }
            main.post {
                val ok = if (file == null) {
                    false
                } else {
                    runCatching { launch(context, file) }.onFailure { error -> AppLog.e(TAG, "share failed", error) }.isSuccess
                }
                busy.set(false)
                onFinished?.invoke(ok)
            }
        }
    }

    private fun ensure(context: Context, wavPath: String, noteId: Long?): File {
        val wav = File(wavPath)
        if (!wav.isFile) error("missing audio")
        cached(context, noteId, wavPath)?.let { return it }
        val pcm = WavAudio.read(wav) ?: error("unreadable audio")
        if (pcm.bits != 16 || pcm.dataBytes <= 0L) error("empty audio")
        val out = target(context, noteId)
        val partial = File(out.parentFile, out.name + ".partial")
        if (partial.exists()) partial.delete()
        try {
            transcode(wav, pcm, partial)
            if (out.exists()) out.delete()
            if (!partial.renameTo(out)) {
                partial.copyTo(out, overwrite = true)
                partial.delete()
            }
        } catch (error: Throwable) {
            partial.delete()
            out.delete()
            throw error
        }
        if (!out.isFile || out.length() <= 0L) error("empty share")
        return out
    }

    private fun target(context: Context, noteId: Long?): File {
        val dir = File(context.cacheDir, "share")
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) error("share cache")
        return File(dir, WavAudio.shareFileName(noteId))
    }

    private fun transcode(wav: File, pcm: WavPcm, out: File) {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            pcm.sampleRate,
            pcm.channels,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        val muxerStarted = booleanArrayOf(false)
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val created = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = created
            encode(encoder, created, wav, pcm, muxerStarted)
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted[0]) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
        if (!muxerStarted[0]) error("no audio output")
    }

    private fun encode(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        wav: File,
        pcm: WavPcm,
        muxerStarted: BooleanArray,
    ) {
        val frame = pcm.channels * (pcm.bits / 8)
        if (frame <= 0) error("empty audio")
        RandomAccessFile(wav, "r").use { raf ->
            raf.seek(pcm.dataOffset)
            var remaining = pcm.dataBytes
            var samplesIn = 0L
            var inputDone = false
            var outputDone = false
            var started = false
            var track = -1
            val info = MediaCodec.BufferInfo()
            val deadline = SystemClock.elapsedRealtime() + 30_000L
            while (!outputDone) {
                if (SystemClock.elapsedRealtime() > deadline) error("encode timeout")
                if (!inputDone) {
                    val index = encoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = encoder.getInputBuffer(index)
                        if (buffer == null) {
                            encoder.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buffer.clear()
                            val capacity = buffer.remaining()
                            val aligned = (minOf(capacity.toLong(), remaining) / frame).toInt() * frame
                            if (aligned <= 0) {
                                val pts = samplesIn * 1_000_000L / pcm.sampleRate
                                encoder.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val bytes = ByteArray(aligned)
                                raf.readFully(bytes)
                                buffer.put(bytes)
                                val pts = samplesIn * 1_000_000L / pcm.sampleRate
                                encoder.queueInputBuffer(index, 0, aligned, pts, 0)
                                samplesIn += aligned / frame
                                remaining -= aligned
                            }
                        }
                    }
                }
                when (val index = encoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (started) error("format changed twice")
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        started = true
                        muxerStarted[0] = true
                    }
                    else -> if (index >= 0) {
                        val encoded = encoder.getOutputBuffer(index)
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (encoded != null && info.size > 0 && !config && started) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(track, encoded, info)
                        }
                        encoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            if (!started) error("no audio output")
        }
    }

    private fun launch(context: Context, file: File) {
        val chooser = chooser(context, file)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun chooser(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = WavAudio.SHARE_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "audio", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, CHOOSER_TITLE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = send.clipData
        }
    }
}
