package com.futureclock.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.R
import com.futureclock.app.data.tz.City
import com.futureclock.app.data.tz.CityCatalog
import com.futureclock.app.databinding.ActivityWorldWidgetConfigBinding
import com.futureclock.app.ui.common.UiFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorldClockConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorldWidgetConfigBinding
    private lateinit var catalog: CityCatalog
    private lateinit var adapter: CityAdapter
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private val selectedCities = linkedMapOf<Long, City>()
    private var searchJob: Job? = null
    private var queryGeneration = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorldWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        catalog = CityCatalog.get(this)
        val restored = savedInstanceState?.getString(STATE_SELECTED)
            ?.let(WorldClockWidget::decodeCities)
            ?.getOrNull()
            ?: WorldClockWidget.loadCities(this, widgetId)
        restored.forEach { selectedCities[it.id] = it }
        adapter = CityAdapter(emptyList(), selectedCities.keys.toMutableSet(), ::toggleCity)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.btnDone.setOnClickListener { complete() }

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadPlaces(s?.toString().orEmpty())
            }
        })

        loadPlaces("")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED, WorldClockWidget.encodeCities(selectedCities.values.toList()))
        super.onSaveInstanceState(outState)
    }

    private fun loadPlaces(query: String) {
        val generation = ++queryGeneration
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            if (query.isNotBlank()) delay(160)
            binding.progress.visibility = View.VISIBLE
            binding.emptyResults.visibility = View.GONE
            val results = try {
                withContext(Dispatchers.IO) { catalog.search(query) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                if (generation != queryGeneration) return@launch
                binding.progress.visibility = View.GONE
                adapter.update(emptyList())
                binding.emptyResults.setText(R.string.place_picker_error)
                binding.emptyResults.visibility = View.VISIBLE
                return@launch
            }
            if (generation != queryGeneration) return@launch
            binding.progress.visibility = View.GONE
            adapter.update(results)
            binding.emptyResults.setText(R.string.world_no_results)
            binding.emptyResults.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun toggleCity(city: City, select: Boolean): Boolean {
        if (select && selectedCities.size >= 3) {
            UiFeedback.show(binding.root, R.string.widget_city_limit)
            return false
        }
        if (select) selectedCities[city.id] = city else selectedCities.remove(city.id)
        return true
    }

    private fun complete() {
        if (selectedCities.isEmpty()) {
            UiFeedback.show(binding.root, R.string.widget_choose_one_city)
            return
        }
        WorldClockWidget.saveCities(this, widgetId, selectedCities.values.toList())
        val update = Intent(this, WorldClockWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        sendBroadcast(update)
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finish()
    }

    private class CityAdapter(
        private var items: List<City>,
        private val selected: MutableSet<Long>,
        private val onToggle: (City, Boolean) -> Boolean
        ) : RecyclerView.Adapter<CityAdapter.VH>() {

        fun update(newItems: List<City>) {
            val oldItems = items
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldItems.size
                override fun getNewListSize(): Int = newItems.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    oldItems[oldItemPosition].id == newItems[newItemPosition].id

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean = oldItems[oldItemPosition] == newItems[newItemPosition]
            })
            items = newItems
            diff.dispatchUpdatesTo(this)
        }

        class VH(val view: View) : RecyclerView.ViewHolder(view) {
            val leading: TextView = view.findViewById(R.id.leading)
            val title: TextView = view.findViewById(R.id.title)
            val subtitle: TextView = view.findViewById(R.id.subtitle)
            val trailing: TextView = view.findViewById(R.id.trailing)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_city_picker, parent, false)
        )

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val city = items[position]
            val isSelected = selected.contains(city.id)
            holder.leading.text = city.flag
            holder.title.text = city.name
            holder.subtitle.text = holder.view.context.getString(
                R.string.place_area_timezone,
                city.areaLabel,
                city.tzId
            )
            holder.trailing.text = if (isSelected) "✓" else "+"
            holder.view.isSelected = isSelected
            holder.view.contentDescription = holder.view.context.getString(
                if (isSelected) R.string.widget_city_selected_description
                else R.string.widget_city_unselected_description,
                city.name,
                city.areaLabel
            )
            holder.view.setOnClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                val select = !selected.contains(city.id)
                if (onToggle(city, select)) {
                    if (select) selected += city.id else selected -= city.id
                    notifyItemChanged(currentPosition)
                }
            }
        }
    }

    companion object {
        private const val STATE_SELECTED = "selected_cities"
    }
}
