package com.futureclock.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.futureclock.app.R

/** Small boundary around the launcher-owned pin request API. */
object WorldWidgetPinning {

    fun requestWorldWidget(context: Context): Boolean =
        request(context, WorldClockWidget::class.java)

    fun requestNextAlarmWidget(context: Context): Boolean =
        request(context, NextAlarmWidget::class.java)

    fun fallbackMessage() = R.string.widget_pin_fallback

    private fun request(context: Context, provider: Class<*>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return runCatching {
            manager.requestPinAppWidget(ComponentName(context, provider), null, null)
        }.getOrDefault(false)
    }
}
