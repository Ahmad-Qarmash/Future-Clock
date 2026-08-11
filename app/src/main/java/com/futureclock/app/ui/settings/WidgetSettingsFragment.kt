package com.futureclock.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.databinding.FragmentWidgetSettingsBinding
import com.futureclock.app.ui.common.UiFeedback
import com.futureclock.app.widget.WorldWidgetPinning
import kotlinx.coroutines.launch

/** Launcher-widget information and pin actions, reachable from Settings → Widgets. */
class WidgetSettingsFragment : Fragment() {

    private var _binding: FragmentWidgetSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWidgetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.btnAddWorldWidget.setOnClickListener { request(WorldWidgetPinning::requestWorldWidget) }
        binding.btnAddAlarmWidget.setOnClickListener { request(WorldWidgetPinning::requestNextAlarmWidget) }

        val app = requireContext().applicationContext as FutureClockApp
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.worldCityDao().observeAll().collect { cities ->
                binding.textWorldWidgetCount.text = resources.getQuantityString(
                    R.plurals.widget_city_count,
                    cities.size,
                    cities.size
                )
            }
        }
    }

    private fun request(pin: (android.content.Context) -> Boolean) {
        if (pin(requireContext())) {
            UiFeedback.show(binding.root, R.string.widget_pin_requested)
        } else {
            UiFeedback.show(binding.root, WorldWidgetPinning.fallbackMessage())
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
