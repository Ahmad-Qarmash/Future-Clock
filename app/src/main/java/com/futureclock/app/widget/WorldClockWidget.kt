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
import com.futureclock.app.data.tz.City
import com.futureclock.app.notification.Actions
import com.futureclock.app.util.TimeFormat
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WorldClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        updateAll(context, mgr, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        id: Int,
        newOptions: android.os.Bundle
    ) {
        updateAll(context, mgr, intArrayOf(id))
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateScheduler.scheduleNext(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ids.forEach { id -> prefs.edit().remove("widget_$id").apply() }
    }

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
        val maxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
        val countCities = if (maxH >= 180) 3 else if (maxH >= 100) 2 else 1
        val layout = if (countCities >= 3) R.layout.widget_world_large else R.layout.widget_world_medium
        val views = RemoteViews(context.packageName, layout)

        val selected = loadCities(context, id).take(countCities)
        val validTimeZones = TimeZone.getAvailableIDs().toHashSet()
        for (i in 0 until countCities) {
            val (cityView, timeView) = when (i) {
                0 -> R.id.widget_city_1 to R.id.widget_time_1
                1 -> R.id.widget_city_2 to R.id.widget_time_2
                else -> R.id.widget_city_3 to R.id.widget_time_3
            }
            if (i < selected.size) {
                val city = selected[i]
                val time = if (city.tzId in validTimeZones) {
                    TimeFormat.formatTime(
                        TimeZone.getTimeZone(city.tzId),
                        use24h = use24h,
                        showSeconds = false
                    )
                } else {
                    context.getString(R.string.widget_time_unavailable)
                }
                views.setTextViewText(cityView, "${city.flag} ${city.name}")
                views.setTextViewText(timeView, time)
            } else {
                views.setTextViewText(cityView, "")
                views.setTextViewText(timeView, "-")
            }
        }

        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        mgr.updateAppWidget(id, views)
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Actions.ACTION_OPEN_WORLD_TAB
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val PREFS = "world_widget_prefs"
        private const val SEPARATOR = "|"

        fun saveCities(context: Context, id: Int, cities: List<City>) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("widget_$id", encodeCities(cities))
                .apply()
        }

        internal fun encodeCities(cities: List<City>): String {
            val array = JSONArray()
            cities.take(3).forEach { city ->
                array.put(
                    JSONObject()
                        .put("id", city.id)
                        .put("name", city.name)
                        .put("country", city.country)
                        .put("countryCode", city.countryCode)
                        .put("flag", city.flag)
                        .put("admin1", city.admin1)
                        .put("tzId", city.tzId)
                        .put("population", city.population)
                )
            }
            return array.toString()
        }

        fun loadCities(context: Context, id: Int): List<City> {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("widget_$id", null)
                ?: return defaultCities()
            if (!raw.trimStart().startsWith("[")) {
                return raw.split(SEPARATOR)
                    .filter { it.isNotBlank() }
                    .map(::legacyCity)
                    .ifEmpty(::defaultCities)
            }
            return decodeCities(raw).getOrDefault(defaultCities()).ifEmpty(::defaultCities)
        }

        internal fun decodeCities(raw: String): Result<List<City>> = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        City(
                            id = item.getLong("id"),
                            name = item.getString("name"),
                            country = item.optString("country"),
                            countryCode = item.optString("countryCode"),
                            flag = item.optString("flag", "🌐"),
                            admin1 = item.optString("admin1"),
                            tzId = item.getString("tzId"),
                            population = item.optLong("population")
                        )
                    )
                }
            }
        }

        private fun legacyCity(tzId: String): City = City(
            id = -(tzId.hashCode().toLong().absoluteValue + 10_000L),
            name = tzId.substringAfterLast('/').replace('_', ' '),
            country = tzId.substringBefore('/').replace('_', ' '),
            countryCode = "",
            flag = "🌐",
            admin1 = "",
            tzId = tzId,
            population = 0
        )

        private fun defaultCities(): List<City> = listOf(
            City(-1, "New York", "United States", "US", "🇺🇸", "New York", "America/New_York", 0),
            City(-2, "London", "United Kingdom", "GB", "🇬🇧", "England", "Europe/London", 0),
            City(-3, "Tokyo", "Japan", "JP", "🇯🇵", "Tokyo", "Asia/Tokyo", 0)
        )
    }
}
