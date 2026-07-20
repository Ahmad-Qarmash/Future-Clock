package com.futureclock.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.futureclock.app.FutureClockApp
import com.futureclock.app.notification.Actions
import com.futureclock.app.notification.Extras
import kotlinx.coroutines.launch

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Actions.ACTION_ALARM_SNOOZE) return
        val alarmId = intent.getLongExtra(Extras.ALARM_ID, -1L)
        if (alarmId < 0) return
        val app = context.applicationContext as FutureClockApp
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                val alarm = app.database.alarmDao().getById(alarmId) ?: return@launch
                AlarmScheduler.snooze(context, alarm)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
