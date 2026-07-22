package com.futureclock.app.util

import java.util.Calendar
import java.util.TimeZone

object AlarmMath {

    /** Days of week bitmask uses ISO numbering: Mon=1<<0, Tue=1<<1, ... Sun=1<<6. */
    const val DOW_MON = 1 shl 0
    const val DOW_TUE = 1 shl 1
    const val DOW_WED = 1 shl 2
    const val DOW_THU = 1 shl 3
    const val DOW_FRI = 1 shl 4
    const val DOW_SAT = 1 shl 5
    const val DOW_SUN = 1 shl 6

    fun isoDow(dow: Int): Int {
        // Calendar.MONDAY=2..SUNDAY=1, but our bitmask treats Mon as 0..6
        return when (dow) {
            Calendar.MONDAY -> DOW_MON
            Calendar.TUESDAY -> DOW_TUE
            Calendar.WEDNESDAY -> DOW_WED
            Calendar.THURSDAY -> DOW_THU
            Calendar.FRIDAY -> DOW_FRI
            Calendar.SATURDAY -> DOW_SAT
            Calendar.SUNDAY -> DOW_SUN
            else -> 0
        }
    }

    fun hasDay(daysOfWeek: Int, dow: Int): Boolean =
        daysOfWeek != 0 && (daysOfWeek and (1 shl dow)) != 0

    /**
     * Compute the next absolute trigger time (ms since epoch) for an alarm.
     * If no days of week are set, it fires once at the next hour:minute occurrence.
     * Otherwise it picks the earliest day matching the mask, strictly after [now].
     */
    fun nextTrigger(now: Long, hour: Int, minute: Int, daysOfWeek: Int): Long {
        return nextTrigger(now, hour, minute, daysOfWeek, "")
    }

    /**
     * Computes the next trigger using the alarm's IANA timezone. This keeps the wall-clock
     * hour and repeat weekdays anchored to that location across travel and DST transitions.
     * A blank ID is reserved for migrated alarms and follows the current device timezone.
     */
    fun nextTrigger(
        now: Long,
        hour: Int,
        minute: Int,
        daysOfWeek: Int,
        timeZoneId: String
    ): Long {
        val cal = Calendar.getInstance(timeZone(timeZoneId)).apply {
            timeInMillis = now
            set(Calendar.MILLISECOND, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MINUTE, minute)
            set(Calendar.HOUR_OF_DAY, hour)
        }
        if (daysOfWeek == 0) {
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
        // We need the day-of-week with bit set earliest in the future (0..13 days ahead).
        for (i in 0..13) {
            val candidate = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val dow = candidate.get(Calendar.DAY_OF_WEEK)
            val bit = bitForDow(dow)
            if (daysOfWeek and bit != 0) {
                if (candidate.timeInMillis > now) return candidate.timeInMillis
            }
        }
        // Should never happen given our loop bounds
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun timeZone(timeZoneId: String): TimeZone {
        if (timeZoneId.isBlank()) return TimeZone.getDefault()
        return if (TimeZone.getAvailableIDs().contains(timeZoneId)) {
            TimeZone.getTimeZone(timeZoneId)
        } else {
            TimeZone.getDefault()
        }
    }

    private fun bitForDow(dow: Int): Int = when (dow) {
        Calendar.MONDAY -> DOW_MON
        Calendar.TUESDAY -> DOW_TUE
        Calendar.WEDNESDAY -> DOW_WED
        Calendar.THURSDAY -> DOW_THU
        Calendar.FRIDAY -> DOW_FRI
        Calendar.SATURDAY -> DOW_SAT
        Calendar.SUNDAY -> DOW_SUN
        else -> 0
    }

    /** Human-readable repeat summary, e.g. "Mon, Wed, Fri" or "Every day". */
    fun formatDays(daysOfWeek: Int): String {
        if (daysOfWeek == 0) return ""
        if (daysOfWeek == 0x7F) return "Every day"
        val weekdays = DOW_MON or DOW_TUE or DOW_WED or DOW_THU or DOW_FRI
        if (daysOfWeek == weekdays) return "Weekdays"
        val weekend = DOW_SAT or DOW_SUN
        if (daysOfWeek == weekend) return "Weekend"
        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val parts = mutableListOf<String>()
        for (i in 0..6) {
            if (daysOfWeek and (1 shl i) != 0) parts += names[i]
        }
        return parts.joinToString(", ")
    }
}
