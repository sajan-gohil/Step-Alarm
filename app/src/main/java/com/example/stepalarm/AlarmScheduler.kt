package com.example.stepalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    @JvmStatic
    fun scheduleAlarm(context: Context, alarm: Alarm) {
        LogFileWriter.logInfo(context, TAG, "scheduleAlarm() called for alarm ${alarm.id} (${alarm.hour}:${alarm.minute}, repeating=${alarm.isRepeating}, enabled=${alarm.isEnabled})")
        
        if (!alarm.isEnabled) {
            LogFileWriter.logInfo(context, TAG, "Alarm ${alarm.id} is disabled, cancelling instead")
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
                
                val requestCode = (alarm.id * 10 + dayOfWeek).toInt()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode, // Unique request code for each day
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    LogFileWriter.logInfo(context, TAG, "Scheduled repeating alarm ${alarm.id} for day $dayOfWeek, requestCode=$requestCode, triggerAt=${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(calendar.timeInMillis))}")
                } catch (e: Exception) {
                    LogFileWriter.logError(context, TAG, "Failed to schedule alarm ${alarm.id} for day $dayOfWeek", e)
                }
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
            
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                LogFileWriter.logInfo(context, TAG, "Scheduled one-time alarm ${alarm.id}, triggerAt=${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(calendar.timeInMillis))}")
            } catch (e: Exception) {
                LogFileWriter.logError(context, TAG, "Failed to schedule one-time alarm ${alarm.id}", e)
            }
        }
    }
    
    @JvmStatic
    fun cancelAlarm(context: Context, alarm: Alarm) {
        LogFileWriter.logInfo(context, TAG, "cancelAlarm() called for alarm ${alarm.id}")
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
        LogFileWriter.logInfo(context, TAG, "rescheduleAllAlarms() called")
        val alarmDatabase = AlarmDatabase(context)
        val alarms = alarmDatabase.getAllAlarms()
        LogFileWriter.logInfo(context, TAG, "Found ${alarms.size} alarms to reschedule")
        
        alarms.forEach { alarm ->
            if (alarm.isEnabled) {
                scheduleAlarm(context, alarm)
            }
        }
        LogFileWriter.logInfo(context, TAG, "rescheduleAllAlarms() completed")
    }
}

