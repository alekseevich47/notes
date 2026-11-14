package com.alekseevich47.notes.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.Html
import android.widget.RemoteViews
import com.alekseevich47.notes.R
import com.alekseevich47.notes.domain.RecordingUiState
import com.alekseevich47.notes.service.RecordingService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun publish(state: RecordingUiState) {
        val manager = AppWidgetManager.getInstance(context)
        updateAll(manager, ComponentName(context, NotesWidgetReceiver::class.java), state, wide = false)
        updateAll(manager, ComponentName(context, NotesWideWidgetReceiver::class.java), state, wide = true)
    }

    private fun updateAll(
        manager: AppWidgetManager,
        component: ComponentName,
        state: RecordingUiState,
        wide: Boolean,
    ) {
        val ids = manager.getAppWidgetIds(component)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, if (wide) R.layout.widget_4x2 else R.layout.widget_2x2)
            bind(views, state, wide)
            manager.updateAppWidget(id, views)
        }
    }

    fun bind(views: RemoteViews, state: RecordingUiState, wide: Boolean) {
        val icon = when {
            state.processing -> R.drawable.ic_processing_anim
            state.done -> R.drawable.ic_check_circle
            state.active -> R.drawable.ic_mic_recording
            else -> R.drawable.ic_mic_outline
        }
        views.setImageViewResource(R.id.widget_mic, icon)
        val toggle = PendingIntent.getService(
            context,
            if (wide) 2 else 1,
            Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_mic, toggle)
        views.setOnClickPendingIntent(R.id.widget_root, toggle)
        if (wide) {
            val lines = state.partialText.lines().filter { it.isNotBlank() }.takeLast(5)
            val html = lines.mapIndexed { index, line ->
                val alpha = ((0.35f + 0.65f * (index + 1) / lines.size.coerceAtLeast(1)) * 255).toInt()
                val color = String.format("#%02XFFFFFF", alpha)
                "<font color='$color'>${Html.escapeHtml(line)}</font>"
            }.joinToString("<br/>")
            views.setTextViewText(
                R.id.widget_transcript,
                Html.fromHtml(html.ifBlank { "<font color='#66FFFFFF'>Нажмите микрофон</font>" }, Html.FROM_HTML_MODE_LEGACY),
            )
        }
    }
}
