package com.futureclock.app.ui.world

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.data.db.WorldCityEntity
import com.futureclock.app.data.tz.City
import com.futureclock.app.data.tz.CityCatalog
import com.futureclock.app.data.tz.Country
import com.futureclock.app.databinding.ActivityWorldPickerBinding
import com.futureclock.app.databinding.ItemCityPickerBinding
import com.futureclock.app.ui.common.UiFeedback
import com.futureclock.app.widget.WidgetUpdateScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone

/**
 * Shared two-stage country/place picker.
 *
 * World Clock persists the selected place. Alarm editing requests RESULT_MODE_ALARM and receives
 * a validated place/time-zone result without coupling alarm data to the disposable catalog file.
 */
class WorldPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorldPickerBinding
    private lateinit var adapter: Adapter
    private lateinit var catalog: CityCatalog
    private var searchJob: Job? = null
    private var isSaving = false
    private var selectedCountryCode: String? = null
    private var selectedCountryName: String? = null
    private var queryGeneration = 0L
    private val validTimeZoneIds by lazy { TimeZone.getAvailableIDs().toHashSet() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorldPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedCountryCode = savedInstanceState?.getString(STATE_COUNTRY_CODE)
        selectedCountryName = savedInstanceState?.getString(STATE_COUNTRY_NAME)
        catalog = CityCatalog.get(this)
        adapter = Adapter(::onRowClicked)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.btnBack.setOnClickListener { showCountries() }
        onBackPressedDispatcher.addCallback(this) {
            if (selectedCountryCode != null) showCountries() else finish()
        }

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadRows(s?.toString().orEmpty())
            }
        })

        updateStageHeader()
        loadRows("")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_COUNTRY_CODE, selectedCountryCode)
        outState.putString(STATE_COUNTRY_NAME, selectedCountryName)
        super.onSaveInstanceState(outState)
    }

    private fun showCountries() {
        selectedCountryCode = null
        selectedCountryName = null
        updateStageHeader()
        clearSearchOrLoad()
    }

    private fun showCountry(country: Country) {
        selectedCountryCode = country.code
        selectedCountryName = country.name
        updateStageHeader()
        clearSearchOrLoad()
    }

    private fun clearSearchOrLoad() {
        if (binding.search.text.isNullOrEmpty()) loadRows("")
        else binding.search.text?.clear()
    }

    private fun updateStageHeader() {
        val countryName = selectedCountryName
        if (countryName == null) {
            binding.btnBack.visibility = View.GONE
            binding.screenTitle.setText(R.string.place_choose_country)
            binding.screenSubtitle.setText(R.string.place_country_subtitle)
            binding.searchLayout.hint = getString(R.string.place_country_search_hint)
            binding.catalogSummary.setText(R.string.world_catalog_summary)
        } else {
            binding.btnBack.visibility = View.VISIBLE
            binding.screenTitle.text = getString(R.string.place_choose_city, countryName)
            binding.screenSubtitle.setText(R.string.place_city_subtitle)
            binding.searchLayout.hint = getString(R.string.place_city_search_hint, countryName)
            binding.catalogSummary.text = selectedCountryCode
        }
    }

    private fun loadRows(query: String) {
        val generation = ++queryGeneration
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            if (query.isNotBlank()) delay(180)
            binding.progress.visibility = View.VISIBLE
            binding.emptyResults.visibility = View.GONE
            val rows = try {
                withContext(Dispatchers.IO) {
                    val countryCode = selectedCountryCode
                    if (countryCode == null) {
                        buildList<PickerRow> {
                            addAll(catalog.countries(query).take(COUNTRY_LIMIT).map(PickerRow::CountryRow))
                            if (query.isNotBlank()) {
                                addAll(catalog.search(query, DIRECT_PLACE_LIMIT).map(PickerRow::CityRow))
                            }
                        }
                    } else {
                        catalog.search(query, PLACE_LIMIT, countryCode).map(PickerRow::CityRow)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                if (generation != queryGeneration) return@launch
                binding.progress.visibility = View.GONE
                adapter.submit(emptyList())
                binding.emptyResults.setText(R.string.place_picker_error)
                binding.emptyResults.visibility = View.VISIBLE
                return@launch
            }
            if (generation != queryGeneration) return@launch
            binding.progress.visibility = View.GONE
            adapter.submit(rows)
            binding.emptyResults.setText(R.string.world_no_results)
            binding.emptyResults.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun onRowClicked(row: PickerRow) {
        when (row) {
            is PickerRow.CountryRow -> showCountry(row.country)
            is PickerRow.CityRow -> selectCity(row.city)
        }
    }

    private fun selectCity(city: City) {
        if (
            city.id <= 0L ||
            city.name.isBlank() ||
            city.countryCode.length != 2 ||
            city.tzId !in validTimeZoneIds
        ) {
            UiFeedback.show(binding.root, R.string.place_invalid)
            return
        }
        if (intent.getStringExtra(EXTRA_MODE) == MODE_ALARM) {
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(EXTRA_PLACE_ID, city.id)
                    .putExtra(EXTRA_PLACE_NAME, city.name)
                    .putExtra(EXTRA_COUNTRY_NAME, city.country)
                    .putExtra(EXTRA_FLAG, city.flag)
                    .putExtra(EXTRA_TIMEZONE_ID, city.tzId)
            )
            finish()
            return
        }
        addWorldCity(city)
    }

    private fun addWorldCity(city: City) {
        if (isSaving) return
        isSaving = true
        searchJob?.cancel()
        binding.search.isEnabled = false
        binding.recycler.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        binding.emptyResults.visibility = View.GONE
        lifecycleScope.launch {
            val app = applicationContext as FutureClockApp
            runCatching {
                withContext(Dispatchers.IO) {
                    val dao = app.database.worldCityDao()
                    dao.insert(
                        WorldCityEntity(
                            locationId = city.id,
                            tzId = city.tzId,
                            displayName = city.name,
                            country = city.areaLabel,
                            flag = city.flag,
                            sortOrder = dao.nextSortOrder()
                        )
                    )
                    WidgetUpdateScheduler.refreshAll(applicationContext)
                }
            }.onSuccess {
                setResult(RESULT_OK)
                finish()
            }.onFailure {
                isSaving = false
                binding.search.isEnabled = true
                binding.recycler.isEnabled = true
                binding.progress.visibility = View.GONE
                UiFeedback.show(binding.root, R.string.world_add_error)
            }
        }
    }

    private sealed interface PickerRow {
        data class CountryRow(val country: Country) : PickerRow
        data class CityRow(val city: City) : PickerRow
    }

    private class Adapter(
        private val onClick: (PickerRow) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<PickerRow> = emptyList()

        fun submit(newItems: List<PickerRow>) {
            val oldItems = items
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldItems.size
                override fun getNewListSize(): Int = newItems.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val old = oldItems[oldItemPosition]
                    val new = newItems[newItemPosition]
                    return when {
                        old is PickerRow.CountryRow && new is PickerRow.CountryRow ->
                            old.country.code == new.country.code
                        old is PickerRow.CityRow && new is PickerRow.CityRow ->
                            old.city.id == new.city.id
                        else -> false
                    }
                }

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean = oldItems[oldItemPosition] == newItems[newItemPosition]
            })
            items = newItems
            diff.dispatchUpdatesTo(this)
        }

        class VH(val binding: ItemCityPickerBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(
            ItemCityPickerBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]
            val context = holder.itemView.context
            when (row) {
                is PickerRow.CountryRow -> {
                    holder.binding.leading.text = row.country.flag
                    holder.binding.title.text = row.country.name
                    val count = context.resources.getQuantityString(
                        R.plurals.place_count,
                        row.country.placeCount,
                        row.country.placeCount
                    )
                    holder.binding.subtitle.text = count
                    holder.binding.trailing.text = "›"
                    holder.binding.root.contentDescription = context.getString(
                        R.string.place_country_row_description,
                        row.country.name,
                        count
                    )
                }
                is PickerRow.CityRow -> {
                    holder.binding.leading.text = row.city.flag
                    holder.binding.title.text = row.city.name
                    holder.binding.subtitle.text = context.getString(
                        R.string.place_area_timezone,
                        row.city.areaLabel,
                        row.city.tzId
                    )
                    holder.binding.trailing.text = ""
                    holder.binding.root.contentDescription = context.getString(
                        R.string.place_city_row_description,
                        row.city.name,
                        row.city.areaLabel,
                        row.city.tzId
                    )
                }
            }
            holder.binding.root.setOnClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) onClick(items[currentPosition])
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "picker_mode"
        const val MODE_ALARM = "alarm"
        const val EXTRA_PLACE_ID = "place_id"
        const val EXTRA_PLACE_NAME = "place_name"
        const val EXTRA_COUNTRY_NAME = "country_name"
        const val EXTRA_FLAG = "flag"
        const val EXTRA_TIMEZONE_ID = "timezone_id"

        private const val STATE_COUNTRY_CODE = "country_code"
        private const val STATE_COUNTRY_NAME = "country_name"
        private const val COUNTRY_LIMIT = 250
        private const val DIRECT_PLACE_LIMIT = 80
        private const val PLACE_LIMIT = 250
    }
}
