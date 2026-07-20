package com.futureclock.app.ui.world

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.FutureClockApp
import com.futureclock.app.ads.AdManager
import com.futureclock.app.databinding.FragmentWorldBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WorldFragment : Fragment() {

    private var _binding: FragmentWorldBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: WorldAdapter
    private var persistJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWorldBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = WorldAdapter(
            onRemove = { city ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val dao = (requireContext().applicationContext as FutureClockApp).database.worldCityDao()
                    dao.deleteByTz(city.tzId)
                    com.futureclock.app.widget.WidgetUpdateScheduler.refreshAll(requireContext())
                }
            }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        val touch = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                adapter.swap(from, to)
                persistOrder()
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        })
        touch.attachToRecyclerView(binding.recycler)

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(requireContext(), WorldPickerActivity::class.java))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val dao = (requireContext().applicationContext as FutureClockApp).database.worldCityDao()
            dao.observeAll().collect { cities ->
                adapter.submit(cities)
                binding.emptyState.visibility = if (cities.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun persistOrder() {
        val snapshot = adapter.snapshot()
        persistJob?.cancel()
        persistJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val dao = (requireContext().applicationContext as FutureClockApp).database.worldCityDao()
            snapshot.forEachIndexed { index, city ->
                dao.update(city.copy(sortOrder = index + 1))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            delay(300)
            activity?.let { AdManager.maybeShowInterstitial(it, AdManager.Trigger.ADD_CITY) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
