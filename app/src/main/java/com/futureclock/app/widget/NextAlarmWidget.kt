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
import java.util.Calendar

class NextAlarmWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateOne(context, mgr, id) }
    }

    override fun onEnabled(context: Context) { WidgetUpdateScheduler.scheduleNext(context) }

    private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_next_alarm)
        val app = context.applicationContext as FutureClockApp

        views.setOnClickPendingIntent(R.id.widget_root, openAlarm(context))

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val next = app.database.alarmDao().getNextEnabled()
                renderAlarm(views, next, context)
                mgr.updateAppWidget(id, views)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun renderAlarm(views: RemoteViews, alarm: AlarmEntity?, context: Context) {
        if (alarm == null) {
            views.setTextViewText(R.id.widget_time, context.getString(R.string.widget_no_alarm))
            views.setTextViewText(R.id.widget_subtitle, " ")
            return
        }
        val zone = AlarmMath.timeZone(alarm.timeZoneId)
        val time = TimeFormat.formatTime(zone, use24h = true, hour = alarm.hour, minute = alarm.minute)
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
