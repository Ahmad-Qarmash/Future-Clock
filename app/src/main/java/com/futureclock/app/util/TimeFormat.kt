package com.futureclock.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object TimeFormat {

    fun formatTime(zone: TimeZone, use24h: Boolean, showSeconds: Boolean): String {
        val pattern = if (use24h) {
            if (showSeconds) "HH:mm:ss" else "HH:mm"
        } else {
            if (showSeconds) "h:mm:ss a" else "h:mm a"
        }
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.timeZone = zone
        return sdf.format(Date())
    }

    fun formatTime(zone: TimeZone, use24h: Boolean, hour: Int, minute: Int): String {
        val pattern = if (use24h) "HH:mm" else "h:mm a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.timeZone = zone
        val cal = Calendar.getInstance(zone)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        return sdf.format(cal.time)
    }

    fun formatTime(use24h: Boolean, hour: Int, minute: Int): String {
        val pattern = if (use24h) "HH:mm" else "h:mm a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        return sdf.format(cal.time)
    }

    fun formatDate(zone: TimeZone): String {
        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        sdf.timeZone = zone
        return sdf.format(Date())
    }

    fun formatShortDate(zone: TimeZone): String {
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        sdf.timeZone = zone
        return sdf.format(Date())
    }

    fun formatDay(zone: TimeZone): String {
        val sdf = SimpleDateFormat("EEEE", Locale.getDefault())
        sdf.timeZone = zone
        return sdf.format(Date()).uppercase(Locale.getDefault())
    }

    fun formatOffset(zone: TimeZone): String {
        val now = Date()
        val offsetMs = zone.getOffset(now.time)
        val totalMinutes = offsetMs / 60000
        val sign = if (totalMinutes >= 0) "+" else "-"
        val abs = Math.abs(totalMinutes)
        val h = abs / 60
        val m = abs % 60
        return String.format(Locale.US, "UTC%s%02d:%02d", sign, h, m)
    }

    /** Offset from the device timezone at this instant, so daylight-saving rules stay correct. */
    fun formatDeviceRelativeOffset(zone: TimeZone, now: Long = System.currentTimeMillis()): String {
        val minutes = (zone.getOffset(now) - TimeZone.getDefault().getOffset(now)) / 60_000
        if (minutes == 0) return "Same time"
        val sign = if (minutes > 0) "+" else "−"
        val absoluteMinutes = abs(minutes)
        val hours = absoluteMinutes / 60
        val remainder = absoluteMinutes % 60
        return if (remainder == 0) "$sign${hours}h" else "$sign${hours}h ${remainder}m"
    }

    /** Days difference between two zones at "today" relative to device zone. */
    fun dayDelta(zone: TimeZone): Int {
        val device = TimeZone.getDefault()
        val deviceCal = Calendar.getInstance(device)
        val zoneCal = Calendar.getInstance(zone)
        val deviceDay = deviceCal.get(Calendar.DAY_OF_YEAR)
        val deviceYear = deviceCal.get(Calendar.YEAR)
        val zoneDay = zoneCal.get(Calendar.DAY_OF_YEAR)
        val zoneYear = zoneCal.get(Calendar.YEAR)
        return if (zoneYear == deviceYear) zoneDay - deviceDay
        else ((zoneCal.timeInMillis - deviceCal.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()
    }
}

object StopwatchFormat {
    fun format(ms: Long, withMillis: Boolean = true): String {
        val totalCs = ms / 10
        val cs = (totalCs % 100).toInt()
        val totalSec = totalCs / 100
        val sec = (totalSec % 60).toInt()
        val totalMin = totalSec / 60
        val min = (totalMin % 60).toInt()
        val hour = (totalMin / 60).toInt()
        val base = if (hour > 0) String.format(Locale.US, "%d:%02d:%02d", hour, min, sec)
        else String.format(Locale.US, "%02d:%02d", min, sec)
        return if (withMillis) String.format(Locale.US, "%s.%02d", base, cs) else base
    }
}

object CountdownFormat {
    fun format(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val sec = (totalSec % 60).toInt()
        val min = ((totalSec / 60) % 60).toInt()
        val hour = (totalSec / 3600).toInt()
        return if (hour > 0) String.format(Locale.US, "%d:%02d:%02d", hour, min, sec)
        else String.format(Locale.US, "%02d:%02d", min, sec)
    }
}

object CountdownLongFormat {
    /** "8h 24m" or "24m 12s" */
    fun format(ms: Long): String {
        if (ms <= 0) return "0m"
        val totalSec = ms / 1000
        val hour = totalSec / 3600
        val min = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return when {
            hour > 0 -> String.format(Locale.US, "%dh %dm", hour, min)
            min > 0 -> String.format(Locale.US, "%dm %02ds", min, sec)
            else -> String.format(Locale.US, "%ds", sec)
        }
    }
}
