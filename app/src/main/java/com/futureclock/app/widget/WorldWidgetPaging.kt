package com.futureclock.app.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import kotlin.math.ceil

/** Size and page calculations are intentionally pure so the widget stays predictable after resize. */
internal object WorldWidgetPaging {

    fun visibleRows(options: Bundle): Int {
        val height = options.getInt(
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 140)
        )
        return when {
            height < 165 -> 2
            height < 225 -> 3
            height < 290 -> 4
            height < 355 -> 5
            else -> MAX_ROWS
        }
    }

    fun pageCount(cityCount: Int, rows: Int): Int =
        if (cityCount <= 0) 1 else ceil(cityCount / rows.coerceAtLeast(1).toDouble()).toInt()

    fun normalizedPage(page: Int, cityCount: Int, rows: Int): Int {
        val pages = pageCount(cityCount, rows)
        return ((page % pages) + pages) % pages
    }

    fun nextPage(page: Int, cityCount: Int, rows: Int, delta: Int): Int =
        normalizedPage(page + delta, cityCount, rows)

    const val MAX_ROWS = 6
}
