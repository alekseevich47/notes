package com.alekseevich47.notes.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.alekseevich47.notes.R
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.service.RecordingService

object WidgetUpdater {
    data class WordsSnapshot(
        val text: String,
        val prompt: Boolean,
        val fadeFrame: Int,
    )

    private val gate = Any()
    private val bound = HashSet<Int>()
    private val dotFrames = intArrayOf(
        R.drawable.ic_widget_dots_0,
        R.drawable.ic_widget_dots_1,
        R.drawable.ic_widget_dots_2,
        R.drawable.ic_widget_dots_3,
        R.drawable.ic_widget_dots_4,
        R.drawable.ic_widget_dots_5,
        R.drawable.ic_widget_dots_6,
        R.drawable.ic_widget_dots_7,
        R.drawable.ic_widget_dots_8,
        R.drawable.ic_widget_dots_9,
        R.drawable.ic_widget_dots_10,
        R.drawable.ic_widget_dots_11,
        R.drawable.ic_widget_dots_12,
        R.drawable.ic_widget_dots_13,
        R.drawable.ic_widget_dots_14,
        R.drawable.ic_widget_dots_15,
        R.drawable.ic_widget_dots_16,
        R.drawable.ic_widget_dots_17,
        R.drawable.ic_widget_dots_18,
        R.drawable.ic_widget_dots_19,
        R.drawable.ic_widget_dots_20,
        R.drawable.ic_widget_dots_21,
        R.drawable.ic_widget_dots_22,
        R.drawable.ic_widget_dots_23,
    )

    private var phase: MicPhase = MicPhase.IDLE
    private var level: Float = 0f
    private var transcript: String = ""
    private var frame: Int = 0
    private var fadeFrame: Int = WidgetMotion.FADE_FRAMES
    private var widePartials: Int = 0

    fun snapshot(): WordsSnapshot = synchronized(gate) {
        WordsSnapshot(
            text = transcript,
            prompt = phase == MicPhase.IDLE || phase == MicPhase.DONE,
            fadeFrame = fadeFrame,
        )
    }

    fun push(context: Context, phase: MicPhase, level: Float, transcript: String) {
        val notify = synchronized(gate) {
            val enteringProcessing = phase == MicPhase.PROCESSING && this.phase != MicPhase.PROCESSING
            val phaseChanged = this.phase != phase
            val displayChanged = displayChanged(this.phase, this.transcript, phase, transcript)
            if (enteringProcessing) frame = 0
            this.phase = phase
            this.transcript = transcript
            this.level = if (phase == MicPhase.RECORDING) {
                WidgetMotion.easeLevel(this.level, level)
            } else {
                0f
            }
            if (displayChanged) fadeFrame = 0
            if (phaseChanged || displayChanged) render(context, full = phaseChanged)
            displayChanged || phaseChanged
        }
        if (notify) notifyWords(context)
    }

    /**
     * High-rate tick from [RecordingService]. Recording eases the mic zoom and
     * glow toward the latest audio level (~64ms). Processing advances the dot wave.
     */
    fun pulse(context: Context, rawLevel: Float) {
        val notify = synchronized(gate) {
            val paintMic = when (phase) {
                MicPhase.RECORDING -> {
                    level = WidgetMotion.easeLevel(level, rawLevel)
                    true
                }
                MicPhase.PROCESSING -> {
                    frame = (frame + 1) % WidgetMotion.DOT_FRAMES
                    true
                }
                MicPhase.DONE -> false
                MicPhase.IDLE -> return@synchronized false
            }
            val fading = fadeFrame < WidgetMotion.FADE_FRAMES
            if (fading) fadeFrame += 1
            if (paintMic) render(context, full = false)
            fading
        }
        if (notify) notifyWords(context)
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        synchronized(gate) { render(app, full = true) }
        notifyWords(app)
    }

    fun onWidgetOptionsChanged(context: Context) {
        notifyWords(context.applicationContext)
    }

    fun forget(ids: IntArray) {
        synchronized(gate) { ids.forEach { bound.remove(it) } }
    }

