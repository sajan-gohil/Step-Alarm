package com.example.stepalarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.recyclerview.widget.RecyclerView

class AlarmAdapter(
    private var alarms: List<Alarm>,
    private val onToggleEnabled: (Alarm) -> Unit,
    private val onDelete: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {
    
    // Flag to prevent listener callbacks during binding
    private var isBinding = false
    
    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeText: TextView = itemView.findViewById(R.id.alarmTimeText)
        val repeatText: TextView = itemView.findViewById(R.id.alarmRepeatText)
        val enabledSwitch: SwitchMaterial = itemView.findViewById(R.id.alarmEnabledSwitch)
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
        
        // Use flag to prevent listener from firing during binding
        isBinding = true
        holder.enabledSwitch.isChecked = alarm.isEnabled
        isBinding = false
        
        // Set listener - will check isBinding flag to avoid callback during layout
        holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding && isChecked != alarm.isEnabled) {
                // Post to handler to ensure we're not in layout phase
                holder.itemView.post {
                    onToggleEnabled(alarm)
                }
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

