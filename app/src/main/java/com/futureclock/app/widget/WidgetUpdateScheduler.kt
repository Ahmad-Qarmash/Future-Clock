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
     *
     * Uses the inexact `setAndAllowWhileIdle` variant when exact alarms are not permitted
     * (API 31+ requires the user to grant `SCHEDULE_EXACT_ALARM` via Settings). We don't
     * want to crash the app at startup if the permission is still missing.
     */
    fun scheduleNext(context: Context) {
        val now = System.currentTimeMillis()
        val nextMinute = ((now / 60_000L) + 1) * 60_000L + 1_500L
        val triggerAtElapsed = SystemClock.elapsedRealtime() + (nextMinute - now)
        val pi = pendingIntent(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { am.canScheduleExactAlarms() } catch (_: Throwable) { false }
        } else true
        try {
            when {
                canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi)
                }
                canExact -> {
                    @Suppress("DEPRECATION")
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi)
                }
                else -> {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi)
                }
            }
        } catch (_: SecurityException) {
            // Permission was revoked; fall back to inexact to keep the app responsive.
            try {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pi)
            } catch (_: Throwable) { /* last-resort: skip tick this minute */ }
        } catch (_: Throwable) { /* defensive: never crash on tick scheduling */ }
    }

    /** Redraws widgets after app data changes. World Clock stays on its current page. */
    fun refreshAll(context: Context, advanceWorldPage: Boolean = false) {
        val mgr = android.appwidget.AppWidgetManager.getInstance(context)
        WorldClockWidget.requestRefresh(context, advanceWorldPage)
        listOf(NextAlarmWidget::class.java).forEach { cls ->
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
