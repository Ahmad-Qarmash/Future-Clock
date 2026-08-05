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
import com.futureclock.app.data.db.AlarmEntity
import com.futureclock.app.notification.Actions
import com.futureclock.app.util.AlarmMath
import com.futureclock.app.util.CountdownLongFormat
import com.futureclock.app.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class NextAlarmWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as FutureClockApp
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarmResult = runCatching { app.database.alarmDao().getNextEnabled() }
                val use24h = app.settings.use24h.first()
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_next_alarm)
                    views.setOnClickPendingIntent(R.id.widget_root, openAlarm(context))
                    alarmResult.onSuccess { renderAlarm(views, it, context, use24h) }
                        .onFailure {
                            views.setTextViewText(
                                R.id.widget_time,
                                context.getString(R.string.widget_temporarily_unavailable)
                            )
                            views.setTextViewText(R.id.widget_subtitle, " ")
                        }
                    mgr.updateAppWidget(id, views)
                }
                if (alarmResult.isFailure) {
                    WidgetUpdateScheduler.scheduleNext(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateScheduler.scheduleNext(context)
    }

    private fun renderAlarm(views: RemoteViews, alarm: AlarmEntity?, context: Context, use24h: Boolean) {
        if (alarm == null) {
            views.setTextViewText(R.id.widget_time, context.getString(R.string.widget_no_alarm))
            views.setTextViewText(R.id.widget_subtitle, " ")
            return
        }
        val zone = AlarmMath.timeZone(alarm.timeZoneId)
        val time = TimeFormat.formatTime(zone, use24h = use24h, hour = alarm.hour, minute = alarm.minute)
        views.setTextViewText(R.id.widget_time, time)
        val nextMs = AlarmMath.nextTrigger(
            System.currentTimeMillis(), alarm.hour, alarm.minute,
            alarm.daysOfWeek, alarm.timeZoneId
        )
        val delta = nextMs - System.currentTimeMillis()
        val countdown = context.getString(R.string.widget_countdown, CountdownLongFormat.format(delta))
        val zoneLabel = alarm.timeZoneId.ifBlank { zone.id }
        val sub = if (alarm.label.isBlank()) "$zoneLabel · $countdown"
        else "${alarm.label} · $zoneLabel · $countdown"
        views.setTextViewText(R.id.widget_subtitle, sub)
    }

    private fun openAlarm(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Actions.ACTION_OPEN_ALARM_TAB
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
