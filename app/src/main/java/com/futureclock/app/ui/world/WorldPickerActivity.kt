package com.futureclock.app.ui.world

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.FutureClockApp
import com.futureclock.app.MainActivity
import com.futureclock.app.R
import com.futureclock.app.data.db.WorldCityEntity
import com.futureclock.app.data.tz.City
import com.futureclock.app.data.tz.CityCatalog
import com.futureclock.app.data.tz.Country
import com.futureclock.app.databinding.ActivityWorldPickerBinding
import com.futureclock.app.databinding.ItemCityPickerBinding
import com.futureclock.app.databinding.ItemPickerSectionBinding
import com.futureclock.app.databinding.ItemTrackedPlaceBinding
import com.futureclock.app.ui.common.UiFeedback
import com.futureclock.app.util.TimeFormat
import com.futureclock.app.widget.WidgetUpdateScheduler
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared two-stage country/place picker.
 *
 * Alarm mode promotes valid, distinct World Clock rows before catalog results. Selection returns
 * a place snapshot and IANA zone; alarms never retain a mutable link to World Clock.
 */
class WorldPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorldPickerBinding
    private lateinit var adapter: Adapter
    private lateinit var catalog: CityCatalog
    private var searchJob: Job? = null
    private var isSaving = false
    private var selectionHandled = false
    private var selectedCountryCode: String? = null
    private var selectedCountryName: String? = null
    private var queryGeneration = 0L
    private val validTimeZoneIds by lazy { TimeZone.getAvailableIDs().toHashSet() }
    private val isAlarmMode by lazy { intent.getStringExtra(EXTRA_MODE) == MODE_ALARM }
    private val selectedPlaceId by lazy { intent.getLongExtra(EXTRA_SELECTED_PLACE_ID, 0L) }
    private val selectedTimeZoneId by lazy {
        intent.getStringExtra(EXTRA_SELECTED_TIMEZONE_ID).orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorldPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedCountryCode = savedInstanceState?.getString(STATE_COUNTRY_CODE)
        selectedCountryName = savedInstanceState?.getString(STATE_COUNTRY_NAME)
        catalog = CityCatalog.get(this)
        adapter = Adapter(
            onClick = ::onRowClicked,
            onManageWorldClocks = ::manageWorldClocks,
            selectedPlaceId = selectedPlaceId,
            selectedTimeZoneId = selectedTimeZoneId
        )
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
        if (binding.search.text.isNullOrEmpty()) loadRows("") else binding.search.text?.clear()
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
        binding.recycler.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        binding.emptyResults.visibility = View.GONE
        searchJob = lifecycleScope.launch {
            if (query.isNotBlank()) delay(180)
            val rows = try {
                withContext(Dispatchers.IO) { buildRows(query) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SQLiteException) {
                showLoadError(generation, error)
                return@launch
            } catch (error: IOException) {
                showLoadError(generation, error)
                return@launch
            } catch (error: IllegalStateException) {
                showLoadError(generation, error)
                return@launch
            }
            if (generation != queryGeneration) return@launch
            binding.progress.visibility = View.GONE
            adapter.submit(rows)
            binding.recycler.isEnabled = true
            binding.emptyResults.setText(R.string.world_no_results)
            binding.emptyResults.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private suspend fun buildRows(query: String): List<PickerRow> {
        val countryCode = selectedCountryCode
        if (countryCode != null) {
            return catalog.search(query, PLACE_LIMIT, countryCode).map(PickerRow::CityRow)
        }
        if (!isAlarmMode) {
            return buildList {
                addAll(catalog.countries(query).take(COUNTRY_LIMIT).map(PickerRow::CountryRow))
                if (query.isNotBlank()) {
                    addAll(catalog.search(query, DIRECT_PLACE_LIMIT).map(PickerRow::CityRow))
                }
            }
        }

        val app = applicationContext as FutureClockApp
        val tracked = app.database.worldCityDao().getAll()
            .filter { it.locationId > 0L && it.tzId in validTimeZoneIds }
            .distinctBy(WorldCityEntity::locationId)
        val normalizedQuery = normalize(query)
        val matchingTracked = if (normalizedQuery.isBlank()) tracked else tracked.filter { city ->
            normalize("${city.displayName} ${city.country} ${city.tzId}").contains(normalizedQuery)
        }
        val trackedIds = tracked.mapTo(HashSet(), WorldCityEntity::locationId)

        return buildList {
            if (matchingTracked.isNotEmpty()) {
                add(
                    PickerRow.SectionRow(
                        title = R.string.place_your_world_clocks,
                        subtitle = R.string.place_your_world_clocks_subtitle,
                        showManage = true
                    )
                )
                addAll(matchingTracked.map(PickerRow::TrackedRow))
            }
            if (normalizedQuery.isBlank()) {
                if (tracked.isNotEmpty()) add(PickerRow.SectionRow(R.string.place_browse_all))
                addAll(catalog.countries().take(COUNTRY_LIMIT).map(PickerRow::CountryRow))
            } else {
                val countries = catalog.countries(query).take(COUNTRY_LIMIT)
                val places = catalog.search(query, DIRECT_PLACE_LIMIT)
                    .filterNot { it.id in trackedIds }
                if (countries.isNotEmpty() || places.isNotEmpty()) {
                    add(PickerRow.SectionRow(R.string.place_search_results))
                    addAll(countries.map(PickerRow::CountryRow))
                    addAll(places.map(PickerRow::CityRow))
                }
            }
        }
    }

    private fun showLoadError(generation: Long, error: Exception) {
        if (generation != queryGeneration) return
        Log.e(TAG, "Unable to load the offline place catalog", error)
        binding.progress.visibility = View.GONE
        adapter.submit(emptyList())
        binding.recycler.isEnabled = true
        binding.emptyResults.setText(R.string.place_picker_error)
        binding.emptyResults.visibility = View.VISIBLE
    }

    private fun onRowClicked(row: PickerRow) {
        when (row) {
            is PickerRow.CountryRow -> showCountry(row.country)
            is PickerRow.CityRow -> selectCity(row.city)
            is PickerRow.TrackedRow -> selectTrackedCity(row.city)
            is PickerRow.SectionRow -> Unit
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
        if (isAlarmMode) {
            returnAlarmSelection(
                city.id,
                city.name,
                city.country,
                city.flag,
                city.tzId,
                fromWorldClock = false
            )
        } else {
            addWorldCity(city)
        }
    }

    private fun selectTrackedCity(city: WorldCityEntity) {
        if (
            city.locationId <= 0L ||
            city.displayName.isBlank() ||
            city.tzId !in validTimeZoneIds
        ) {
            UiFeedback.show(binding.root, R.string.place_invalid)
            return
        }
        returnAlarmSelection(
            city.locationId,
            city.displayName,
            city.country,
            city.flag,
            city.tzId,
            fromWorldClock = true
        )
    }

    private fun returnAlarmSelection(
        placeId: Long,
        name: String,
        country: String,
        flag: String,
        timeZoneId: String,
        fromWorldClock: Boolean
    ) {
        if (selectionHandled) return
        selectionHandled = true
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_PLACE_ID, placeId)
                .putExtra(EXTRA_PLACE_NAME, name)
                .putExtra(EXTRA_COUNTRY_NAME, country)
                .putExtra(EXTRA_FLAG, flag)
                .putExtra(EXTRA_TIMEZONE_ID, timeZoneId)
                .putExtra(EXTRA_SOURCE_TRACKED, fromWorldClock)
        )
        finish()
    }

    private fun manageWorldClocks() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DEFAULT_TAB, R.id.nav_world)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    private fun addWorldCity(city: City) {
        if (isSaving) return
        isSaving = true
        searchJob?.cancel()
        binding.search.isEnabled = false
        binding.recycler.isEnabled = false
        binding.progress.visibility = View.GONE
        binding.emptyResults.visibility = View.GONE
        binding.savingStatus.text = getString(R.string.world_adding_named_place, city.name)
        binding.savingOverlay.visibility = View.VISIBLE
        lifecycleScope.launch {
            val app = applicationContext as FutureClockApp
            try {
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
                }
                app.applicationScope.launch(Dispatchers.Default) {
                    WidgetUpdateScheduler.refreshAll(applicationContext)
                }
                setResult(RESULT_OK)
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SQLiteException) {
                Log.e(TAG, "Unable to save World Clock location", error)
                isSaving = false
                binding.search.isEnabled = true
                binding.recycler.isEnabled = true
                binding.savingOverlay.visibility = View.GONE
                UiFeedback.show(binding.root, R.string.world_add_error)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to save World Clock location", error)
                isSaving = false
                binding.search.isEnabled = true
                binding.recycler.isEnabled = true
                binding.savingOverlay.visibility = View.GONE
                UiFeedback.show(binding.root, R.string.world_add_error)
            }
        }
    }

    private sealed interface PickerRow {
        data class SectionRow(
            @StringRes val title: Int,
            @StringRes val subtitle: Int? = null,
            val showManage: Boolean = false
        ) : PickerRow

        data class CountryRow(val country: Country) : PickerRow
        data class CityRow(val city: City) : PickerRow
        data class TrackedRow(val city: WorldCityEntity) : PickerRow
    }

    private class Adapter(
        private val onClick: (PickerRow) -> Unit,
        private val onManageWorldClocks: () -> Unit,
        private val selectedPlaceId: Long,
        private val selectedTimeZoneId: String
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<PickerRow> = emptyList()

        fun submit(newItems: List<PickerRow>) {
            val oldItems = items
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldItems.size
                override fun getNewListSize(): Int = newItems.size
                override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                    val old = oldItems[oldPosition]
                    val new = newItems[newPosition]
                    return when {
                        old is PickerRow.CountryRow && new is PickerRow.CountryRow ->
                            old.country.code == new.country.code
                        old is PickerRow.CityRow && new is PickerRow.CityRow ->
                            old.city.id == new.city.id
                        old is PickerRow.TrackedRow && new is PickerRow.TrackedRow ->
                            old.city.locationId == new.city.locationId
                        old is PickerRow.SectionRow && new is PickerRow.SectionRow ->
                            old.title == new.title
                        else -> false
                    }
                }

                override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
                    oldItems[oldPosition] == newItems[newPosition]
            })
            items = newItems
            diff.dispatchUpdatesTo(this)
        }

        class PlaceVH(val binding: ItemCityPickerBinding) : RecyclerView.ViewHolder(binding.root)
        class SectionVH(val binding: ItemPickerSectionBinding) : RecyclerView.ViewHolder(binding.root)
        class TrackedVH(val binding: ItemTrackedPlaceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is PickerRow.SectionRow -> VIEW_SECTION
            is PickerRow.TrackedRow -> VIEW_TRACKED
            else -> VIEW_PLACE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_SECTION -> SectionVH(ItemPickerSectionBinding.inflate(inflater, parent, false))
                VIEW_TRACKED -> TrackedVH(ItemTrackedPlaceBinding.inflate(inflater, parent, false))
                else -> PlaceVH(ItemCityPickerBinding.inflate(inflater, parent, false))
            }
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = items[position]
            val context = holder.itemView.context
            when (row) {
                is PickerRow.CountryRow -> {
                    val binding = (holder as PlaceVH).binding
                    binding.leading.text = row.country.flag
                    binding.title.text = row.country.name
                    val count = context.resources.getQuantityString(
                        R.plurals.place_count,
                        row.country.placeCount,
                        row.country.placeCount
                    )
                    binding.subtitle.text = count
                    binding.trailing.setText(R.string.navigation_chevron)
                    binding.root.isSelected = false
                    binding.root.contentDescription = context.getString(
                        R.string.place_country_row_description,
                        row.country.name,
                        count
                    )
                }

                is PickerRow.CityRow -> {
                    val binding = (holder as PlaceVH).binding
                    binding.leading.text = row.city.flag
                    binding.title.text = row.city.name
                    binding.subtitle.text = context.getString(
                        R.string.place_area_timezone,
                        row.city.areaLabel,
                        row.city.tzId
                    )
                    binding.trailing.text = ""
                    binding.root.isSelected =
                        row.city.id == selectedPlaceId ||
                            (selectedPlaceId == 0L && row.city.tzId == selectedTimeZoneId)
                    binding.root.contentDescription = context.getString(
                        R.string.place_city_row_description,
                        row.city.name,
                        row.city.areaLabel,
                        row.city.tzId
                    )
                }

                is PickerRow.TrackedRow -> {
                    val binding = (holder as TrackedVH).binding
                    val zone = TimeZone.getTimeZone(row.city.tzId)
                    val day = dayLabel(context, TimeFormat.dayDelta(zone))
                    binding.flag.text = row.city.flag
                    binding.title.text = row.city.displayName
                    binding.country.text = row.city.country
                    binding.time.text = TimeFormat.formatTime(
                        zone,
                        DateFormat.is24HourFormat(context),
                        false
                    )
                    binding.meta.text = context.getString(
                        R.string.place_offset_day,
                        TimeFormat.formatOffset(zone),
                        day
                    )
                    binding.root.isChecked =
                        row.city.locationId == selectedPlaceId ||
                            (selectedPlaceId == 0L && row.city.tzId == selectedTimeZoneId)
                    binding.root.contentDescription = context.getString(
                        R.string.place_tracked_description,
                        row.city.displayName,
                        row.city.country,
                        binding.time.text,
                        binding.meta.text
                    )
                }

                is PickerRow.SectionRow -> {
                    val binding = (holder as SectionVH).binding
                    binding.title.setText(row.title)
                    if (row.subtitle == null) {
                        binding.subtitle.visibility = View.GONE
                    } else {
                        binding.subtitle.setText(row.subtitle)
                        binding.subtitle.visibility = View.VISIBLE
                    }
                    binding.action.visibility = if (row.showManage) View.VISIBLE else View.GONE
                    binding.action.setOnClickListener {
                        if (row.showManage) onManageWorldClocks()
                    }
                }
            }
            holder.itemView.setOnClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (
                    currentPosition != RecyclerView.NO_POSITION &&
                    items[currentPosition] !is PickerRow.SectionRow
                ) {
                    onClick(items[currentPosition])
                }
            }
        }

        companion object {
            private const val VIEW_PLACE = 0
            private const val VIEW_SECTION = 1
            private const val VIEW_TRACKED = 2

            private fun dayLabel(context: android.content.Context, delta: Int): String = when {
                delta > 0 -> context.getString(R.string.day_tomorrow)
                delta < 0 -> context.getString(R.string.day_yesterday)
                else -> context.getString(R.string.day_today)
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
        const val EXTRA_SOURCE_TRACKED = "source_tracked"
        const val EXTRA_SELECTED_PLACE_ID = "selected_place_id"
        const val EXTRA_SELECTED_TIMEZONE_ID = "selected_timezone_id"

        private const val STATE_COUNTRY_CODE = "country_code"
        private const val STATE_COUNTRY_NAME = "country_name"
        private const val COUNTRY_LIMIT = 250
        private const val DIRECT_PLACE_LIMIT = 80
        private const val PLACE_LIMIT = 250
        private const val TAG = "WorldPicker"

        private fun normalize(value: String): String = Normalizer.normalize(
            value.trim().lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
    }
}
