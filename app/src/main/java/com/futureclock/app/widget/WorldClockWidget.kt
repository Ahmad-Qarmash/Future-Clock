package com.futureclock.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.futureclock.app.BuildConfig
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.data.db.WorldCityEntity
import com.futureclock.app.notification.Actions
import com.futureclock.app.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.TimeZone
import kotlin.math.abs

/**
 * The flagship widget. Its content is always the tracked World Clock list from Room;
 * preferences only hold the page each individual widget is currently displaying.
 */
class WorldClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids, pageDelta = 0)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle
    ) {
        updateWidgets(context, manager, intArrayOf(id), pageDelta = 0)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Actions.ACTION_WORLD_WIDGET_NEXT_PAGE -> updateWidgets(
                context,
                AppWidgetManager.getInstance(context),
                intArrayOf(intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)),
                pageDelta = 1
            )

            Actions.ACTION_WORLD_WIDGET_PREVIOUS_PAGE -> updateWidgets(
                context,
                AppWidgetManager.getInstance(context),
                intArrayOf(intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)),
                pageDelta = -1
            )

            Actions.ACTION_WORLD_WIDGET_REFRESH -> {
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: intArrayOf()
                val delta = if (intent.getBooleanExtra(EXTRA_ADVANCE_PAGE, false)) 1 else 0
                updateWidgets(context, AppWidgetManager.getInstance(context), ids, delta)
            }

            else -> super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateScheduler.scheduleNext(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { editor.remove(pageKey(it)) }
        editor.apply()
    }

    private fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        requestedIds: IntArray,
        pageDelta: Int
    ) {
        val ids = requestedIds.filter { it != AppWidgetManager.INVALID_APPWIDGET_ID }.toIntArray()
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = WidgetDataSource.loadWorld(context)
                ids.forEach { id -> renderWidget(context, manager, id, snapshot, pageDelta) }
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "World widget update failed", error)
                ids.forEach { id -> renderUnavailable(context, manager, id) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun renderWidget(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        snapshot: WorldWidgetSnapshot,
        pageDelta: Int
    ) {
        val rows = WorldWidgetPaging.visibleRows(manager.getAppWidgetOptions(id))
        val storedPage = currentPage(context, id)
        val page = WorldWidgetPaging.nextPage(storedPage, snapshot.cities.size, rows, pageDelta)
        savePage(context, id, page)

        val views = RemoteViews(context.packageName, R.layout.widget_world_live)
        views.setTextViewText(
            R.id.widget_city_count,
            context.resources.getQuantityString(
                R.plurals.widget_city_count,
                snapshot.cities.size,
                snapshot.cities.size
            )
        )

        val pageCount = WorldWidgetPaging.pageCount(snapshot.cities.size, rows)
        views.setTextViewText(
            R.id.widget_page_indicator,
            if (snapshot.cities.isEmpty()) context.getString(R.string.widget_no_places_short)
            else context.getString(R.string.widget_page_indicator, page + 1, pageCount)
        )

        val cities = snapshot.cities.drop(page * rows).take(rows)
        views.setViewVisibility(R.id.widget_empty_state, if (cities.isEmpty()) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_rows, if (cities.isEmpty()) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_navigation, if (pageCount > 1) View.VISIBLE else View.INVISIBLE)
        if (cities.isEmpty()) {
            views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.widget_empty_title))
            views.setTextViewText(R.id.widget_empty_subtitle, context.getString(R.string.widget_empty_subtitle))
        }

        ROW_IDS.forEachIndexed { index, rowId ->
            val city = cities.getOrNull(index)
            views.setViewVisibility(rowId, if (city == null) View.GONE else View.VISIBLE)
            if (city != null) renderCity(context, views, index, city, snapshot.use24h)
        }

        views.setOnClickPendingIntent(R.id.widget_header, openWorld(context, null, id))
        views.setOnClickPendingIntent(R.id.widget_empty_state, openWorld(context, null, id))
        views.setOnClickPendingIntent(R.id.widget_prev, pageIntent(context, id, previous = true))
        views.setOnClickPendingIntent(R.id.widget_next, pageIntent(context, id, previous = false))
        views.setContentDescription(R.id.widget_prev, context.getString(R.string.widget_previous_page))
        views.setContentDescription(R.id.widget_next, context.getString(R.string.widget_next_page))
        manager.updateAppWidget(id, views)
        debug("Rendered id=$id rows=$rows page=${page + 1}/$pageCount cities=${snapshot.cities.size}")
    }

    private fun renderUnavailable(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_world_live)
        views.setViewVisibility(R.id.widget_rows, View.GONE)
        views.setViewVisibility(R.id.widget_navigation, View.INVISIBLE)
        views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
        views.setTextViewText(R.id.widget_city_count, context.getString(R.string.widget_unavailable_short))
        views.setTextViewText(R.id.widget_page_indicator, "")
        views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.widget_unavailable_title))
        views.setTextViewText(R.id.widget_empty_subtitle, context.getString(R.string.widget_unavailable_subtitle))
        views.setOnClickPendingIntent(R.id.widget_header, openWorld(context, null, id))
        views.setOnClickPendingIntent(R.id.widget_empty_state, openWorld(context, null, id))
        manager.updateAppWidget(id, views)
    }

    private fun renderCity(
        context: Context,
        views: RemoteViews,
        index: Int,
        city: WorldCityEntity,
        use24h: Boolean
    ) {
        val zone = safeTimeZone(city.tzId)
        val now = System.currentTimeMillis()
        val cityName = listOf(city.flag, city.displayName).filter(String::isNotBlank).joinToString(" ")
        val meta = context.getString(
            R.string.widget_city_meta,
            city.country,
            TimeFormat.formatOffset(zone),
            relativeOffset(context, zone, now),
            dayRelation(context, zone)
        )
        views.setTextViewText(CITY_IDS[index], cityName)
        views.setTextViewText(META_IDS[index], meta)
        views.setTextViewText(TIME_IDS[index], TimeFormat.formatTime(zone, use24h, showSeconds = false))
        views.setOnClickPendingIntent(ROW_IDS[index], openWorld(context, city.locationId, index))
        views.setContentDescription(
            ROW_IDS[index],
            context.getString(
                R.string.widget_city_description,
                city.displayName,
                city.country,
                TimeFormat.formatTime(zone, use24h, showSeconds = false),
                meta
            )
        )
    }

    private fun dayRelation(context: Context, zone: TimeZone): String = when (TimeFormat.dayDelta(zone)) {
        1 -> context.getString(R.string.widget_tomorrow)
        -1 -> context.getString(R.string.widget_yesterday)
        0 -> context.getString(R.string.world_today)
        else -> context.getString(R.string.widget_other_day)
    }

    private fun relativeOffset(context: Context, zone: TimeZone, now: Long): String {
        val minutes = (zone.getOffset(now) - TimeZone.getDefault().getOffset(now)) / 60_000
        if (minutes == 0) return context.getString(R.string.widget_same_time)
        val sign = if (minutes > 0) "+" else "−"
        val absoluteMinutes = abs(minutes)
        val hours = absoluteMinutes / 60
        val remainingMinutes = absoluteMinutes % 60
        return if (remainingMinutes == 0) "$sign${hours}h" else "$sign${hours}h ${remainingMinutes}m"
    }

    private fun openWorld(context: Context, locationId: Long?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Actions.ACTION_OPEN_WORLD_TAB
            data = Uri.parse("futureclock://world/${locationId ?: "all"}/$requestCode")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (locationId != null) putExtra(MainActivity.EXTRA_WORLD_CITY_ID, locationId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode + 20_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pageIntent(context: Context, id: Int, previous: Boolean): PendingIntent {
        val action = if (previous) Actions.ACTION_WORLD_WIDGET_PREVIOUS_PAGE else Actions.ACTION_WORLD_WIDGET_NEXT_PAGE
        val intent = Intent(context, WorldClockWidget::class.java).apply {
            this.action = action
            data = Uri.parse("futureclock://widget/$id/${if (previous) "previous" else "next"}")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id * 10 + if (previous) 1 else 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun safeTimeZone(tzId: String): TimeZone =
        if (AVAILABLE_ZONE_IDS.contains(tzId)) TimeZone.getTimeZone(tzId) else TimeZone.getDefault()

    private fun currentPage(context: Context, id: Int): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(pageKey(id), 0)

    private fun savePage(context: Context, id: Int, page: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(pageKey(id), page).apply()
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    companion object {
        const val PREFS = "world_widget_state"
        private const val EXTRA_ADVANCE_PAGE = "advance_world_widget_page"
        private const val TAG = "WorldClockWidget"
        private val AVAILABLE_ZONE_IDS = TimeZone.getAvailableIDs().toHashSet()
        private val ROW_IDS = intArrayOf(
            R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
            R.id.widget_row_4, R.id.widget_row_5, R.id.widget_row_6
        )
        private val CITY_IDS = intArrayOf(
            R.id.widget_city_1, R.id.widget_city_2, R.id.widget_city_3,
            R.id.widget_city_4, R.id.widget_city_5, R.id.widget_city_6
        )
        private val META_IDS = intArrayOf(
            R.id.widget_meta_1, R.id.widget_meta_2, R.id.widget_meta_3,
            R.id.widget_meta_4, R.id.widget_meta_5, R.id.widget_meta_6
        )
        private val TIME_IDS = intArrayOf(
            R.id.widget_time_1, R.id.widget_time_2, R.id.widget_time_3,
            R.id.widget_time_4, R.id.widget_time_5, R.id.widget_time_6
        )

        fun requestRefresh(context: Context, advancePage: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WorldClockWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, WorldClockWidget::class.java)
                    .setAction(Actions.ACTION_WORLD_WIDGET_REFRESH)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    .putExtra(EXTRA_ADVANCE_PAGE, advancePage)
            )
        }

        internal fun pageForTest(context: Context, id: Int): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(pageKey(id), 0)

        internal fun savePageForTest(context: Context, id: Int, page: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(pageKey(id), page).commit()
        }

        private fun pageKey(id: Int) = "page_$id"
    }
}
