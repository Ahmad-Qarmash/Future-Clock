package com.futureclock.app.ui.alarm

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.alarm.AlarmScheduler
import com.futureclock.app.data.db.AlarmEntity
import com.futureclock.app.databinding.ActivityAlarmEditBinding
import com.futureclock.app.util.AlarmMath
import com.futureclock.app.ui.common.UiFeedback
import com.futureclock.app.ui.world.WorldPickerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmEditBinding
    private var existingId: Long = 0L
    private var existing: AlarmEntity? = null
    private var selectedTimeZoneId: String = TimeZone.getDefault().id
    private var selectedPlaceLabel: String = selectedTimeZoneId
    private var isPlacePickerOpen = false
    private val validTimeZoneIds by lazy { TimeZone.getAvailableIDs().toHashSet() }
    private val placePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isPlacePickerOpen = false
        binding.timezonePicker.clearFocus()
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val timeZoneId = data.getStringExtra(WorldPickerActivity.EXTRA_TIMEZONE_ID)
            ?: return@registerForActivityResult
        if (timeZoneId !in validTimeZoneIds) {
            binding.timezonePicker.error = getString(R.string.alarm_timezone_invalid)
            return@registerForActivityResult
        }
        val city = data.getStringExtra(WorldPickerActivity.EXTRA_PLACE_NAME).orEmpty()
        val country = data.getStringExtra(WorldPickerActivity.EXTRA_COUNTRY_NAME).orEmpty()
        val flag = data.getStringExtra(WorldPickerActivity.EXTRA_FLAG).orEmpty()
        selectedTimeZoneId = timeZoneId
        selectedPlaceLabel = listOf("$flag $city".trim(), country, timeZoneId)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        binding.timezonePicker.setText(selectedPlaceLabel)
        binding.timezonePicker.error = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        existingId = intent.getLongExtra("alarm_id", 0L)
        selectedTimeZoneId = savedInstanceState?.getString(STATE_TIME_ZONE_ID)
            ?: TimeZone.getDefault().id
        selectedPlaceLabel = savedInstanceState?.getString(STATE_PLACE_LABEL)
            ?: selectedTimeZoneId
        binding.timePicker.setIs24HourView(true)
        binding.timePicker.hour = savedInstanceState?.getInt(STATE_HOUR) ?: 7
        binding.timePicker.minute = savedInstanceState?.getInt(STATE_MINUTE) ?: 0
        setupPlacePicker()

        if (existingId > 0) {
            title = getString(R.string.alarm_edit_title)
            binding.textScreenTitle.text = getString(R.string.alarm_edit_title)
            binding.btnDelete.visibility = android.view.View.VISIBLE
            lifecycleScope.launch {
                val alarm = (application as FutureClockApp).database.alarmDao().getById(existingId)
                alarm?.let {
                    existing = it
                    if (savedInstanceState == null) {
                        binding.timePicker.hour = it.hour
                        binding.timePicker.minute = it.minute
                        binding.editLabel.setText(it.label)
                        binding.switchVibrate.isChecked = it.vibrate
                        binding.switchGradual.isChecked = it.gradualVolume
                        binding.snoozeSlider.value = it.snoozeMinutes.toFloat()
                        binding.snoozeValue.text = resources.getQuantityString(
                            R.plurals.duration_minutes,
                            it.snoozeMinutes,
                            it.snoozeMinutes
                        )
                        selectedTimeZoneId = it.timeZoneId
                            .takeIf(validTimeZoneIds::contains)
                            ?: TimeZone.getDefault().id
                        selectedPlaceLabel = withContext(Dispatchers.IO) {
                            com.futureclock.app.data.tz.CityCatalog.get(this@AlarmEditActivity)
                                .findByTimeZone(selectedTimeZoneId)
                        }?.let { place ->
                            "${place.flag} ${place.name} · ${place.country} · ${place.tzId}"
                        } ?: selectedTimeZoneId
                        binding.timezonePicker.setText(selectedPlaceLabel)
                        binding.chipMon.isChecked = AlarmMath.hasDay(it.daysOfWeek, 0)
                        binding.chipTue.isChecked = AlarmMath.hasDay(it.daysOfWeek, 1)
                        binding.chipWed.isChecked = AlarmMath.hasDay(it.daysOfWeek, 2)
                        binding.chipThu.isChecked = AlarmMath.hasDay(it.daysOfWeek, 3)
                        binding.chipFri.isChecked = AlarmMath.hasDay(it.daysOfWeek, 4)
                        binding.chipSat.isChecked = AlarmMath.hasDay(it.daysOfWeek, 5)
                        binding.chipSun.isChecked = AlarmMath.hasDay(it.daysOfWeek, 6)
                    }
                }
            }
        } else {
            title = getString(R.string.alarm_new_title)
            binding.textScreenTitle.text = getString(R.string.alarm_new_title)
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TIME_ZONE_ID, selectedTimeZoneId)
        outState.putString(STATE_PLACE_LABEL, selectedPlaceLabel)
        outState.putInt(STATE_HOUR, binding.timePicker.hour)
        outState.putInt(STATE_MINUTE, binding.timePicker.minute)
        super.onSaveInstanceState(outState)
    }

    private fun setupPlacePicker() {
        binding.timezonePicker.setText(selectedPlaceLabel)
        binding.timezonePicker.setOnClickListener { launchPlacePicker() }
        binding.timezonePicker.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) launchPlacePicker()
        }
    }

    private fun launchPlacePicker() {
        if (isPlacePickerOpen) return
        isPlacePickerOpen = true
        placePicker.launch(
            android.content.Intent(this, WorldPickerActivity::class.java)
                .putExtra(WorldPickerActivity.EXTRA_MODE, WorldPickerActivity.MODE_ALARM)
        )
    }

    private fun saveAlarm() {
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
            hour = hour, minute = minute, label = label,
            daysOfWeek = days, vibrate = vibrate, gradualVolume = gradual,
            snoozeMinutes = snooze, enabled = true, timeZoneId = selectedTimeZoneId
        ) ?: AlarmEntity(
            hour = hour, minute = minute, label = label,
            daysOfWeek = days, vibrate = vibrate, gradualVolume = gradual,
            snoozeMinutes = snooze, enabled = true, timeZoneId = selectedTimeZoneId
        )).copy(
            nextTriggerMs = AlarmMath.nextTrigger(
                System.currentTimeMillis(), hour, minute, days, selectedTimeZoneId
            )
        )

        val app = application as FutureClockApp
        lifecycleScope.launch(Dispatchers.IO) {
            val id = if (current == null) app.database.alarmDao().insert(alarm)
            else { app.database.alarmDao().update(alarm); alarm.id }
            AlarmScheduler.schedule(this@AlarmEditActivity, alarm.copy(id = id))
            com.futureclock.app.widget.WidgetUpdateScheduler.refreshAll(this@AlarmEditActivity)
            runOnUiThread {
                UiFeedback.show(binding.root, R.string.alarm_saved, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                    .addCallback(object : com.google.android.material.snackbar.Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: com.google.android.material.snackbar.Snackbar?, event: Int) {
                            finish()
                        }
                    })
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
        val current = existing ?: run { finish(); return }
        val app = application as FutureClockApp
        lifecycleScope.launch(Dispatchers.IO) {
            app.database.alarmDao().delete(current)
            AlarmScheduler.cancel(this@AlarmEditActivity, current.id)
            com.futureclock.app.widget.WidgetUpdateScheduler.refreshAll(this@AlarmEditActivity)
            runOnUiThread { finish() }
        }
    }
    companion object {
        private const val STATE_TIME_ZONE_ID = "selected_time_zone_id"
        private const val STATE_PLACE_LABEL = "selected_place_label"
        private const val STATE_HOUR = "alarm_hour"
        private const val STATE_MINUTE = "alarm_minute"
    }
}
