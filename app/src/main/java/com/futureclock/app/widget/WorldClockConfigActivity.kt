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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.R
import com.futureclock.app.data.tz.City
import com.futureclock.app.data.tz.CityCatalog
import com.futureclock.app.databinding.ActivityWorldWidgetConfigBinding

class WorldClockConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorldWidgetConfigBinding
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private val selectedTz = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorldWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        // Load existing
        val raw = getSharedPreferences(WorldClockWidget.PREFS, MODE_PRIVATE)
            .getString("widget_$widgetId", null)
        raw?.split("|")?.filter { it.isNotBlank() }?.forEach { selectedTz += it }

        val adapter = CityAdapter(CityCatalog.ALL, selectedTz.toMutableSet()) { city, isSelected ->
            if (isSelected) selectedTz.add(city.tzId) else selectedTz.remove(city.tzId)
            if (selectedTz.size > 3) {
                selectedTz.remove(city.tzId)
                binding.recycler.adapter?.notifyDataSetChanged()
            }
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.update(CityCatalog.search(s?.toString().orEmpty()))
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.root.post {
            // Done when user picks 1-3
            val confirm = com.google.android.material.button.MaterialButton(this).apply {
                text = "Done"
                setOnClickListener { complete() }
            }
            (binding.root as android.widget.LinearLayout).addView(confirm, binding.root.childCount)
        }
    }

    private fun complete() {
        if (selectedTz.isEmpty()) {
            // Auto-fill defaults if nothing picked
            selectedTz.add("America/New_York")
            selectedTz.add("Europe/London")
        }
        WorldClockWidget.saveCities(this, widgetId, selectedTz.toList())
        val mgr = AppWidgetManager.getInstance(this)
        val update = Intent(this, WorldClockWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        sendBroadcast(update)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, result)
        finish()
    }

    private class CityAdapter(
        private var items: List<City>,
        private val selected: MutableSet<String>,
        private val onToggle: (City, Boolean) -> Unit
    ) : RecyclerView.Adapter<CityAdapter.VH>() {

        fun update(newItems: List<City>) {
            items = newItems
            notifyDataSetChanged()
        }

        class VH(val view: View) : RecyclerView.ViewHolder(view) {
            val label: TextView = view.findViewById(R.id.label)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_city_picker, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val city = items[position]
            val isSelected = selected.contains(city.tzId)
            holder.label.text = "${city.flag} ${city.name} — ${city.country}"
            holder.view.setBackgroundResource(
                if (isSelected) R.color.ripple_neon else android.R.color.transparent
            )
            holder.view.setOnClickListener {
                val nowSelected = !selected.contains(city.tzId)
                onToggle(city, nowSelected)
                notifyItemChanged(holder.bindingAdapterPosition)
            }
        }
    }
}
