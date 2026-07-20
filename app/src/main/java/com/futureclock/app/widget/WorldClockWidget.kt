package com.futureclock.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.data.tz.CityCatalog
import com.futureclock.app.notification.Actions
import com.futureclock.app.util.TimeFormat
import java.util.TimeZone

class WorldClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateOne(context, mgr, id) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        updateOne(context, mgr, id)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateScheduler.scheduleNext(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ids.forEach { id -> prefs.edit().remove("widget_$id").apply() }
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
        val opts = mgr.getAppWidgetOptions(id)
        val maxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
        val countCities = if (maxH >= 180) 3 else if (maxH >= 100) 2 else 1
        val layout = if (countCities >= 3) R.layout.widget_world_large else R.layout.widget_world_medium
        val views = RemoteViews(context.packageName, layout)

        val selected = loadCities(context, id).take(countCities)
        for (i in 0 until countCities) {
            val (cityView, timeView) = when (i) {
                0 -> R.id.widget_city_1 to R.id.widget_time_1
                1 -> R.id.widget_city_2 to R.id.widget_time_2
                else -> R.id.widget_city_3 to R.id.widget_time_3
            }
            if (i < selected.size) {
                val city = selected[i]
                val zone = TimeZone.getTimeZone(city.tzId)
                views.setTextViewText(cityView, "${city.flag} ${city.name}")
                views.setTextViewText(timeView, TimeFormat.formatTime(zone, use24h = true, showSeconds = false))
            } else {
                views.setTextViewText(cityView, "")
                views.setTextViewText(timeView, "-")
            }
        }

        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        mgr.updateAppWidget(id, views)
    }

    private fun loadCities(context: Context, id: Int): List<com.futureclock.app.data.tz.City> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString("widget_$id", null) ?: return defaultCities()
        return raw.split(SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { tzId -> CityCatalog.ALL.find { it.tzId == tzId } }
            .ifEmpty { defaultCities() }
    }

    private fun defaultCities(): List<com.futureclock.app.data.tz.City> {
        val defaults = listOf("America/New_York", "Europe/London", "Asia/Tokyo")
        return defaults.mapNotNull { tzId -> CityCatalog.ALL.find { it.tzId == tzId } }
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Actions.ACTION_OPEN_WORLD_TAB
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val PREFS = "world_widget_prefs"
        private const val SEPARATOR = "|"

        fun saveCities(context: Context, id: Int, tzIds: List<String>) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("widget_$id", tzIds.joinToString(SEPARATOR))
                .apply()
        }
    }
}
