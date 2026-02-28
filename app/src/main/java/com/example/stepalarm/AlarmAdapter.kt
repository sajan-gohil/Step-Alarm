package com.example.stepalarm

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
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
        val cardView: MaterialCardView = itemView as MaterialCardView
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
        val palette = ThemeManager.getSelectedPalette(holder.itemView.context)
        
        holder.timeText.text = alarm.getTimeString()
        holder.repeatText.text = alarm.getRepeatDaysString()
        
        // Apply palette colors to card
        holder.cardView.setCardBackgroundColor(palette.cardBackground)
        holder.cardView.strokeColor = palette.cardStroke

        // Text colors
        holder.timeText.setTextColor(palette.textPrimary)
        holder.repeatText.setTextColor(palette.subtleAccent)

        // Switch track and thumb colors
        val trackStates = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(palette.switchTrackOn, palette.switchTrackOff)
        )
        holder.enabledSwitch.trackTintList = trackStates
        holder.enabledSwitch.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(palette.accent, palette.subtleAccent)
        )

        // Delete button tint
        holder.deleteButton.setColorFilter(palette.subtleAccent, PorterDuff.Mode.SRC_IN)

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
            // Animate the delete action
            holder.cardView.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(200)
                .withEndAction {
                    onDelete(alarm)
                    holder.cardView.alpha = 1f
                    holder.cardView.scaleX = 1f
                    holder.cardView.scaleY = 1f
                }
                .start()
        }

        // Entrance animation for items
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 30f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setStartDelay((position * 50).toLong().coerceAtMost(300))
            .start()
    }
    
    override fun getItemCount(): Int = alarms.size
    
    fun updateAlarms(newAlarms: List<Alarm>) {
        alarms = newAlarms
        notifyDataSetChanged()
    }
}

