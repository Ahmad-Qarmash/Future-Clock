package com.futureclock.app.ui.alarm

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.futureclock.app.FutureClockApp
import com.futureclock.app.alarm.AlarmScheduler
import com.futureclock.app.databinding.FragmentAlarmBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmFragment : Fragment() {

    private var _binding: FragmentAlarmBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AlarmAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlarmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireContext().applicationContext as FutureClockApp
        adapter = AlarmAdapter(
            onToggle = { alarm, enabled ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val updated = alarm.copy(enabled = enabled)
                    val next = if (enabled)
                        com.futureclock.app.util.AlarmMath.nextTrigger(
                            System.currentTimeMillis(), alarm.hour, alarm.minute,
                            alarm.daysOfWeek, alarm.timeZoneId
                        )
                    else 0L
                    app.database.alarmDao().update(updated.copy(nextTriggerMs = next))
                    if (enabled) AlarmScheduler.schedule(requireContext(), updated)
                    else AlarmScheduler.cancel(requireContext(), alarm.id)
                    com.futureclock.app.widget.WidgetUpdateScheduler.refreshAll(requireContext())
                }
            },
            onClick = { alarm ->
                val intent = Intent(requireContext(), AlarmEditActivity::class.java)
                intent.putExtra("alarm_id", alarm.id)
                startActivity(intent)
            }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(requireContext(), AlarmEditActivity::class.java))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val use24h = app.settings.use24h.first()
            adapter.use24h = use24h
            app.database.alarmDao().observeAll().collect { list ->
                adapter.submit(list)
                binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
