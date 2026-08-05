package com.futureclock.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.futureclock.app.FutureClockApp
import com.futureclock.app.alarm.AlarmScheduler
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED) return

        val app = context.applicationContext as FutureClockApp
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                app.database.alarmDao().getEnabledSortedByNext().forEach { alarm ->
                    val next = com.futureclock.app.util.AlarmMath.nextTrigger(
                        System.currentTimeMillis(), alarm.hour, alarm.minute,
                        alarm.daysOfWeek, alarm.timeZoneId
                    )
                    app.database.alarmDao().update(alarm.copy(nextTriggerMs = next))
                    AlarmScheduler.schedule(context, alarm)
                }
            } finally {
                com.futureclock.app.widget.WidgetUpdateScheduler.refreshAll(context)
                com.futureclock.app.widget.WidgetUpdateScheduler.scheduleNext(context)
                pendingResult.finish()
            }
        }
    }
}
