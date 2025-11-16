package com.example.stepalarm

import java.util.Calendar

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val isRepeating: Boolean,
    val repeatDays: Set<Int>, // Set of Calendar.DAY_OF_WEEK values (Calendar.SUNDAY = 1, etc.)
    val isEnabled: Boolean
) {
    fun getTimeString(): String {
        return String.format("%02d:%02d", hour, minute)
    }
    
    fun getRepeatDaysString(): String {
        if (!isRepeating || repeatDays.isEmpty()) {
            return "No repeat"
        }
        
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val sortedDays = repeatDays.sorted()
        return sortedDays.joinToString(", ") { dayNames[it - 1] }
    }
    
    fun getNextAlarmTime(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        if (isRepeating && repeatDays.isNotEmpty()) {
            // Find the next occurrence based on repeat days
            val currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val currentTime = Calendar.getInstance().timeInMillis
            
            // Check if today is a repeat day and time hasn't passed
            if (repeatDays.contains(currentDayOfWeek) && calendar.timeInMillis > currentTime) {
                return calendar.timeInMillis
            }
            
            // Find next repeat day
            var daysToAdd = 1
            var found = false
            while (daysToAdd <= 7 && !found) {
                val nextDay = (currentDayOfWeek + daysToAdd - 1) % 7 + 1
                if (repeatDays.contains(nextDay)) {
                    calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                    found = true
                } else {
                    daysToAdd++
                }
            }
            
            if (!found) {
                // If no repeat day found in next 7 days, find the earliest one
                val earliestDay = repeatDays.minOrNull() ?: currentDayOfWeek
                val daysUntilEarliest = if (earliestDay >= currentDayOfWeek) {
                    earliestDay - currentDayOfWeek
                } else {
                    7 - currentDayOfWeek + earliestDay
                }
                calendar.add(Calendar.DAY_OF_YEAR, daysUntilEarliest)
            }
        } else {
            // One-time alarm: if time has passed, set for tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        return calendar.timeInMillis
    }
}

