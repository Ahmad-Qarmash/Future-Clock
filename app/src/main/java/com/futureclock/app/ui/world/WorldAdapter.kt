package com.futureclock.app.ui.world

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.R
import com.futureclock.app.data.db.WorldCityEntity
import com.futureclock.app.databinding.ItemWorldCityBinding
import com.futureclock.app.util.AlarmMath
import com.futureclock.app.util.TimeFormat
import java.util.Calendar
import java.util.Collections
import java.util.TimeZone

class WorldAdapter(
    private val onRemove: (WorldCityEntity) -> Unit
) : RecyclerView.Adapter<WorldAdapter.VH>() {

    private val items = mutableListOf<WorldCityEntity>()

    fun submit(newItems: List<WorldCityEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun snapshot(): List<WorldCityEntity> = items.toList()

    fun swap(from: Int, to: Int) {
        if (from < to) Collections.swap(items, from, to)
        else Collections.swap(items, to, from)
        notifyItemMoved(from, to)
    }

    class VH(val binding: ItemWorldCityBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemWorldCityBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val city = items[position]
        val zone = AlarmMath.timeZone(city.tzId)
        val cal = Calendar.getInstance(zone)
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        holder.binding.cityFlag.text = city.flag
        holder.binding.cityName.text = city.displayName
        holder.binding.cityCountry.text = city.country
        holder.binding.cityTime.text = TimeFormat.formatTime(use24h = true, hour = h, minute = m)
        val deltaDays = TimeFormat.dayDelta(zone)
        holder.binding.cityDayDiff.text = when {
            deltaDays > 0 -> holder.itemView.context.getString(R.string.world_day, deltaDays)
            deltaDays < 0 -> holder.itemView.context.getString(R.string.world_yesterday, -deltaDays)
            else -> holder.itemView.context.getString(R.string.world_today)
        }
        holder.binding.btnRemove.setOnClickListener { onRemove(city) }
    }
}
