package com.futureclock.app.ui.alarm

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.alarm.AlarmScheduler
import com.futureclock.app.data.db.AlarmEntity
import com.futureclock.app.data.tz.CityCatalog
import com.futureclock.app.databinding.ActivityAlarmEditBinding
import com.futureclock.app.ui.common.UiFeedback
import com.futureclock.app.ui.world.WorldPickerActivity
import com.futureclock.app.util.AlarmMath
import com.futureclock.app.util.TimeFormat
import com.futureclock.app.widget.WidgetUpdateScheduler
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmEditBinding
    private var existingId: Long = 0L
    private var existing: AlarmEntity? = null
    private var selectedTimeZoneId: String = TimeZone.getDefault().id
    private var selectedPlaceId: Long = 0L
    private var selectedPlaceName: String = ""
    private var selectedPlaceCountry: String = ""
    private var selectedPlaceFlag: String = ""
    private var selectedFromWorldClock = false
    private var isPlacePickerOpen = false
    private var isPersisting = false
    private val validTimeZoneIds by lazy { TimeZone.getAvailableIDs().toHashSet() }

    private val placePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isPlacePickerOpen = false
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val timeZoneId = data.getStringExtra(WorldPickerActivity.EXTRA_TIMEZONE_ID)
            ?: return@registerForActivityResult
        if (timeZoneId !in validTimeZoneIds) {
            binding.timezonePicker.error = getString(R.string.alarm_timezone_invalid)
            return@registerForActivityResult
        }
        selectedPlaceId = data.getLongExtra(WorldPickerActivity.EXTRA_PLACE_ID, 0L)
        selectedPlaceName = data.getStringExtra(WorldPickerActivity.EXTRA_PLACE_NAME).orEmpty()
        selectedPlaceCountry = data.getStringExtra(WorldPickerActivity.EXTRA_COUNTRY_NAME).orEmpty()
        selectedPlaceFlag = data.getStringExtra(WorldPickerActivity.EXTRA_FLAG).orEmpty()
        selectedTimeZoneId = timeZoneId
        selectedFromWorldClock = data.getBooleanExtra(
            WorldPickerActivity.EXTRA_SOURCE_TRACKED,
            false
        )
        binding.timezonePicker.error = null
        renderSelectedPlace()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        existingId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        selectedTimeZoneId = savedInstanceState?.getString(STATE_TIME_ZONE_ID)
            ?: TimeZone.getDefault().id
        selectedPlaceId = savedInstanceState?.getLong(STATE_PLACE_ID) ?: 0L
        selectedPlaceName = savedInstanceState?.getString(STATE_PLACE_NAME).orEmpty()
        selectedPlaceCountry = savedInstanceState?.getString(STATE_PLACE_COUNTRY).orEmpty()
        selectedPlaceFlag = savedInstanceState?.getString(STATE_PLACE_FLAG).orEmpty()
        selectedFromWorldClock = savedInstanceState?.getBoolean(STATE_FROM_WORLD_CLOCK) ?: false

        binding.timePicker.setIs24HourView(true)
        binding.timePicker.hour = savedInstanceState?.getInt(STATE_HOUR) ?: 7
        binding.timePicker.minute = savedInstanceState?.getInt(STATE_MINUTE) ?: 0
        setupPlacePicker()
        renderSelectedPlace()

        if (existingId > 0) {
            title = getString(R.string.alarm_edit_title)
            binding.textScreenTitle.setText(R.string.alarm_edit_title)
            binding.btnDelete.visibility = View.VISIBLE
            lifecycleScope.launch { loadExistingAlarm(savedInstanceState == null) }
        } else {
            title = getString(R.string.alarm_new_title)
            binding.textScreenTitle.setText(R.string.alarm_new_title)
        }

        binding.snoozeSlider.addOnChangeListener { _, value, _ ->
            val minutes = value.toInt()
            binding.snoozeValue.text = resources.getQuantityString(
                R.plurals.duration_minutes,
                minutes,
                minutes
            )
        }

        binding.btnSave.setOnClickListener { saveAlarm() }
        binding.btnDelete.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_FutureClock_Dialog)
                .setMessage(R.string.alarm_delete_confirm)
                .setPositiveButton(R.string.action_delete) { _, _ -> deleteAlarm() }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        lifecycleScope.launch {
            while (true) {
                delay(30_000L)
                renderSelectedPlace()
            }
        }
    }

    private suspend fun loadExistingAlarm(hydrateControls: Boolean) {
        val app = application as FutureClockApp
        val alarm = app.database.alarmDao().getById(existingId) ?: return
        existing = alarm
        if (!hydrateControls) return

        binding.timePicker.hour = alarm.hour
        binding.timePicker.minute = alarm.minute
        binding.editLabel.setText(alarm.label)
        binding.switchVibrate.isChecked = alarm.vibrate
        binding.switchGradual.isChecked = alarm.gradualVolume
        binding.snoozeSlider.value = alarm.snoozeMinutes.toFloat()
        binding.snoozeValue.text = resources.getQuantityString(
            R.plurals.duration_minutes,
            alarm.snoozeMinutes,
            alarm.snoozeMinutes
        )
        selectedTimeZoneId = alarm.timeZoneId
            .takeIf(validTimeZoneIds::contains)
            ?: TimeZone.getDefault().id
        selectedPlaceId = alarm.placeId
        selectedPlaceName = alarm.placeName
        selectedPlaceCountry = alarm.placeCountry
        selectedPlaceFlag = alarm.placeFlag

        if (selectedPlaceName.isBlank()) {
            try {
                withContext(Dispatchers.IO) {
                    CityCatalog.get(this@AlarmEditActivity).findByTimeZone(selectedTimeZoneId)
                }?.let { place ->
                    selectedPlaceId = place.id
                    selectedPlaceName = place.name
                    selectedPlaceCountry = place.areaLabel
                    selectedPlaceFlag = place.flag
                }
            } catch (error: IllegalStateException) {
                Log.w(TAG, "Could not resolve legacy alarm place metadata", error)
            }
        }
        selectedFromWorldClock = selectedPlaceId > 0L &&
            withContext(Dispatchers.IO) {
                app.database.worldCityDao().getByLocationId(selectedPlaceId) != null
            }
        renderSelectedPlace()

        binding.chipMon.isChecked = AlarmMath.hasDay(alarm.daysOfWeek, 0)
        binding.chipTue.isChecked = AlarmMath.hasDay(alarm.daysOfWeek, 1)
        binding.chipWed.isChecked = AlarmMath.hasDay(alarm.daysOfWeek, 2)
        binding.chipThu.isChecked = AlarmMath.hasDay(alarm.daysOfWeek, 3)
        binding.chipFri.isChecked = AlarmMath.hasDay(alarm.daysOfWeek, 4)
        binding.chipSat.isChecked = AlarmMath.hasDay(alarm.daysOfWeek, 5)
        binding.chipSun.isChecked = AlarmMath.hasDay(alarm.daysOfWeek, 6)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TIME_ZONE_ID, selectedTimeZoneId)
        outState.putLong(STATE_PLACE_ID, selectedPlaceId)
        outState.putString(STATE_PLACE_NAME, selectedPlaceName)
        outState.putString(STATE_PLACE_COUNTRY, selectedPlaceCountry)
        outState.putString(STATE_PLACE_FLAG, selectedPlaceFlag)
        outState.putBoolean(STATE_FROM_WORLD_CLOCK, selectedFromWorldClock)
        outState.putInt(STATE_HOUR, binding.timePicker.hour)
        outState.putInt(STATE_MINUTE, binding.timePicker.minute)
        super.onSaveInstanceState(outState)
    }

    private fun setupPlacePicker() {
        binding.timezoneCard.setOnClickListener { launchPlacePicker() }
        binding.timezonePicker.setOnClickListener { launchPlacePicker() }
        binding.btnChangeTimezone.setOnClickListener { launchPlacePicker() }
    }

    private fun launchPlacePicker() {
        if (isPlacePickerOpen) return
        isPlacePickerOpen = true
        placePicker.launch(
            android.content.Intent(this, WorldPickerActivity::class.java)
                .putExtra(WorldPickerActivity.EXTRA_MODE, WorldPickerActivity.MODE_ALARM)
                .putExtra(WorldPickerActivity.EXTRA_SELECTED_PLACE_ID, selectedPlaceId)
                .putExtra(WorldPickerActivity.EXTRA_SELECTED_TIMEZONE_ID, selectedTimeZoneId)
        )
    }

    private fun renderSelectedPlace() {
        val zone = TimeZone.getTimeZone(selectedTimeZoneId)
        val name = selectedPlaceName.ifBlank { selectedTimeZoneId }
        binding.timezoneFlag.text = selectedPlaceFlag.ifBlank {
            getString(R.string.alarm_timezone_fallback_icon)
        }
        binding.timezonePicker.text = name
        binding.timezoneId.text = selectedTimeZoneId
        binding.timezoneId.visibility =
            if (name == selectedTimeZoneId) View.GONE else View.VISIBLE
        binding.timezoneCountry.text = selectedPlaceCountry.ifBlank {
            getString(R.string.alarm_timezone_device)
        }
        val abbreviation = zone.getDisplayName(
            zone.inDaylightTime(Date()),
            TimeZone.SHORT,
            Locale.getDefault()
        )
        val delta = TimeFormat.dayDelta(zone)
        val day = when {
            delta > 0 -> getString(R.string.day_tomorrow)
            delta < 0 -> getString(R.string.day_yesterday)
            else -> getString(R.string.day_today)
        }
        binding.timezoneCurrent.text = getString(
            R.string.alarm_timezone_current,
            TimeFormat.formatTime(zone, DateFormat.is24HourFormat(this), false),
            "$abbreviation · ${TimeFormat.formatOffset(zone)}",
            day
        )
        binding.timezoneSource.visibility =
            if (selectedFromWorldClock) View.VISIBLE else View.GONE
        binding.timezoneCard.contentDescription = listOf(
            name,
            selectedPlaceCountry,
            binding.timezoneCurrent.text
        ).filter { it.toString().isNotBlank() }.joinToString(". ")
    }

    private fun saveAlarm() {
        if (isPersisting) return
        if (selectedTimeZoneId !in validTimeZoneIds) {
            binding.timezonePicker.error = getString(R.string.alarm_timezone_invalid)
            return
        }
        val days = daysBitmask()
        val hour = binding.timePicker.hour
        val minute = binding.timePicker.minute
        val label = binding.editLabel.text.toString().trim()
        val snooze = binding.snoozeSlider.value.toInt()
        val vibrate = binding.switchVibrate.isChecked
        val gradual = binding.switchGradual.isChecked
        val current = existing
        val alarm = (current?.copy(
            hour = hour,
            minute = minute,
            label = label,
            daysOfWeek = days,
            vibrate = vibrate,
            gradualVolume = gradual,
            snoozeMinutes = snooze,
            enabled = true,
            timeZoneId = selectedTimeZoneId,
            placeId = selectedPlaceId,
            placeName = selectedPlaceName,
            placeCountry = selectedPlaceCountry,
            placeFlag = selectedPlaceFlag
        ) ?: AlarmEntity(
            hour = hour,
            minute = minute,
            label = label,
            daysOfWeek = days,
            vibrate = vibrate,
            gradualVolume = gradual,
            snoozeMinutes = snooze,
            enabled = true,
            timeZoneId = selectedTimeZoneId,
            placeId = selectedPlaceId,
            placeName = selectedPlaceName,
            placeCountry = selectedPlaceCountry,
            placeFlag = selectedPlaceFlag
        )).copy(
            nextTriggerMs = AlarmMath.nextTrigger(
                System.currentTimeMillis(),
                hour,
                minute,
                days,
                selectedTimeZoneId
            )
        )

        val app = application as FutureClockApp
        setPersisting(true)
        lifecycleScope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    val savedId = if (current == null) {
                        app.database.alarmDao().insert(alarm)
                    } else {
                        app.database.alarmDao().update(alarm)
                        alarm.id
                    }
                    AlarmScheduler.schedule(
                        this@AlarmEditActivity,
                        alarm.copy(id = savedId)
                    )
                    savedId
                }
                app.applicationScope.launch(Dispatchers.Default) {
                    WidgetUpdateScheduler.refreshAll(app)
                }
                setResult(RESULT_OK, android.content.Intent().putExtra(EXTRA_ALARM_ID, id))
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Unable to save alarm", error)
                setPersisting(false)
                UiFeedback.show(binding.root, R.string.alarm_save_error)
            }
        }
    }

    private fun daysBitmask(): Int {
        var mask = 0
        if (binding.chipMon.isChecked) mask = mask or AlarmMath.DOW_MON
        if (binding.chipTue.isChecked) mask = mask or AlarmMath.DOW_TUE
        if (binding.chipWed.isChecked) mask = mask or AlarmMath.DOW_WED
        if (binding.chipThu.isChecked) mask = mask or AlarmMath.DOW_THU
        if (binding.chipFri.isChecked) mask = mask or AlarmMath.DOW_FRI
        if (binding.chipSat.isChecked) mask = mask or AlarmMath.DOW_SAT
        if (binding.chipSun.isChecked) mask = mask or AlarmMath.DOW_SUN
        return mask
    }

    private fun deleteAlarm() {
        if (isPersisting) return
        val current = existing ?: run {
            finish()
            return
        }
        val app = application as FutureClockApp
        setPersisting(true, deleting = true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.database.alarmDao().delete(current)
                    AlarmScheduler.cancel(this@AlarmEditActivity, current.id)
                }
                app.applicationScope.launch(Dispatchers.Default) {
                    WidgetUpdateScheduler.refreshAll(app)
                }
                setResult(RESULT_OK)
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Unable to delete alarm", error)
                setPersisting(false)
                UiFeedback.show(binding.root, R.string.alarm_delete_error)
            }
        }
    }

    private fun setPersisting(active: Boolean, deleting: Boolean = false) {
        isPersisting = active
        binding.savingProgress.visibility = if (active) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !active
        binding.btnDelete.isEnabled = !active
        binding.timezoneCard.isEnabled = !active
        binding.timezonePicker.isEnabled = !active
        binding.btnChangeTimezone.isEnabled = !active
        binding.btnSave.setText(
            if (active && !deleting) R.string.action_saving else R.string.action_save
        )
        binding.btnDelete.setText(
            if (active && deleting) R.string.action_deleting else R.string.action_delete
        )
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val STATE_TIME_ZONE_ID = "selected_time_zone_id"
        private const val STATE_PLACE_ID = "selected_place_id"
        private const val STATE_PLACE_NAME = "selected_place_name"
        private const val STATE_PLACE_COUNTRY = "selected_place_country"
        private const val STATE_PLACE_FLAG = "selected_place_flag"
        private const val STATE_FROM_WORLD_CLOCK = "selected_from_world_clock"
        private const val STATE_HOUR = "alarm_hour"
        private const val STATE_MINUTE = "alarm_minute"
        private const val TAG = "AlarmEdit"
    }
}
