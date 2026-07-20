package com.futureclock.app.notification

object NotificationChannels {
    const val ALARMS = "alarms"
    const val TIMER = "timer"
    const val STOPWATCH = "stopwatch"
}

object NotificationIds {
    const val ACTIVE_ALARM = 1001
    const val ALARM_PREFIX = 2000
    const val TIMER = 3001
    const val STOPWATCH = 3002
}

object Actions {
    const val ACTION_ALARM_FIRE = "com.futureclock.app.ACTION_ALARM_FIRE"
    const val ACTION_ALARM_DISMISS = "com.futureclock.app.ACTION_ALARM_DISMISS"
    const val ACTION_ALARM_SNOOZE = "com.futureclock.app.ACTION_ALARM_SNOOZE"
    const val ACTION_TIMER_START = "com.futureclock.app.ACTION_TIMER_START"
    const val ACTION_TIMER_PAUSE = "com.futureclock.app.ACTION_TIMER_PAUSE"
    const val ACTION_TIMER_RESET = "com.futureclock.app.ACTION_TIMER_RESET"
    const val ACTION_TIMER_TICK = "com.futureclock.app.ACTION_TIMER_TICK"
    const val ACTION_STOPWATCH_START = "com.futureclock.app.ACTION_STOPWATCH_START"
    const val ACTION_STOPWATCH_PAUSE = "com.futureclock.app.ACTION_STOPWATCH_PAUSE"
    const val ACTION_STOPWATCH_RESET = "com.futureclock.app.ACTION_STOPWATCH_RESET"
    const val ACTION_STOPWATCH_LAP = "com.futureclock.app.ACTION_STOPWATCH_LAP"
    const val ACTION_WIDGET_TICK = "com.futureclock.app.ACTION_WIDGET_TICK"
    const val ACTION_OPEN_ALARM_TAB = "com.futureclock.app.ACTION_OPEN_ALARM_TAB"
    const val ACTION_OPEN_CLOCK_TAB = "com.futureclock.app.ACTION_OPEN_CLOCK_TAB"
    const val ACTION_OPEN_WORLD_TAB = "com.futureclock.app.ACTION_OPEN_WORLD_TAB"
    const val ACTION_OPEN_TIMER_TAB = "com.futureclock.app.ACTION_OPEN_TIMER_TAB"
    const val ACTION_OPEN_STOPWATCH_TAB = "com.futureclock.app.ACTION_OPEN_STOPWATCH_TAB"
}

object Extras {
    const val ALARM_ID = "alarm_id"
    const val TIMER_TOTAL_MS = "timer_total_ms"
    const val TIMER_REMAINING_MS = "timer_remaining_ms"
    const val STOPWATCH_BASE = "stopwatch_base"
    const val STOPWATCH_OFFSET = "stopwatch_offset"
    const val LAP_INDEX = "lap_index"
}
