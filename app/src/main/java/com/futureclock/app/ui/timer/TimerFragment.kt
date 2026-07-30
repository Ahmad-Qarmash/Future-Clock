package com.futureclock.app.ui.timer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.futureclock.app.R
import com.futureclock.app.databinding.FragmentTimerBinding
import com.futureclock.app.service.TimerService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TimerFragment : Fragment() {

    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!

    private var totalMs: Long = 60_000L
    private var presetMinutes: Int = 1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.circularTimer.totalMs = totalMs
        binding.circularTimer.remainingMs = totalMs
        binding.btnSettings.setOnClickListener {
            (activity as? com.futureclock.app.MainActivity)?.openSettings()
        }

        binding.presetChips.setOnCheckedStateChangeListener { _, ids ->
            val mins = when (ids.firstOrNull()) {
                R.id.chip_1m -> 1
                R.id.chip_3m -> 3
                R.id.chip_5m -> 5
                R.id.chip_10m -> 10
                R.id.chip_30m -> 30
                else -> null
            }
            if (mins != null && mins != presetMinutes) {
                presetMinutes = mins
                totalMs = mins * 60_000L
                binding.circularTimer.totalMs = totalMs
                binding.circularTimer.remainingMs = totalMs
            }
        }
        binding.chip1m.isChecked = true

        binding.btnPrimary.setOnClickListener {
            val state = TimerService.state.value
            when (state.state) {
                TimerService.State.IDLE, TimerService.State.FINISHED -> {
                    TimerService.start(requireContext(), totalMs)
                }
                TimerService.State.RUNNING -> {
                    TimerService.pause(requireContext())
                }
                TimerService.State.PAUSED -> {
                    TimerService.start(requireContext(), state.remainingMs)
                }
            }
        }
        binding.btnReset.setOnClickListener {
            TimerService.reset(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            TimerService.state.collectLatest { state ->
                val total = if (state.totalMs > 0) state.totalMs else totalMs
                // In IDLE the service reports 0/0; show the selected preset instead of 00:00 DONE.
                val remaining = when (state.state) {
                    TimerService.State.IDLE -> total
                    else -> state.remainingMs
                }
                binding.circularTimer.totalMs = total
                binding.circularTimer.remainingMs = remaining
                binding.textStatus.text = when (state.state) {
                    TimerService.State.IDLE -> getString(R.string.timer_idle)
                    TimerService.State.RUNNING -> getString(R.string.timer_running)
                    TimerService.State.PAUSED -> getString(R.string.timer_paused)
                    TimerService.State.FINISHED -> "DONE"
                }
                binding.btnPrimary.text = when (state.state) {
                    TimerService.State.RUNNING -> getString(R.string.action_pause)
                    else -> getString(R.string.action_start)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
