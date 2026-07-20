package com.futureclock.app.ui.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.databinding.FragmentClockBinding
import com.futureclock.app.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.TimeZone

class ClockFragment : Fragment() {

    private var _binding: FragmentClockBinding? = null
    private val binding get() = _binding!!

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBattery(intent)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.analogClock.timeZone = TimeZone.getDefault()
        binding.btnSettings.setOnClickListener {
            (activity as? com.futureclock.app.MainActivity)?.openSettings()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireContext().applicationContext as FutureClockApp
            val use24h = app.settings.use24h.first()
            val showSeconds = app.settings.showSeconds.first()

            while (true) {
                render(use24h, showSeconds)
                delay(500L)
            }
        }

        // Initial battery render
        updateBattery(null)
    }

    private fun render(use24h: Boolean, showSeconds: Boolean) {
        val zone = TimeZone.getDefault()
        val now = java.util.Calendar.getInstance()
        val h = now.get(java.util.Calendar.HOUR_OF_DAY)
        val m = now.get(java.util.Calendar.MINUTE)
        val s = now.get(java.util.Calendar.SECOND)
        val dow = now.get(java.util.Calendar.DAY_OF_WEEK)
        val dowNames = arrayOf("SUNDAY","MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY")
        binding.textDay.text = dowNames[dow - 1]
        binding.textTime.text = TimeFormat.formatTime(use24h, h, m)
        binding.textDate.text = TimeFormat.formatDate(zone)
        binding.textTimezone.text = TimeFormat.formatOffset(zone)
        if (showSeconds) {
            binding.textSeconds.visibility = View.VISIBLE
            binding.textSeconds.text = String.format(":%02d", s)
        } else {
            binding.textSeconds.visibility = View.GONE
        }
    }

    private fun updateBattery(intent: Intent?) {
        val bm = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            ?: bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level >= 0) {
            binding.textBattery.text = getString(R.string.clock_battery_format, level)
        }
    }

    override fun onStart() {
        super.onStart()
        requireContext().registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStop() {
        super.onStop()
        runCatching { requireContext().unregisterReceiver(batteryReceiver) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
