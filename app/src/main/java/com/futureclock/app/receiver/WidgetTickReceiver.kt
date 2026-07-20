package com.futureclock.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.futureclock.app.widget.WidgetUpdateScheduler

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetUpdateScheduler.refreshAll(context)
        WidgetUpdateScheduler.scheduleNext(context)
    }
}
