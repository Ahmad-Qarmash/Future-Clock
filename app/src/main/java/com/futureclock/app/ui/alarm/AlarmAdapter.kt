package com.futureclock.app.ui.alarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futureclock.app.R
import com.futureclock.app.data.db.AlarmEntity
import com.futureclock.app.databinding.ItemAlarmBinding
import com.futureclock.app.util.AlarmMath
import com.futureclock.app.util.TimeFormat
import java.util.Calendar

class AlarmAdapter(
    private val onToggle: (AlarmEntity, Boolean) -> Unit,
    private val onClick: (AlarmEntity) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.VH>() {

    var use24h: Boolean = true
    private val items = mutableListOf<AlarmEntity>()

    fun submit(list: List<AlarmEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemAlarmBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val alarm = items[position]
        holder.binding.alarmTime.text = TimeFormat.formatTime(use24h, alarm.hour, alarm.minute)
        holder.binding.alarmLabel.text = alarm.label
        holder.binding.alarmLabel.visibility = if (alarm.label.isBlank()) View.GONE else View.VISIBLE

        val days = AlarmMath.formatDays(alarm.daysOfWeek)
        val nextMs = AlarmMath.nextTrigger(System.currentTimeMillis(), alarm.hour, alarm.minute, alarm.daysOfWeek)
        val cal = Calendar.getInstance().apply { timeInMillis = nextMs }
        val tomorrow = isTomorrow(cal)
        val ctx = holder.itemView.context
        val whenText = buildString {
            append(if (tomorrow) ctx.getString(R.string.alarm_tomorrow)
                   else ctx.getString(R.string.alarm_today_at, TimeFormat.formatTime(use24h, alarm.hour, alarm.minute)))
            if (days.isNotEmpty()) append(" · ").append(days)
        }
        holder.binding.alarmWhen.text = whenText
        holder.binding.alarmSwitch.setOnCheckedChangeListener(null)
        holder.binding.alarmSwitch.isChecked = alarm.enabled
        holder.binding.alarmSwitch.setOnCheckedChangeListener { _, checked -> onToggle(alarm, checked) }
        holder.binding.root.setOnClickListener { onClick(alarm) }
    }

    private fun isTomorrow(cal: Calendar): Boolean {
        val now = Calendar.getInstance()
        return now.get(Calendar.DAY_OF_YEAR) != cal.get(Calendar.DAY_OF_YEAR) &&
                now.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
    }
}
