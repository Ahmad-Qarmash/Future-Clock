package com.futureclock.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.FutureClockApp
import com.futureclock.app.notification.Actions
import com.futureclock.app.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class DigitalClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        updateAll(context, mgr, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        updateAll(context, mgr, intArrayOf(id))
    }

    override fun onEnabled(context: Context) { WidgetUpdateScheduler.scheduleNext(context) }

    private fun updateAll(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val use24h = (context.applicationContext as FutureClockApp).settings.use24h.first()
                ids.forEach { id -> updateOne(context, mgr, id, use24h) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int, use24h: Boolean) {
        val opts = mgr.getAppWidgetOptions(id)
        val maxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 100)
        val layout = if (maxH >= 100) R.layout.widget_digital_medium else R.layout.widget_digital_small
        val views = RemoteViews(context.packageName, layout)

        val cal = Calendar.getInstance()
        val zone = java.util.TimeZone.getDefault()
        if (layout == R.layout.widget_digital_medium) {
            views.setTextViewText(R.id.widget_time, TimeFormat.formatTime(zone, use24h, true))
            views.setTextViewText(R.id.widget_date, TimeFormat.formatDate(zone))
        } else {
            val t = TimeFormat.formatTime(zone, use24h, false)
            views.setTextViewText(R.id.widget_time, t)
            val sec = String.format("%02d", cal.get(Calendar.SECOND))
            views.setTextViewText(R.id.widget_seconds, ":$sec")
            views.setTextViewText(R.id.widget_day, TimeFormat.formatDay(zone).take(3))
            views.setTextViewText(R.id.widget_date, TimeFormat.formatShortDate(zone))
        }

        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        mgr.updateAppWidget(id, views)
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Actions.ACTION_OPEN_CLOCK_TAB
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
