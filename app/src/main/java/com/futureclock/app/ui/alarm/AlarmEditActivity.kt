package com.futureclock.app.ui.alarm

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.alarm.AlarmScheduler
import com.futureclock.app.data.db.AlarmEntity
import com.futureclock.app.databinding.ActivityAlarmEditBinding
import com.futureclock.app.util.AlarmMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmEditBinding
    private var existingId: Long = 0L
    private var existing: AlarmEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        existingId = intent.getLongExtra("alarm_id", 0L)
        binding.timePicker.setIs24HourView(true)
        binding.timePicker.hour = 7
        binding.timePicker.minute = 0

        if (existingId > 0) {
            title = getString(R.string.alarm_edit_title)
            binding.btnDelete.visibility = android.view.View.VISIBLE
            lifecycleScope.launch {
                val alarm = (application as FutureClockApp).database.alarmDao().getById(existingId)
                alarm?.let {
                    existing = it
                    binding.timePicker.hour = it.hour
                    binding.timePicker.minute = it.minute
                    binding.editLabel.setText(it.label)
                    binding.switchVibrate.isChecked = it.vibrate
                    binding.switchGradual.isChecked = it.gradualVolume
                    binding.snoozeSlider.value = it.snoozeMinutes.toFloat()
                    binding.snoozeValue.text = "${it.snoozeMinutes} min"
                    binding.chipMon.isChecked = AlarmMath.hasDay(it.daysOfWeek, 0)
                    binding.chipTue.isChecked = AlarmMath.hasDay(it.daysOfWeek, 1)
                    binding.chipWed.isChecked = AlarmMath.hasDay(it.daysOfWeek, 2)
                    binding.chipThu.isChecked = AlarmMath.hasDay(it.daysOfWeek, 3)
                    binding.chipFri.isChecked = AlarmMath.hasDay(it.daysOfWeek, 4)
                    binding.chipSat.isChecked = AlarmMath.hasDay(it.daysOfWeek, 5)
                    binding.chipSun.isChecked = AlarmMath.hasDay(it.daysOfWeek, 6)
                }
            }
        } else {
            title = getString(R.string.alarm_new_title)
        }

        binding.snoozeSlider.addOnChangeListener { _, value, _ ->
            binding.snoozeValue.text = "${value.toInt()} min"
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

    private fun saveAlarm() {
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
            snoozeMinutes = snooze, enabled = true
        ) ?: AlarmEntity(
            hour = hour, minute = minute, label = label,
            daysOfWeek = days, vibrate = vibrate, gradualVolume = gradual,
            snoozeMinutes = snooze, enabled = true
        )).copy(
            nextTriggerMs = AlarmMath.nextTrigger(System.currentTimeMillis(), hour, minute, days)
        )

        val app = application as FutureClockApp
        lifecycleScope.launch(Dispatchers.IO) {
            val id = if (current == null) app.database.alarmDao().insert(alarm)
            else { app.database.alarmDao().update(alarm); alarm.id }
            AlarmScheduler.schedule(this@AlarmEditActivity, alarm.copy(id = id))
            com.futureclock.app.widget.WidgetUpdateScheduler.refreshAll(this@AlarmEditActivity)
            runOnUiThread {
                Toast.makeText(this@AlarmEditActivity, "Saved", Toast.LENGTH_SHORT).show()
                finish()
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
}
