package com.alekseevich47.notes.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.alekseevich47.notes.MainActivity
import com.alekseevich47.notes.R
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.service.RecordingService

object WidgetUpdater {
    @Volatile var phase: MicPhase = MicPhase.IDLE
    @Volatile var level: Float = 0f
    @Volatile var transcript: String = ""
    @Volatile var frame: Int = 0
    @Volatile var fadeFrame: Int = WidgetMotion.FRAME_COUNT - 1

    private val gate = Any()
    private val dotFrames = intArrayOf(
        R.drawable.ic_widget_dots_0,
        R.drawable.ic_widget_dots_1,
        R.drawable.ic_widget_dots_2,
        R.drawable.ic_widget_dots_3,
        R.drawable.ic_widget_dots_4,
        R.drawable.ic_widget_dots_5,
    )

    fun push(context: Context, phase: MicPhase, level: Float, transcript: String) {
        synchronized(gate) {
            val previous = rollingLines(this.transcript)
            val next = rollingLines(transcript)
            this.phase = phase
            this.level = level
            this.transcript = transcript
            frame = (frame + 1) % WidgetMotion.FRAME_COUNT
            fadeFrame = if (WidgetMotion.shouldResetFade(previous, next)) {
                0
            } else {
                (fadeFrame + 1).coerceAtMost(WidgetMotion.FRAME_COUNT - 1)
            }
            render(context)
        }
    }

    fun refresh(context: Context) = render(context.applicationContext)

    fun bumpFrame(context: Context) {
        synchronized(gate) {
            frame = (frame + 1) % WidgetMotion.FRAME_COUNT
            if (fadeFrame < WidgetMotion.FRAME_COUNT - 1) fadeFrame += 1
            render(context)
        }
    }

    private fun render(context: Context) {
        synchronized(gate) {
            val manager = AppWidgetManager.getInstance(context)
            val small = ComponentName(context, NotesWidgetSmall::class.java)
            val wide = ComponentName(context, NotesWidgetWide::class.java)
            manager.getAppWidgetIds(small).forEach { id ->
                manager.updateAppWidget(id, smallViews(context))
            }
            manager.getAppWidgetIds(wide).forEach { id ->
                manager.updateAppWidget(id, wideViews(context))
            }
        }
    }

    private fun smallViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_2x2)
        applyMic(views, phase, level, frame)
        views.setOnClickPendingIntent(R.id.widget_root, micIntent(context))
        views.setOnClickPendingIntent(R.id.widget_mic, micIntent(context))
        return views
    }

    private fun wideViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_4x2)
        applyMic(views, phase, level, frame)
        val lines = rollingLines(transcript)
        val ids = intArrayOf(R.id.line0, R.id.line1, R.id.line2, R.id.line3)
        val base = ContextCompat.getColor(context, R.color.widget_text)
        ids.forEachIndexed { index, viewId ->
            val value = lines.getOrNull(index).orEmpty()
            views.setTextViewText(viewId, value)
            val alpha = if (value.isBlank()) {
                0f
            } else {
                WidgetMotion.transcriptAlpha(index, ids.size, fadeFrame)
            }
            views.setTextColor(viewId, WidgetMotion.colorWithAlpha(base, alpha))
            views.setFloat(viewId, "setAlpha", 1f)
        }
        views.setOnClickPendingIntent(R.id.widget_mic, micIntent(context))
        views.setOnClickPendingIntent(R.id.widget_transcript, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        return views
    }

    private fun applyMic(views: RemoteViews, phase: MicPhase, level: Float, frame: Int) {
        val hot = phase == MicPhase.RECORDING || phase == MicPhase.PROCESSING
        views.setInt(
            R.id.widget_mic,
            "setBackgroundResource",
            if (hot) R.drawable.widget_mic_plate_rec else R.drawable.widget_mic_plate,
        )
        val icon = when (phase) {
            MicPhase.RECORDING -> R.drawable.ic_widget_mic_rec
            MicPhase.PROCESSING -> dotFrames[WidgetMotion.dotFrame(frame)]
            MicPhase.DONE -> R.drawable.ic_widget_done
            MicPhase.IDLE -> R.drawable.ic_widget_mic
        }
        views.setImageViewResource(R.id.widget_mic_icon, icon)
        val scale = when (phase) {
            MicPhase.RECORDING -> WidgetMotion.recordingScale(frame, level)
            MicPhase.PROCESSING -> WidgetMotion.processingScale(frame)
            MicPhase.DONE -> WidgetMotion.doneScale(frame)
            MicPhase.IDLE -> 1f
        }
        val alpha = when (phase) {
            MicPhase.RECORDING -> WidgetMotion.recordingAlpha(frame, level)
            else -> 1f
        }
        views.setFloat(R.id.widget_mic_icon, "setScaleX", scale)
        views.setFloat(R.id.widget_mic_icon, "setScaleY", scale)
        views.setFloat(R.id.widget_mic_icon, "setAlpha", alpha)
    }

    fun rollingLines(text: String, maxLines: Int = 4, width: Int = 36): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.trim().split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            val extra = if (current.isEmpty()) word.length else word.length + 1
            if (current.isNotEmpty() && current.length + extra > width) {
                lines += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.takeLast(maxLines).let { tail ->
            List(maxLines - tail.size) { "" } + tail
        }
    }

    private fun micIntent(context: Context): PendingIntent {
        val intent = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_TOGGLE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutable()
        return PendingIntent.getForegroundService(context, 21, intent, flags)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(context, 22, intent, PendingIntent.FLAG_UPDATE_CURRENT or immutable())
    }

    private fun immutable(): Int = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
}
