package com.alekseevich47.notes.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.alekseevich47.notes.MainActivity
import com.alekseevich47.notes.R
import com.alekseevich47.notes.domain.MicPhase
import com.alekseevich47.notes.service.RecordingService
import java.io.File

object WidgetUpdater {
    private data class TextFade(
        var base: String,
        var incoming: String,
        var frame: Int,
    )

    private data class Prefs(
        val recognizer: String,
        val warmupOnBoot: Boolean,
        val modelLoading: Boolean,
    )

    private val gate = Any()
    private val bound = HashSet<Int>()
    private val fades = HashMap<Int, TextFade>()
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

    private val squareIds = intArrayOf(
        R.id.widget_mic,
        R.id.widget_mic_glow,
        R.id.widget_mic_plate,
        R.id.widget_mic_icon,
        R.id.widget_mic_done,
    )

    private var phase: MicPhase = MicPhase.IDLE
    private var level: Float = 0f
    private var transcript: String = ""
    private var frame: Int = 0
    private var modelLoadingFlag: Boolean = false
    private var widePartials: Int = 0
    private var prefsCache = Prefs(recognizer = "tone", warmupOnBoot = false, modelLoading = false)
    private var prefsAt: Long = 0L

    fun push(
        context: Context,
        phase: MicPhase,
        level: Float,
        transcript: String,
        modelLoading: Boolean? = null,
    ) {
        synchronized(gate) {
            val enteringProcessing = phase == MicPhase.PROCESSING && this.phase != MicPhase.PROCESSING
            val phaseChanged = this.phase != phase
            if (enteringProcessing) frame = 0
            if (modelLoading != null) modelLoadingFlag = modelLoading
            this.phase = phase
            this.transcript = transcript
            this.level = if (phase == MicPhase.RECORDING) {
                WidgetMotion.easeLevel(this.level, level)
            } else {
                0f
            }
            render(context, full = phaseChanged, refreshPrefs = phaseChanged)
        }
    }

    /** Warmup or a settings screen can publish that the speech model is still opening. */
    fun setModelLoading(context: Context, loading: Boolean) {
        synchronized(gate) {
            if (modelLoadingFlag == loading) return
            modelLoadingFlag = loading
            render(context, full = true, refreshPrefs = true)
        }
    }

