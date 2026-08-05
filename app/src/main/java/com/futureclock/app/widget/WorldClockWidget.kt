package com.futureclock.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.data.db.AlarmEntity
import com.futureclock.app.data.db.WorldCityEntity
import com.futureclock.app.data.tz.City
import com.futureclock.app.notification.Actions
import com.futureclock.app.util.AlarmMath
import com.futureclock.app.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone
import kotlin.math.absoluteValue

class WorldClockWidget : AppWidgetProvider() {

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

    override fun onDeleted(context: Context, ids: IntArray) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { id -> editor.remove("widget_$id") }
        editor.apply()
    }

    private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = WidgetDataSource.load(context)
                ids.forEach { id -> renderWidget(context, manager, id, snapshot) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun renderWidget(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        snapshot: WidgetSnapshot
    ) {
        val options = manager.getAppWidgetOptions(id)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250)
        val layout = when {
            maxWidth >= 180 && maxHeight >= 180 -> R.layout.widget_world_large
            maxHeight >= 135 -> R.layout.widget_world_medium
            else -> R.layout.widget_world_small
        }
        val visibleCityCount = when (layout) {
            R.layout.widget_world_large -> 3
            R.layout.widget_world_medium -> 2
            else -> 1
        }
        val views = RemoteViews(context.packageName, layout)
        val configuredCities = loadConfiguredCities(context, id)
            ?.takeIf { it.isNotEmpty() }
            ?.mapIndexed { index, city -> city.toEntity(index) }
        val cities = configuredCities
            ?: snapshot.cities.ifEmpty {
                defaultCities().mapIndexed { index, city -> city.toEntity(index) }
            }

        views.setTextViewText(
            R.id.widget_city_count,
            context.resources.getQuantityString(
                R.plurals.widget_city_count,
                cities.size,
                cities.size
            )
        )

        val rowIds = intArrayOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)
        for (index in 0 until visibleCityCount) {
            val city = cities.getOrNull(index)
            views.setViewVisibility(rowIds[index], if (city == null) View.GONE else View.VISIBLE)
            if (city != null) renderCity(context, views, index, city, snapshot.use24h)
        }
        renderNextAlarm(context, views, snapshot.nextAlarm, snapshot.use24h)

        views.setOnClickPendingIntent(R.id.widget_root, openWorld(context))
        views.setOnClickPendingIntent(R.id.widget_alarm_root, openAlarms(context))
        manager.updateAppWidget(id, views)
    }

    private fun renderCity(
        context: Context,
        views: RemoteViews,
        index: Int,
        city: WorldCityEntity,
        use24h: Boolean
    ) {
        val cityIds = intArrayOf(R.id.widget_city_1, R.id.widget_city_2, R.id.widget_city_3)
        val metaIds = intArrayOf(R.id.widget_meta_1, R.id.widget_meta_2, R.id.widget_meta_3)
        val timeIds = intArrayOf(R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3)
        val zone = safeTimeZone(city.tzId)
        val cityLabel = listOf(city.flag, city.displayName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val meta = listOf(city.country, dayRelation(context, zone))
            .filter { it.isNotBlank() }
            .joinToString(" \u00B7 ")

        views.setTextViewText(cityIds[index], cityLabel)
        views.setTextViewText(metaIds[index], meta)
        configureTextClock(views, timeIds[index], zone.id, use24h)
    }

    private fun renderNextAlarm(
        context: Context,
        views: RemoteViews,
        next: WidgetAlarm?,
        use24h: Boolean
    ) {
        if (next == null) {
            views.setTextViewText(R.id.widget_alarm_time, context.getString(R.string.widget_no_alarm_short))
            views.setTextViewText(R.id.widget_alarm_subtitle, context.getString(R.string.widget_create_alarm))
            return
        }

        val alarm = next.alarm
        val zone = AlarmMath.timeZone(alarm.timeZoneId)
        val countdown = WidgetDataSource.formatCountdown(
            context,
            next.triggerAtMillis,
            System.currentTimeMillis()
        )
        val subtitle = listOf(alarm.label, alarmPlace(alarm, zone.id), countdown)
            .filter { it.isNotBlank() }
            .joinToString(" \u00B7 ")
        views.setTextViewText(
            R.id.widget_alarm_time,
            TimeFormat.formatTime(zone, use24h, alarm.hour, alarm.minute)
        )
        views.setTextViewText(R.id.widget_alarm_subtitle, subtitle)
    }

    private fun alarmPlace(alarm: AlarmEntity, fallbackZoneId: String): String {
        val selectedPlace = listOf(alarm.placeFlag, alarm.placeName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return selectedPlace.ifBlank {
            alarm.timeZoneId.substringAfterLast('/').replace('_', ' ').ifBlank { fallbackZoneId }
        }
    }

    private fun City.toEntity(order: Int) = WorldCityEntity(
        locationId = id,
        tzId = tzId,
        displayName = name,
        country = country,
        flag = flag,
        sortOrder = order
    )

    private fun dayRelation(context: Context, zone: TimeZone): String =
        when (val delta = TimeFormat.dayDelta(zone)) {
            0 -> context.getString(R.string.world_today)
            1 -> context.getString(R.string.widget_tomorrow)
            -1 -> context.getString(R.string.widget_yesterday)
            in 2..Int.MAX_VALUE -> context.resources.getQuantityString(
                R.plurals.widget_days_ahead,
                delta,
                delta
            )
            else -> context.resources.getQuantityString(
                R.plurals.widget_days_behind,
                -delta,
                -delta
            )
        }

    private fun safeTimeZone(tzId: String): TimeZone =
        if (TimeZone.getAvailableIDs().contains(tzId)) TimeZone.getTimeZone(tzId)
        else TimeZone.getDefault()

    private fun configureTextClock(views: RemoteViews, viewId: Int, tzId: String, use24h: Boolean) {
        val pattern = if (use24h) "HH:mm" else "h:mm a"
        views.setString(viewId, "setTimeZone", tzId)
        views.setCharSequence(viewId, "setFormat12Hour", pattern)
        views.setCharSequence(viewId, "setFormat24Hour", pattern)
    }

    private fun openWorld(context: Context): PendingIntent {
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

    private fun openAlarms(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Actions.ACTION_OPEN_ALARM_TAB
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1,
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

        fun loadCities(context: Context, id: Int): List<City> =
            loadConfiguredCities(context, id)?.ifEmpty(::defaultCities) ?: defaultCities()

        internal fun loadConfiguredCities(context: Context, id: Int): List<City>? {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("widget_$id", null)
                ?: return null
            if (!raw.trimStart().startsWith("[")) {
                return raw.split(SEPARATOR)
                    .filter { it.isNotBlank() }
                    .map(::legacyCity)
                    .ifEmpty(::defaultCities)
            }
            return decodeCities(raw).getOrNull()?.ifEmpty(::defaultCities)
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
                            flag = item.optString("flag", "\uD83C\uDF10"),
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
            flag = "\uD83C\uDF10",
            admin1 = "",
            tzId = tzId,
            population = 0
        )

        private fun defaultCities(): List<City> = listOf(
            City(-1, "New York", "United States", "US", "\uD83C\uDDFA\uD83C\uDDF8", "New York", "America/New_York", 0),
            City(-2, "London", "United Kingdom", "GB", "\uD83C\uDDEC\uD83C\uDDE7", "England", "Europe/London", 0),
            City(-3, "Tokyo", "Japan", "JP", "\uD83C\uDDEF\uD83C\uDDF5", "Tokyo", "Asia/Tokyo", 0)
        )
    }
}
