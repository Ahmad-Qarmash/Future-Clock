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
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val app = context.applicationContext as FutureClockApp
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                app.database.alarmDao().getEnabledSortedByNext().forEach { alarm ->
                    AlarmScheduler.schedule(context, alarm)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
