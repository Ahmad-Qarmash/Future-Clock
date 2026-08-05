package com.futureclock.app.widget

import com.futureclock.app.data.db.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WidgetDataSourceTest {

    @Test
    fun findNextAlarm_recomputesSchedulesAndIgnoresDisabledAlarms() {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.AUGUST, 6, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val alarms = listOf(
            alarm(id = 1, hour = 13, minute = 0, nextTriggerMs = 1L),
            alarm(id = 2, hour = 12, minute = 30, nextTriggerMs = Long.MAX_VALUE),
            alarm(id = 3, hour = 12, minute = 5, enabled = false, nextTriggerMs = 0L)
        )

        val next = WidgetDataSource.findNextAlarm(alarms, now)

        assertEquals(2L, next?.alarm?.id)
    }

    @Test
    fun findNextAlarm_returnsNullWhenNoAlarmIsEnabled() {
        assertNull(
            WidgetDataSource.findNextAlarm(
                listOf(alarm(id = 1, hour = 8, minute = 0, enabled = false)),
                now = 0L
            )
        )
    }

    private fun alarm(
        id: Long,
        hour: Int,
        minute: Int,
        enabled: Boolean = true,
        nextTriggerMs: Long = 0L
    ) = AlarmEntity(
        id = id,
        hour = hour,
        minute = minute,
        enabled = enabled,
        timeZoneId = "UTC",
        nextTriggerMs = nextTriggerMs
    )
}
