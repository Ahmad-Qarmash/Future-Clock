package com.futureclock.app.ui.world

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.ads.AdManager
import com.futureclock.app.data.tz.City
import com.futureclock.app.data.tz.CityCatalog
import com.futureclock.app.databinding.ActivityWorldPickerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorldPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorldPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorldPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = Adapter(CityCatalog.ALL) { city ->
            addCity(city)
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.submit(CityCatalog.search(s?.toString().orEmpty()))
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun addCity(city: City) {
        val app = applicationContext as FutureClockApp
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = app.database.worldCityDao()
            val next = dao.nextSortOrder()
            dao.insert(
                com.futureclock.app.data.db.WorldCityEntity(
                    tzId = city.tzId,
                    displayName = city.name,
                    country = city.country,
                    flag = city.flag,
                    sortOrder = next
                )
            )
            com.futureclock.app.widget.WidgetUpdateScheduler.refreshAll(this@WorldPickerActivity)
        }
        AdManager.maybeShowInterstitial(this, AdManager.Trigger.ADD_CITY)
        finish()
    }

    private class Adapter(
        var items: List<City>,
        val onClick: (City) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        fun submit(newItems: List<City>) {
            items = newItems
            notifyDataSetChanged()
        }

        class VH(val binding: com.futureclock.app.databinding.ItemCityPickerBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(com.futureclock.app.databinding.ItemCityPickerBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val city = items[position]
            holder.binding.label.text = "${city.flag} ${city.name} — ${city.country}"
            holder.binding.root.setOnClickListener { onClick(city) }
        }
    }
}
