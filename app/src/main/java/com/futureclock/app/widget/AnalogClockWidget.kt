package com.futureclock.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.notification.Actions
import com.futureclock.app.ui.views.ClockRenderer
import com.futureclock.app.util.TimeFormat
import java.util.Calendar

class AnalogClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateOne(context, mgr, id) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        updateOne(context, mgr, id)
    }

    override fun onEnabled(context: Context) { WidgetUpdateScheduler.scheduleNext(context) }
    override fun onDisabled(context: Context) { /* keep ticking in case other widgets are alive */ }

    private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
        val opts = mgr.getAppWidgetOptions(id)
        val maxW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 200)
        val maxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 200)

        val layout = when {
            maxH >= 220 -> R.layout.widget_analog_large
            maxH >= 130 -> R.layout.widget_analog_medium
            else -> R.layout.widget_analog_small
        }
        val views = RemoteViews(context.packageName, layout)

        // Render analog bitmap
        // RemoteViews sends this bitmap through Binder. Large launcher cells on high-density
        // screens can otherwise exceed the transaction limit and leave a blank 3x3 widget.
        val size = maxW.coerceAtMost(maxH).dp(context).coerceIn(MIN_RENDER_PX, MAX_RENDER_PX)
        val bmp: Bitmap = ClockRenderer.renderBitmap(size, size, Calendar.getInstance())
        views.setImageViewBitmap(R.id.analog_clock, bmp)

        if (layout == R.layout.widget_analog_medium || layout == R.layout.widget_analog_large) {
            val zone = java.util.TimeZone.getDefault()
            views.setTextViewText(R.id.widget_date, TimeFormat.formatShortDate(zone))
            if (layout == R.layout.widget_analog_large) {
                views.setTextViewText(R.id.widget_day, TimeFormat.formatDay(zone))
            }
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

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val MIN_RENDER_PX = 160
        private const val MAX_RENDER_PX = 384
    }
}
