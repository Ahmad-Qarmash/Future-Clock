package com.futureclock.app.ui.stopwatch

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.R
import com.futureclock.app.databinding.FragmentStopwatchBinding
import com.futureclock.app.databinding.ItemLapBinding
import com.futureclock.app.service.StopwatchService
import com.futureclock.app.util.StopwatchFormat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StopwatchFragment : Fragment() {

    private var _binding: FragmentStopwatchBinding? = null
    private val binding get() = _binding!!
    private lateinit var lapAdapter: LapAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStopwatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lapAdapter = LapAdapter()
        binding.recyclerLaps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLaps.adapter = lapAdapter

        binding.chronometer.setOnChronometerTickListener { /* updated via flow */ }
        binding.btnToggle.setOnClickListener {
            val s = StopwatchService.state.value
            if (s.running) StopwatchService.pause(requireContext())
            else StopwatchService.start(requireContext())
        }
        binding.btnLap.setOnClickListener { StopwatchService.lap(requireContext()) }
        binding.btnLap.setOnLongClickListener { v -> share(); v.performClick(); true }

        viewLifecycleOwner.lifecycleScope.launch {
            StopwatchService.state.collectLatest { state ->
                binding.btnToggle.text = getString(if (state.running) R.string.action_pause else R.string.action_start)
                lapAdapter.submit(state.laps)
                binding.recyclerLaps.visibility = if (state.laps.isEmpty()) View.GONE else View.VISIBLE
                binding.textEmptyLaps.visibility = if (state.laps.isEmpty()) View.VISIBLE else View.GONE
                render(state)
            }
        }
        // Tick the digits while running: the StateFlow only emits on state
        // changes, so we re-render on an interval to keep the display live.
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val s = StopwatchService.state.value
                if (s.running) render(s)
                kotlinx.coroutines.delay(33L)
            }
        }
    }

    private fun render(state: StopwatchService.State) {
        val total = state.totalMs
        binding.chronometer.base = SystemClock.elapsedRealtime() - total
        val formatted = StopwatchFormat.format(total, withMillis = true)
        val parts = formatted.split(".")
        binding.chronometer.text = parts[0]
        binding.textMillis.text = if (parts.size > 1) ".${parts[1]}" else ".00"
    }

    private fun share() {
        val state = StopwatchService.state.value
        val text = getString(
            R.string.stopwatch_share_body,
            StopwatchFormat.format(state.totalMs, withMillis = false),
            state.laps.size
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.stopwatch_share_subject))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class LapAdapter : RecyclerView.Adapter<LapAdapter.VH>() {
        private val items = mutableListOf<StopwatchService.Lap>()
        fun submit(list: List<StopwatchService.Lap>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }
        class VH(val b: ItemLapBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemLapBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val lap = items[position]
            holder.b.lapIndex.text = "#${lap.index}"
            holder.b.lapTime.text = StopwatchFormat.format(lap.lapMs)
            holder.b.lapTotal.text = StopwatchFormat.format(lap.totalMs, withMillis = false)
        }
    }
}
