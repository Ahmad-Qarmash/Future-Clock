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
import com.futureclock.app.util.AlarmMath
import com.futureclock.app.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NextAlarmWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = WidgetDataSource.load(context)
                ids.forEach { id -> render(context, manager, id, snapshot) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateScheduler.scheduleNext(context)
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        snapshot: WidgetSnapshot
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_next_alarm)
        val next = snapshot.nextAlarm
        if (next == null) {
            views.setTextViewText(R.id.widget_time, context.getString(R.string.widget_no_alarm))
            views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_create_alarm))
        } else {
            val alarm = next.alarm
            val zone = AlarmMath.timeZone(alarm.timeZoneId)
            views.setTextViewText(
                R.id.widget_time,
                TimeFormat.formatTime(zone, snapshot.use24h, alarm.hour, alarm.minute)
            )
            val zoneName = CityCatalog.ALL.firstOrNull { it.tzId == alarm.timeZoneId }?.name
                ?: alarm.timeZoneId.substringAfterLast('/').replace('_', ' ').ifBlank { zone.id }
            val subtitle = listOf(
                alarm.label,
                zoneName,
                WidgetDataSource.formatCountdown(
                    context,
                    next.triggerAtMillis,
                    System.currentTimeMillis()
                )
            ).filter { it.isNotBlank() }.joinToString(" \u00B7 ")
            views.setTextViewText(R.id.widget_subtitle, subtitle)
        }

        views.setOnClickPendingIntent(R.id.widget_root, openAlarm(context))
        manager.updateAppWidget(id, views)
    }

    private fun openAlarm(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Actions.ACTION_OPEN_ALARM_TAB
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
