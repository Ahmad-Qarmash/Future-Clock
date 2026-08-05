package com.futureclock.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.futureclock.app.FutureClockApp
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.notification.Actions
import com.futureclock.app.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.TimeZone

class DigitalClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidgets(context, manager, intArrayOf(id))
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateScheduler.scheduleNext(context)
    }

    private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FutureClockApp
                val use24h = runCatching { app.settings.use24h.first() }.getOrDefault(true)
                ids.forEach { id -> render(context, manager, id, use24h) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int, use24h: Boolean) {
        val options = manager.getAppWidgetOptions(id)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 100)
        val layout = if (maxHeight >= 100) {
            R.layout.widget_digital_medium
        } else {
            R.layout.widget_digital_small
        }
        val views = RemoteViews(context.packageName, layout)
        val zone = TimeZone.getDefault()
        val pattern = if (use24h) "HH:mm" else "h:mm a"

        views.setString(R.id.widget_time, "setTimeZone", zone.id)
        views.setCharSequence(R.id.widget_time, "setFormat12Hour", pattern)
        views.setCharSequence(R.id.widget_time, "setFormat24Hour", pattern)
        views.setTextViewText(R.id.widget_date, TimeFormat.formatDate(zone))
        if (layout == R.layout.widget_digital_small) {
            views.setTextViewText(R.id.widget_day, TimeFormat.formatDay(zone).take(3))
            views.setTextViewText(R.id.widget_date, TimeFormat.formatShortDate(zone))
        }

        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        manager.updateAppWidget(id, views)
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Actions.ACTION_OPEN_CLOCK_TAB
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
