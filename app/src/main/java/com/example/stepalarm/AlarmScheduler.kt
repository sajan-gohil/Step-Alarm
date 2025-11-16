package com.example.stepalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object AlarmScheduler {
    @JvmStatic
    fun scheduleAlarm(context: Context, alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancelAlarm(context, alarm)
            return
        }
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        if (alarm.isRepeating && alarm.repeatDays.isNotEmpty()) {
            // Schedule for each repeat day
            val currentDayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
            val currentTime = System.currentTimeMillis()
            
            alarm.repeatDays.forEach { dayOfWeek ->
                val calendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, alarm.hour)
                    set(java.util.Calendar.MINUTE, alarm.minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    
                    // Calculate days until the target day of week
                    var daysUntilTarget = dayOfWeek - currentDayOfWeek
                    if (daysUntilTarget < 0) {
                        daysUntilTarget += 7
                    }
                    
                    // If it's today but time has passed, schedule for next week
                    if (daysUntilTarget == 0 && timeInMillis <= currentTime) {
                        daysUntilTarget = 7
                    }
                    
                    add(java.util.Calendar.DAY_OF_YEAR, daysUntilTarget)
                }
                
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = "com.example.stepalarm.ALARM_TRIGGERED"
                    putExtra("alarm_id", alarm.id)
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    (alarm.id * 10 + dayOfWeek).toInt(), // Unique request code for each day
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } else {
            // One-time alarm
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, alarm.hour)
                set(java.util.Calendar.MINUTE, alarm.minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "com.example.stepalarm.ALARM_TRIGGERED"
                putExtra("alarm_id", alarm.id)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
    
    @JvmStatic
    fun cancelAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        if (alarm.isRepeating && alarm.repeatDays.isNotEmpty()) {
            // Cancel all repeat day alarms
            alarm.repeatDays.forEach { dayOfWeek ->
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = "com.example.stepalarm.ALARM_TRIGGERED"
                    putExtra("alarm_id", alarm.id)
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    (alarm.id * 10 + dayOfWeek).toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                alarmManager.cancel(pendingIntent)
            }
        } else {
            // Cancel one-time alarm
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "com.example.stepalarm.ALARM_TRIGGERED"
                putExtra("alarm_id", alarm.id)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(pendingIntent)
        }
    }
    
    @JvmStatic
    fun rescheduleAllAlarms(context: Context) {
        val alarmDatabase = AlarmDatabase(context)
        val alarms = alarmDatabase.getAllAlarms()
        
        alarms.forEach { alarm ->
            if (alarm.isEnabled) {
                scheduleAlarm(context, alarm)
            }
        }
    }
}

