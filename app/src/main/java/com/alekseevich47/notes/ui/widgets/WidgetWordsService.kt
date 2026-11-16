package com.alekseevich47.notes.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.alekseevich47.notes.R

class WidgetWordsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return WidgetWordsFactory(applicationContext, widgetId)
    }
}

private class WidgetWordsFactory(
    private val context: Context,
    private val widgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private var lines: List<String> = listOf(WidgetMotion.SAVED_NOTE_PROMPT)
    private var prompt: Boolean = true
    private var fadeFrame: Int = WidgetMotion.FADE_FRAMES

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val snap = WidgetUpdater.snapshot()
        val width = AppWidgetManager.getInstance(context)
            .getAppWidgetOptions(widgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            .takeIf { it > 0 } ?: 250
        prompt = snap.prompt
        fadeFrame = snap.fadeFrame
        lines = if (snap.prompt) {
            listOf(WidgetMotion.SAVED_NOTE_PROMPT)
        } else {
            WidgetMotion.wrapWords(snap.text, WidgetMotion.charsForWidth(width))
        }
    }

    override fun onDestroy() {
        lines = emptyList()
    }

    override fun getCount(): Int = lines.size

    override fun getViewTypeCount(): Int = 2

    override fun getViewAt(position: Int): RemoteViews {
        val promptRow = prompt
        val layout = if (promptRow) R.layout.widget_prompt_line else R.layout.widget_word_line
        val views = RemoteViews(context.packageName, layout)
        val value = if (promptRow) {
            WidgetMotion.SAVED_NOTE_PROMPT
        } else {
            lines.getOrNull(position).orEmpty()
        }
        views.setTextViewText(R.id.word_line, value)
        val newest = position == lines.lastIndex
        val alpha = if (newest) WidgetMotion.wordFadeAlpha(fadeFrame) else 1f
        val color = ContextCompat.getColor(
            context,
            if (promptRow) R.color.widget_muted else R.color.widget_text,
        )
        views.setTextColor(R.id.word_line, WidgetMotion.colorWithAlpha(color, alpha))
        val slideDp = if (newest) WidgetMotion.wordSlideDp(fadeFrame) else 0f
        views.setFloat(R.id.word_line, "setTranslationY", slideDp * context.resources.displayMetrics.density)
        return views
    }

    override fun getLoadingView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_word_line)
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
