package com.futureclock.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.futureclock.app.BuildConfig
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.databinding.FragmentSettingsBinding
import com.futureclock.app.ui.theme.ThemeController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireContext().applicationContext as FutureClockApp
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.textVersion.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)

        viewLifecycleOwner.lifecycleScope.launch {
            val settings = app.settings
            binding.switch24h.isChecked = settings.use24h.first()
            binding.switchSeconds.isChecked = settings.showSeconds.first()
            val snooze = settings.snoozeMinutes.first()
            binding.sliderSnooze.value = snooze.toFloat()
            binding.textSnoozeValue.text = resources.getQuantityString(
                R.plurals.duration_minutes,
                snooze,
                snooze
            )
            val theme = settings.themeMode.first()
            binding.themeGroup.check(
                when (theme) {
                    ThemeController.LIGHT -> R.id.theme_light
                    ThemeController.DARK -> R.id.theme_dark
                    else -> R.id.theme_system
                }
            )

            binding.switch24h.setOnCheckedChangeListener { _, checked -> viewLifecycleOwner.lifecycleScope.launch { settings.setUse24h(checked) } }
            binding.switchSeconds.setOnCheckedChangeListener { _, checked -> viewLifecycleOwner.lifecycleScope.launch { settings.setShowSeconds(checked) } }
            binding.sliderSnooze.addOnChangeListener { _, value, _ ->
                val v = value.toInt()
                binding.textSnoozeValue.text = resources.getQuantityString(
                    R.plurals.duration_minutes,
                    v,
                    v
                )
                viewLifecycleOwner.lifecycleScope.launch { settings.setSnoozeMinutes(v) }
            }
            binding.themeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val mode = when (checkedId) {
                    R.id.theme_light -> ThemeController.LIGHT
                    R.id.theme_dark -> ThemeController.DARK
                    else -> ThemeController.SYSTEM
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    settings.setThemeMode(mode)
                    ThemeController.apply(mode)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
