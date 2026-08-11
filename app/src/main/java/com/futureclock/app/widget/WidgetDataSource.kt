package com.futureclock.app.widget

import android.content.Context
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.data.db.AlarmEntity
import com.futureclock.app.data.db.WorldCityEntity
import com.futureclock.app.util.AlarmMath
import kotlinx.coroutines.flow.first
import kotlin.math.ceil

internal data class WidgetAlarm(
    val alarm: AlarmEntity,
    val triggerAtMillis: Long
)

internal data class WidgetSnapshot(
    val nextAlarm: WidgetAlarm?,
    val use24h: Boolean
)

internal data class WorldWidgetSnapshot(
    val cities: List<WorldCityEntity>,
    val use24h: Boolean
)

/** Loads one consistent snapshot for widgets from the same data used by the app screens. */
internal object WidgetDataSource {

    suspend fun loadWorld(context: Context): WorldWidgetSnapshot {
        val app = context.applicationContext as FutureClockApp
        return WorldWidgetSnapshot(
            cities = runCatching { app.database.worldCityDao().getAll() }
                .getOrDefault(emptyList()),
            use24h = runCatching { app.settings.use24h.first() }.getOrDefault(true)
        )
    }

    suspend fun load(context: Context): WidgetSnapshot {
        val app = context.applicationContext as FutureClockApp
        val now = System.currentTimeMillis()
        val alarms = runCatching { app.database.alarmDao().getAll() }
            .getOrDefault(emptyList())
        val use24h = runCatching { app.settings.use24h.first() }
            .getOrDefault(true)

        return WidgetSnapshot(
            nextAlarm = findNextAlarm(alarms, now),
            use24h = use24h
        )
    }

    internal fun findNextAlarm(alarms: List<AlarmEntity>, now: Long): WidgetAlarm? =
        alarms.asSequence()
            .filter { it.enabled }
            .map { alarm ->
                WidgetAlarm(
                    alarm = alarm,
                    triggerAtMillis = AlarmMath.nextTrigger(
                        now,
                        alarm.hour,
                        alarm.minute,
                        alarm.daysOfWeek,
                        alarm.timeZoneId
                    )
                )
            }
            .minByOrNull { it.triggerAtMillis }

    fun formatCountdown(context: Context, triggerAtMillis: Long, now: Long): String {
        val totalMinutes = ceil((triggerAtMillis - now).coerceAtLeast(0L) / 60_000.0)
            .toLong()
            .coerceAtLeast(1L)
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        val duration = when {
            days > 0 -> context.getString(R.string.widget_duration_days, days, hours)
            hours > 0 -> context.getString(R.string.widget_duration_hours, hours, minutes)
            else -> context.getString(R.string.widget_duration_minutes, minutes)
        }
        return context.getString(R.string.widget_countdown, duration)
    }
}
