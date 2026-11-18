package com.alekseevich47.notes.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

class NotesWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.refresh(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.refresh(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetUpdater.forget(appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetUpdater.onWidgetOptionsChanged(context)
    }
}

class NotesWidgetWide : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.refresh(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.refresh(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetUpdater.forget(appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetUpdater.onWidgetOptionsChanged(context)
    }
}
