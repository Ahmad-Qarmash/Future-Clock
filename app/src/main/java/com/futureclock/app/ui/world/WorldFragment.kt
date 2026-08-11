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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.futureclock.app.FutureClockApp
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.databinding.FragmentWorldBinding
import com.futureclock.app.ui.common.UiFeedback
import com.futureclock.app.widget.WidgetUpdateScheduler
import com.futureclock.app.widget.WorldWidgetDiscovery
import com.futureclock.app.widget.WorldWidgetPinning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WorldFragment : Fragment() {

    private var _binding: FragmentWorldBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: WorldAdapter
    private var persistJob: Job? = null
    private var discoveryPromptVisible = false
    private var pendingFocusCityId: Long? = null

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
                    dao.deleteByLocationId(city.locationId)
                    WidgetUpdateScheduler.refreshAll(requireContext())
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
        binding.btnSettings.setOnClickListener {
            (activity as? MainActivity)?.openSettings()
        }
        binding.btnAddWidget.setOnClickListener { requestWorldWidget() }
        binding.btnEmptyAdd.setOnClickListener {
            startActivity(Intent(requireContext(), WorldPickerActivity::class.java))
        }
        pendingFocusCityId = (activity as? MainActivity)?.consumePendingWorldCityId()

        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireContext().applicationContext as FutureClockApp
            val dao = app.database.worldCityDao()
            dao.observeAll().collect { cities ->
                adapter.submit(cities)
                binding.emptyState.visibility = if (cities.isEmpty()) View.VISIBLE else View.GONE
                applyPendingFocus(cities)
                maybeShowWidgetDiscovery(cities.size)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireContext().applicationContext as FutureClockApp
            app.settings.use24h.collect { adapter.use24h = it }
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
            WidgetUpdateScheduler.refreshAll(requireContext())
        }
    }

    /** Called by MainActivity when a widget row is tapped. */
    fun focusCity(locationId: Long) {
        pendingFocusCityId = locationId
        applyPendingFocus(adapter.snapshot())
    }

    private fun applyPendingFocus(cities: List<com.futureclock.app.data.db.WorldCityEntity>) {
        val cityId = pendingFocusCityId ?: return
        val index = cities.indexOfFirst { it.locationId == cityId }
        if (index >= 0) {
            binding.recycler.post { binding.recycler.smoothScrollToPosition(index) }
            pendingFocusCityId = null
        }
    }

    private fun maybeShowWidgetDiscovery(cityCount: Int) {
        if (discoveryPromptVisible || !WorldWidgetDiscovery.shouldPrompt(requireContext(), cityCount)) return
        discoveryPromptVisible = true
        binding.root.post {
            if (!isAdded || !WorldWidgetDiscovery.shouldPrompt(requireContext(), cityCount)) return@post
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.widget_discovery_title)
                .setMessage(R.string.widget_discovery_message)
                .setPositiveButton(R.string.widget_add_to_home) { _, _ ->
                    WorldWidgetDiscovery.markPromptShown(requireContext())
                    requestWorldWidget()
                }
                .setNegativeButton(R.string.widget_not_now) { _, _ ->
                    WorldWidgetDiscovery.markPromptShown(requireContext())
                }
                .show()
        }
    }

    private fun requestWorldWidget() {
        if (WorldWidgetPinning.requestWorldWidget(requireContext())) {
            UiFeedback.show(binding.root, R.string.widget_pin_requested)
        } else {
            UiFeedback.show(binding.root, WorldWidgetPinning.fallbackMessage())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
