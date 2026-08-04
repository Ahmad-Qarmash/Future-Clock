package com.futureclock.app.ui.clock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.futureclock.app.FutureClockApp
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.data.db.AlarmEntity
import com.futureclock.app.databinding.DialogCustomClockBinding
import com.futureclock.app.databinding.FragmentClockBinding
import com.futureclock.app.util.TimeFormat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.TimeZone

class ClockFragment : Fragment() {

    private var _binding: FragmentClockBinding? = null
    private val binding get() = _binding!!

    private var use24h = true
    private var showSeconds = true
    private var customZoneEnabled = false
    private var customZoneName = "Custom time"
    private var customZoneOffsetMinutes = 0
    private var nextAlarm: AlarmEntity? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireContext().applicationContext as FutureClockApp

        binding.btnSettings.setOnClickListener { (activity as? MainActivity)?.openSettings() }
        binding.btnCustomTime.setOnClickListener { showCustomTimeDialog() }
        binding.cardTimeZone.setOnClickListener { showCustomTimeDialog() }

        binding.timeFormatGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selected24h = checkedId == R.id.format_24h
            if (selected24h != use24h) {
                use24h = selected24h
                render()
                viewLifecycleOwner.lifecycleScope.launch { app.settings.setUse24h(selected24h) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    app.settings.use24h.collect { savedUse24h ->
                        use24h = savedUse24h
                        binding.timeFormatGroup.check(if (savedUse24h) R.id.format_24h else R.id.format_12h)
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
        if (_binding == null) return
        val zone = activeZone()
        val now = Date()
        val calendar = Calendar.getInstance(zone).apply { time = now }

        binding.analogClock.timeZone = zone
        binding.analogClock.timeOffsetMillis = 0L
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
        binding.textTimezone.text = TimeFormat.formatOffset(zone)
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
            sameDay(triggerCal, (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }) -> getString(R.string.day_tomorrow)
            else -> format(trigger, displayZone, "EEE, MMM d")
        }
        val timeText = format(trigger, displayZone, if (use24h) "HH:mm" else "h:mm a")
        val label = alarm.label.trim()
        val base = getString(R.string.clock_next_alarm_format, dayText, timeText)
        return if (label.isEmpty()) base else "$base · $label"
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun format(date: Date, zone: TimeZone, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = zone }.format(date)

    private fun showCustomTimeDialog() {
        val dialogBinding = DialogCustomClockBinding.inflate(layoutInflater)
        dialogBinding.switchCustomZone.isChecked = customZoneEnabled
        dialogBinding.inputZoneName.setText(customZoneName)
        dialogBinding.sliderUtcOffset.value = customZoneOffsetMinutes.toFloat()

        fun updateFields(enabled: Boolean) {
            dialogBinding.customZoneFields.isEnabled = enabled
            dialogBinding.customZoneFields.alpha = if (enabled) 1f else 0.45f
        }

        fun updateOffset(value: Int) {
            dialogBinding.textOffsetValue.text = formatOffset(value)
        }

        updateFields(customZoneEnabled)
        updateOffset(customZoneOffsetMinutes)
        dialogBinding.switchCustomZone.setOnCheckedChangeListener { _, enabled -> updateFields(enabled) }
        dialogBinding.sliderUtcOffset.addOnChangeListener { _, value, _ -> updateOffset(value.toInt()) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clock_custom_time_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val enabled = dialogBinding.switchCustomZone.isChecked
                val name = dialogBinding.inputZoneName.text?.toString().orEmpty()
                val offset = dialogBinding.sliderUtcOffset.value.toInt()
                viewLifecycleOwner.lifecycleScope.launch {
                    val app = requireContext().applicationContext as FutureClockApp
                    app.settings.setCustomZone(enabled, name, offset)
                }
            }
            .show()
    }

    private fun formatOffset(totalMinutes: Int): String {
        val sign = if (totalMinutes >= 0) "+" else "-"
        val absolute = kotlin.math.abs(totalMinutes)
        return String.format(Locale.US, "UTC%s%02d:%02d", sign, absolute / 60, absolute % 60)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
