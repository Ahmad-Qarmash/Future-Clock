package com.futureclock.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.futureclock.app.FutureClockApp
import com.futureclock.app.notification.Actions
import com.futureclock.app.notification.Extras
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Actions.ACTION_ALARM_FIRE) return
        val alarmId = intent.getLongExtra(Extras.ALARM_ID, -1L)
        if (alarmId < 0) return

        val app = context.applicationContext as FutureClockApp
        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                val alarm = app.database.alarmDao().getById(alarmId) ?: return@launch
                if (!alarm.enabled) return@launch
                val ring = Intent(context, AlarmRingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(Extras.ALARM_ID, alarmId)
                }
                context.startActivity(ring)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
