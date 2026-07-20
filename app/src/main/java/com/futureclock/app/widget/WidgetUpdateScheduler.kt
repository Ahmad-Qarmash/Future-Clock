package com.futureclock.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.futureclock.app.receiver.WidgetTickReceiver

object WidgetUpdateScheduler {

    private const val REQUEST_CODE = 7001

    /**
     * Schedule the next tick to align with the next minute boundary, plus a small buffer
     * to make sure the system has a chance to redraw. This avoids drift from chained
     * delays or workmanager.
     */
    fun scheduleNext(context: Context) {
        val now = System.currentTimeMillis()
        val nextMinute = ((now / 60_000L) + 1) * 60_000L + 1_500L
        val triggerAtElapsed = nextMinute - SystemClock.elapsedRealtime()
        val pi = pendingIntent(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi)
        } else {
            @Suppress("DEPRECATION")
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi)
        }
    }

    fun refreshAll(context: Context) {
        val mgr = android.appwidget.AppWidgetManager.getInstance(context)
        listOf(
            AnalogClockWidget::class.java,
            DigitalClockWidget::class.java,
            WorldClockWidget::class.java,
            NextAlarmWidget::class.java
        ).forEach { cls ->
            val cn = ComponentName(context, cls)
            val ids = mgr.getAppWidgetIds(cn)
            if (ids.isEmpty()) return@forEach
            val intent = Intent(context, cls).apply { action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE }
            intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetTickReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
