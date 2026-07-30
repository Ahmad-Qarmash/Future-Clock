package com.futureclock.app.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.databinding.FragmentMoreBinding
import com.futureclock.app.databinding.ItemMoreActionBinding

class MoreFragment : Fragment() {

    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = activity as? MainActivity
        binding.btnSettings.setOnClickListener { host?.openSettings() }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = MoreAdapter(
            listOf(
                MoreAction(
                    R.string.tab_stopwatch,
                    R.string.more_stopwatch_description,
                    R.drawable.ic_stopwatch
                ) { host?.openStopwatchFromMore() },
                MoreAction(
                    R.string.tab_settings,
                    R.string.more_settings_description,
                    R.drawable.ic_settings
                ) { host?.openSettings() },
                MoreAction(
                    R.string.settings_appearance,
                    R.string.more_appearance_description,
                    R.drawable.ic_clock
                ) { host?.openSettings() },
                MoreAction(
                    R.string.more_widgets,
                    R.string.more_widgets_description,
                    R.drawable.ic_world
                ) {
                    AlertDialog.Builder(requireContext(), R.style.Theme_FutureClock_Dialog)
                        .setTitle(R.string.more_widgets)
                        .setMessage(R.string.more_widgets_help)
                        .setPositiveButton(R.string.action_ok, null)
                        .show()
                },
                MoreAction(
                    R.string.more_about,
                    R.string.more_about_description,
                    R.drawable.ic_clock
                ) { host?.openSettings() }
            )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private data class MoreAction(
        @StringRes val title: Int,
        @StringRes val subtitle: Int,
        @DrawableRes val icon: Int,
        val onClick: () -> Unit
    )

    private class MoreAdapter(
        private val items: List<MoreAction>
    ) : RecyclerView.Adapter<MoreAdapter.VH>() {

        class VH(val binding: ItemMoreActionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(
            ItemMoreActionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.binding.title.setText(item.title)
            holder.binding.subtitle.setText(item.subtitle)
            holder.binding.icon.setImageResource(item.icon)
            holder.binding.root.setOnClickListener { item.onClick() }
        }
    }
}
