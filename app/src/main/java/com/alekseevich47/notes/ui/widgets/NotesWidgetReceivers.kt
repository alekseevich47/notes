package com.alekseevich47.notes.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.alekseevich47.notes.R
import com.alekseevich47.notes.domain.RecordingUiState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class NotesWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val updater = entryPoint(context).widgetUpdater()
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_2x2)
            updater.bind(views, RecordingUiState(), wide = false)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

class NotesWideWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val updater = entryPoint(context).widgetUpdater()
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_4x2)
            updater.bind(views, RecordingUiState(), wide = true)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetUpdater(): WidgetUpdater
}

private fun entryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