    private fun displayChanged(
        previousPhase: MicPhase,
        previousText: String,
        nextPhase: MicPhase,
        nextText: String,
    ): Boolean {
        val wasPrompt = previousPhase == MicPhase.IDLE || previousPhase == MicPhase.DONE
        val nowPrompt = nextPhase == MicPhase.IDLE || nextPhase == MicPhase.DONE
        if (wasPrompt != nowPrompt) return true
        if (nowPrompt) return false
        return previousText.trim() != nextText.trim()
    }

    private fun render(context: Context, full: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val smallIds = manager.getAppWidgetIds(ComponentName(context, NotesWidgetSmall::class.java))
        val wideIds = manager.getAppWidgetIds(ComponentName(context, NotesWidgetWide::class.java))
        smallIds.forEach { id ->
            manager.updateAppWidget(id, smallViews(context))
            bound += id
        }
        var flushed = full
        wideIds.forEach { id ->
            val force = full || id !in bound || widePartials >= WIDE_FLUSH
            if (force) {
                manager.updateAppWidget(id, wideViews(context, id))
                bound += id
                flushed = true
            } else {
                manager.partiallyUpdateAppWidget(id, wideMic(context))
            }
        }
        widePartials = if (flushed || wideIds.isEmpty()) 0 else widePartials + 1
    }

    private fun notifyWords(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NotesWidgetWide::class.java))
        ids.forEach { id -> manager.notifyAppWidgetViewDataChanged(id, R.id.widget_words) }
    }

    private fun smallViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_2x2)
        applyMic(views)
        val click = micIntent(context)
        bindMicClick(views, click)
        views.setOnClickPendingIntent(R.id.widget_root, click)
        return views
    }

    private fun wideViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_4x2)
        applyMic(views)
        val intent = Intent(context, WidgetWordsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_words, intent)
        bindMicClick(views, micIntent(context))
        return views
    }

    private fun bindMicClick(views: RemoteViews, click: PendingIntent) {
        views.setOnClickPendingIntent(R.id.widget_mic, click)
        views.setOnClickPendingIntent(R.id.widget_mic_plate, click)
        views.setOnClickPendingIntent(R.id.widget_mic_glow, click)
        views.setOnClickPendingIntent(R.id.widget_mic_icon, click)
        views.setOnClickPendingIntent(R.id.widget_mic_done, click)
    }

    private fun wideMic(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_4x2)
        applyMic(views)
        return views
    }

    private fun applyMic(views: RemoteViews) {
        val recording = phase == MicPhase.RECORDING
        val processing = phase == MicPhase.PROCESSING
        val done = phase == MicPhase.DONE
        views.setInt(
            R.id.widget_mic_plate,
            "setBackgroundResource",
            when {
                done -> R.drawable.widget_mic_plate_done
                recording || processing -> R.drawable.widget_mic_plate_rec
                else -> R.drawable.widget_mic_plate
            },
        )
        val icon = when {
            processing -> dotFrames[WidgetMotion.dotFrame(frame)]
            recording -> R.drawable.ic_widget_mic_rec
            else -> R.drawable.ic_widget_mic
        }
        views.setImageViewResource(R.id.widget_mic_icon, icon)
        views.setViewVisibility(R.id.widget_mic_icon, if (done) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_mic_done, if (done) View.VISIBLE else View.GONE)
        val scale = if (recording) WidgetMotion.recordingScale(level) else 1f
        views.setFloat(R.id.widget_mic_plate, "setScaleX", scale)
        views.setFloat(R.id.widget_mic_plate, "setScaleY", scale)
        val glow = if (recording) WidgetMotion.glowAlpha(level) else 0f
        val glowScale = if (recording) WidgetMotion.glowScale(level) else 1f
        views.setFloat(R.id.widget_mic_glow, "setAlpha", glow)
        views.setFloat(R.id.widget_mic_glow, "setScaleX", glowScale)
        views.setFloat(R.id.widget_mic_glow, "setScaleY", glowScale)
        views.setFloat(R.id.widget_mic_icon, "setAlpha", 1f)
        views.setFloat(R.id.widget_mic_icon, "setScaleX", 1f)
        views.setFloat(R.id.widget_mic_icon, "setScaleY", 1f)
    }

    private fun micIntent(context: Context): PendingIntent {
        val intent = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_TOGGLE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutable()
        return PendingIntent.getForegroundService(context, 21, intent, flags)
    }

    private fun immutable(): Int = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

    private const val WIDE_FLUSH = 400
}
