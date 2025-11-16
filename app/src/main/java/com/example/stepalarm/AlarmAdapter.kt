package com.example.stepalarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlarmAdapter(
    private var alarms: List<Alarm>,
    private val onToggleEnabled: (Alarm) -> Unit,
    private val onDelete: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {
    
    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeText: TextView = itemView.findViewById(R.id.alarmTimeText)
        val repeatText: TextView = itemView.findViewById(R.id.alarmRepeatText)
        val enabledSwitch: Switch = itemView.findViewById(R.id.alarmEnabledSwitch)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteAlarmButton)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        
        holder.timeText.text = alarm.getTimeString()
        holder.repeatText.text = alarm.getRepeatDaysString()
        holder.enabledSwitch.isChecked = alarm.isEnabled
        
        holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != alarm.isEnabled) {
                onToggleEnabled(alarm)
            }
        }
        
        holder.deleteButton.setOnClickListener {
            onDelete(alarm)
        }
    }
    
    override fun getItemCount(): Int = alarms.size
    
    fun updateAlarms(newAlarms: List<Alarm>) {
        alarms = newAlarms
        notifyDataSetChanged()
    }
}

