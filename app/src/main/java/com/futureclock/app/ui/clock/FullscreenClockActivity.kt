package com.futureclock.app.ui.clock

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.data.db.AlarmEntity
import com.futureclock.app.databinding.ActivityFullscreenClockBinding
import com.futureclock.app.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.TimeZone

class FullscreenClockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenClockBinding
    private var use24h = true
    private var showSeconds = true
    private var customZoneEnabled = false
    private var customZoneName = "Custom time"
    private var customZoneOffsetMinutes = 0
    private var nextAlarm: AlarmEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenClockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnClose.setOnClickListener { finish() }

        val app = applicationContext as FutureClockApp
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    app.settings.use24h.collect {
                        use24h = it
                        render()
                    }
                }
                launch {
                    app.settings.showSeconds.collect {
                        showSeconds = it
                        binding.analogClock.showSeconds = it
                        render()
                    }
                }
                launch {
                    combine(
                        app.settings.customZoneEnabled,
                        app.settings.customZoneName,
                        app.settings.customZoneOffsetMinutes
                    ) { enabled, name, offset -> Triple(enabled, name, offset) }
                        .collect { (enabled, name, offset) ->
                            customZoneEnabled = enabled
                            customZoneName = name
                            customZoneOffsetMinutes = offset
                            render()
                        }
                }
                launch {
                    app.database.alarmDao().observeAll().collect { alarms ->
                        nextAlarm = alarms
                            .asSequence()
                            .filter { it.enabled && it.nextTriggerMs > 0L }
                            .minByOrNull { it.nextTriggerMs }
                        render()
                    }
                }
                launch {
                    while (true) {
                        render()
                        delay(250L)
                    }
                }
            }
        }
    }

    private fun activeZone(): TimeZone = if (customZoneEnabled) {
        SimpleTimeZone(customZoneOffsetMinutes * 60_000, customZoneName)
    } else {
        TimeZone.getDefault()
    }

    private fun render() {
        val zone = activeZone()
        val now = Date()
        val calendar = Calendar.getInstance(zone).apply { time = now }

        binding.analogClock.timeZone = zone
        binding.textDay.text = TimeFormat.formatDay(zone)
        binding.textTime.text = format(now, zone, if (use24h) "HH:mm" else "h:mm")
        binding.textSeconds.visibility = if (showSeconds) View.VISIBLE else View.GONE
        binding.textSeconds.text = format(now, zone, ":ss")
        binding.textPeriod.visibility = if (use24h) View.GONE else View.VISIBLE
        binding.textPeriod.text = format(now, zone, "a")
        binding.textDate.text = TimeFormat.formatDate(zone)
        binding.textTimezoneName.text = if (customZoneEnabled) {
            customZoneName
        } else {
            zone.id.substringAfterLast('/').replace('_', ' ')
        }
        binding.textTimezoneOffset.text = TimeFormat.formatOffset(zone)
        binding.textCalendar.text = getString(
            R.string.clock_calendar_format,
            calendar.get(Calendar.WEEK_OF_YEAR),
            calendar.get(Calendar.DAY_OF_YEAR)
        )
        binding.textNextAlarm.text = formatNextAlarm(nextAlarm, zone)
    }

    private fun formatNextAlarm(alarm: AlarmEntity?, displayZone: TimeZone): String {
        alarm ?: return getString(R.string.clock_no_alarm)
        val trigger = Date(alarm.nextTriggerMs)
        val triggerCal = Calendar.getInstance(displayZone).apply { time = trigger }
        val today = Calendar.getInstance(displayZone)
        val dayText = when {
            sameDay(triggerCal, today) -> getString(R.string.day_today)
            sameDay(triggerCal, (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }) -> {
                getString(R.string.day_tomorrow)
            }
            else -> format(trigger, displayZone, "EEE, MMM d")
        }
        val timeText = format(trigger, displayZone, if (use24h) "HH:mm" else "h:mm a")
        val base = getString(R.string.clock_next_alarm_format, dayText, timeText)
        return alarm.label.trim().takeIf { it.isNotEmpty() }?.let { "$base · $it" } ?: base
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun format(date: Date, zone: TimeZone, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = zone }.format(date)
}