    /**
     * High-rate tick from [RecordingService]. Recording eases the mic zoom and
     * glow toward the latest audio level. Processing steps the dot wave every
     * [WidgetMotion.DOT_FRAME_MS].
     */
    fun pulse(context: Context, rawLevel: Float) {
        synchronized(gate) {
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
                MicPhase.IDLE -> return
            }
            val fading = advanceFades()
            if (paintMic || fading) render(context, full = false, refreshPrefs = false)
        }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        synchronized(gate) { render(app, full = true, refreshPrefs = true) }
    }

    fun onWidgetOptionsChanged(context: Context) {
        refresh(context.applicationContext)
    }

    fun forget(ids: IntArray) {
        synchronized(gate) {
            ids.forEach {
                bound.remove(it)
                fades.remove(it)
            }
        }
    }

    private fun advanceFades(): Boolean {
        var active = false
        for (fade in fades.values) {
            if (fade.frame < WidgetMotion.FADE_FRAMES) {
                fade.frame += 1
                active = true
            }
        }
        return active
    }

    private fun render(context: Context, full: Boolean, refreshPrefs: Boolean) {
        val prefs = prefs(context, refreshPrefs)
        val loading = modelLoadingFlag || prefs.modelLoading
        val live = WidgetMotion.liveTextEnabled(prefs.recognizer, prefs.warmupOnBoot)
        val manager = AppWidgetManager.getInstance(context)
        val smallIds = manager.getAppWidgetIds(ComponentName(context, NotesWidgetSmall::class.java))
        val wideIds = manager.getAppWidgetIds(ComponentName(context, NotesWidgetWide::class.java))
        smallIds.forEach { id ->
            manager.updateAppWidget(id, smallViews(context, id, loading))
            bound += id
        }
        var flushed = full
        wideIds.forEach { id ->
            val force = full || id !in bound || widePartials >= WIDE_FLUSH
            val views = wideViews(context, id, live = live, bindClicks = force)
            if (force) {
                manager.updateAppWidget(id, views)
                bound += id
                flushed = true
            } else {
                manager.partiallyUpdateAppWidget(id, views)
            }
        }
        fades.keys.retainAll(wideIds.toSet())
        widePartials = if (flushed || wideIds.isEmpty()) 0 else widePartials + 1
    }

    private fun smallViews(context: Context, widgetId: Int, loading: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_2x2)
        val (width, height) = widgetSizeDp(context, options(context, widgetId))
        val label = WidgetMotion.statusLabel(phase, loading)
        val side = WidgetMotion.smallMicSideDp(width, height, if (label == null) 0 else STATUS_DP)
        applyMic(context, views, side, SMALL_ICON_INSET)
        if (label == null) {
            views.setViewVisibility(R.id.widget_status, View.GONE)
            views.setTextViewText(R.id.widget_status, "")
        } else {
            views.setViewVisibility(R.id.widget_status, View.VISIBLE)
            views.setTextViewText(R.id.widget_status, label)
        }
        val click = micIntent(context)
        bindMicClick(views, click)
        views.setOnClickPendingIntent(R.id.widget_root, click)
        return views
    }

    private fun wideViews(
        context: Context,
        widgetId: Int,
        live: Boolean,
        bindClicks: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_4x2)
        val (width, height) = widgetSizeDp(context, options(context, widgetId))
        val side = WidgetMotion.wideMicSideDp(width, height)
        applyMic(context, views, side, WIDE_ICON_INSET)
        applyWideText(context, views, widgetId, width, live)
        if (bindClicks) {
            bindMicClick(views, micIntent(context))
            val open = openAppIntent(context, recording = phase == MicPhase.RECORDING)
            views.setOnClickPendingIntent(R.id.widget_text_block, open)
            views.setOnClickPendingIntent(R.id.widget_words, open)
            views.setOnClickPendingIntent(R.id.widget_words_out, open)
        }
        return views
    }

    private fun applyWideText(
        context: Context,
        views: RemoteViews,
        widgetId: Int,
        widthDp: Int,
        live: Boolean,
    ) {
        val width = if (widthDp > 0) widthDp else 250
        val next = WidgetMotion.displayText(phase, transcript, width, live)
        val animate = phase == MicPhase.RECORDING || phase == MicPhase.PROCESSING
        val fade = fades[widgetId] ?: TextFade(next, next, WidgetMotion.FADE_FRAMES).also { fades[widgetId] = it }
        if (fade.frame >= WidgetMotion.FADE_FRAMES && fade.base != fade.incoming) {
            fade.base = fade.incoming
        }
        if (fade.incoming != next) {
            if (!animate) {
                fade.base = next
                fade.incoming = next
                fade.frame = WidgetMotion.FADE_FRAMES
            } else if (next.startsWith(fade.incoming) || next.startsWith(fade.base)) {
                // Keep fading. An append must not rewind to an older base, and a
                // prefix of what is already on screen must not blank the block.
                fade.incoming = next
                if (fade.frame >= WidgetMotion.FADE_FRAMES) fade.frame = 0
            } else {
                fade.base = fade.incoming
                fade.incoming = next
                fade.frame = 0
            }
        }
        val paint = WidgetMotion.blockPaint(fade.base, fade.incoming, fade.frame)
        val color = context.getColor(
            if (WidgetMotion.textMuted(phase, live)) R.color.widget_muted else R.color.widget_text,
        )
        val wholeFade = paint.outgoing.isNotEmpty()
        views.setTextColor(R.id.widget_words, color)
        views.setTextViewText(R.id.widget_words, incomingText(paint, color))
        views.setFloat(R.id.widget_words, "setAlpha", if (wholeFade) paint.suffixAlpha else 1f)
        val showOut = wholeFade && paint.outgoingAlpha > 0.01f
        views.setViewVisibility(R.id.widget_words_out, if (showOut) View.VISIBLE else View.GONE)
        if (showOut) {
            views.setTextColor(R.id.widget_words_out, color)
            views.setTextViewText(R.id.widget_words_out, paint.outgoing)
            views.setFloat(R.id.widget_words_out, "setAlpha", paint.outgoingAlpha)
        }
    }

    private fun incomingText(paint: WidgetMotion.BlockPaint, color: Int): CharSequence {
        val text = paint.incoming
        if (paint.outgoing.isNotEmpty() || paint.solidUntil >= text.length || paint.suffixAlpha >= 0.99f) {
            return text
        }
        val start = paint.solidUntil.coerceIn(0, text.length)
        if (start >= text.length) return text
        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(WidgetMotion.colorWithAlpha(color, paint.suffixAlpha)),
                start,
                text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun bindMicClick(views: RemoteViews, click: PendingIntent) {
        views.setOnClickPendingIntent(R.id.widget_mic, click)
        views.setOnClickPendingIntent(R.id.widget_mic_plate, click)
        views.setOnClickPendingIntent(R.id.widget_mic_glow, click)
        views.setOnClickPendingIntent(R.id.widget_mic_icon, click)
        views.setOnClickPendingIntent(R.id.widget_mic_done, click)
    }

    private fun applyMic(context: Context, views: RemoteViews, sideDp: Int, iconInset: Float) {
        val recording = phase == MicPhase.RECORDING
        val processing = phase == MicPhase.PROCESSING
        val done = phase == MicPhase.DONE
        forceSquare(views, sideDp)
        insetIcon(context, views, sideDp, iconInset)
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

    private fun forceSquare(views: RemoteViews, sideDp: Int) {
        if (sideDp <= 0 || Build.VERSION.SDK_INT < 31) return
        val side = sideDp.toFloat()
        for (id in squareIds) {
            views.setViewLayoutWidth(id, side, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(id, side, TypedValue.COMPLEX_UNIT_DIP)
        }
    }

    private fun insetIcon(context: Context, views: RemoteViews, sideDp: Int, fraction: Float) {
        if (sideDp <= 0) return
        val px = (sideDp * context.resources.displayMetrics.density * fraction).toInt().coerceAtLeast(0)
        views.setViewPadding(R.id.widget_mic_icon, px, px, px, px)
    }

    private fun prefs(context: Context, force: Boolean): Prefs {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - prefsAt < PREFS_TTL_MS) return prefsCache
        prefsCache = readPrefs(context)
        prefsAt = now
        return prefsCache
    }

    private fun readPrefs(context: Context): Prefs {
        val stored = runCatching {
            context.getSharedPreferences(WidgetMotion.PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        val recognizer = stored?.getString(WidgetMotion.PREF_RECOGNIZER, null)
        val warmup = if (stored?.contains(WidgetMotion.PREF_WARMUP) == true) {
            stored.getBoolean(WidgetMotion.PREF_WARMUP, false)
        } else {
            false
        }
        val flag = stored?.getBoolean(WidgetMotion.PREF_MODEL_LOADING, false) == true
        val statuses = modelStatusLines(context)
        return Prefs(
            recognizer = recognizer?.trim()?.takeIf { it.isNotEmpty() } ?: "tone",
            warmupOnBoot = warmup,
            modelLoading = WidgetMotion.modelLoading(flag, statuses),
        )
    }

    private fun modelStatusLines(context: Context): List<String> {
        val root = File(context.filesDir, "models")
        if (!root.isDirectory) return emptyList()
        return listOf("t-one.status", "gigaam.status").map { name ->
            val file = File(root, name)
            if (!file.isFile) return@map ""
            runCatching { file.bufferedReader().use { it.readLine() }.orEmpty() }.getOrDefault("")
        }
    }

    private fun options(context: Context, widgetId: Int): Bundle {
        return runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        }.getOrDefault(Bundle())
    }

    private fun widgetSizeDp(context: Context, options: Bundle): Pair<Int, Int> {
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val width = if (portrait) minW.takeIf { it > 0 } ?: maxW else maxW.takeIf { it > 0 } ?: minW
        val height = if (portrait) maxH.takeIf { it > 0 } ?: minH else minH.takeIf { it > 0 } ?: maxH
        return width to height
    }

    private fun micIntent(context: Context): PendingIntent {
        val intent = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_TOGGLE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutable()
        return PendingIntent.getForegroundService(context, 21, intent, flags)
    }

    private fun openAppIntent(context: Context, recording: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (recording) putExtra(MainActivity.EXTRA_OPEN_RECORDING, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutable()
        return PendingIntent.getActivity(context, if (recording) 34 else 33, intent, flags)
    }

    private fun immutable(): Int = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

    private const val WIDE_FLUSH = 400
    private const val PREFS_TTL_MS = 1_000L
    private const val STATUS_DP = 16
    private const val SMALL_ICON_INSET = 0.16f
    private const val WIDE_ICON_INSET = 0.11f
}
