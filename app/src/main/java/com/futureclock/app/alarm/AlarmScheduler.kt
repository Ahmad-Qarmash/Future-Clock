package com.futureclock.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.futureclock.app.data.db.AlarmEntity
import com.futureclock.app.notification.Actions
import com.futureclock.app.notification.NotificationIds
import com.futureclock.app.util.AlarmMath

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    fun schedule(context: Context, alarm: AlarmEntity) {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return
        }
        val triggerMs = AlarmMath.nextTrigger(
            System.currentTimeMillis(),
            alarm.hour,
            alarm.minute,
            alarm.daysOfWeek,
            alarm.timeZoneId
        )
        if (triggerMs <= 0) return

        val pi = fireIntent(context, alarm.id)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        try {
            if (canExact) {
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerMs, showIntent(context, alarm.id)),
                    pi
                )
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
            Log.d(TAG, "Scheduled alarm ${alarm.id} at $triggerMs")
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm permission missing — falling back", se)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    fun cancel(context: Context, alarmId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(fireIntent(context, alarmId))
    }

    fun snooze(context: Context, alarm: AlarmEntity) {
        val triggerMs = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        val pi = fireIntent(context, alarm.id)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
    }

    private fun fireIntent(context: Context, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = Actions.ACTION_ALARM_FIRE
            putExtra(com.futureclock.app.notification.Extras.ALARM_ID, alarmId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, NotificationIds.ALARM_PREFIX + alarmId.toInt(), intent, flags)
    }

    private fun showIntent(context: Context, alarmId: Long): PendingIntent? {
        val show = Intent(context, com.futureclock.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.futureclock.app.notification.Extras.ALARM_ID, alarmId)
        }
        return PendingIntent.getActivity(
            context,
            NotificationIds.ALARM_PREFIX + alarmId.toInt(),
            show,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
