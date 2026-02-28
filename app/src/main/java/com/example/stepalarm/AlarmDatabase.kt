package com.example.stepalarm

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AlarmDatabase(private val context: Context) {
    private val TAG = "AlarmDatabase"
    private val prefs: SharedPreferences = context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "alarms_list"
    
    init {
        LogFileWriter.logInfo(context, TAG, "AlarmDatabase initialized")
    }
    
    fun saveAlarm(alarm: Alarm): Long {
        LogFileWriter.logInfo(context, TAG, "saveAlarm() called for alarm id=${alarm.id}")
        try {
            val alarms = getAllAlarms().toMutableList()
            val existingIndex = alarms.indexOfFirst { it.id == alarm.id && alarm.id != 0L }
            
            val savedAlarm = if (existingIndex >= 0) {
                alarms[existingIndex] = alarm
                LogFileWriter.logInfo(context, TAG, "Updated existing alarm at index $existingIndex")
                alarm
            } else {
                val newId = if (alarms.isEmpty()) 1L else (alarms.maxOfOrNull { it.id } ?: 0L) + 1
                val newAlarm = alarm.copy(id = newId)
                alarms.add(newAlarm)
                LogFileWriter.logInfo(context, TAG, "Created new alarm with ID $newId")
                newAlarm
            }
            
            saveAlarms(alarms)
            return savedAlarm.id
        } catch (e: Exception) {
            LogFileWriter.logError(context, TAG, "Failed to save alarm", e)
            throw e
        }
    }
    
    fun deleteAlarm(alarmId: Long) {
        LogFileWriter.logInfo(context, TAG, "deleteAlarm() called for ID $alarmId")
        try {
            val alarms = getAllAlarms().toMutableList()
            alarms.removeAll { it.id == alarmId }
            saveAlarms(alarms)
            LogFileWriter.logInfo(context, TAG, "Alarm $alarmId deleted, ${alarms.size} alarms remaining")
        } catch (e: Exception) {
            LogFileWriter.logError(context, TAG, "Failed to delete alarm $alarmId", e)
            throw e
        }
    }
    
    fun getAllAlarms(): List<Alarm> {
        try {
            val json = prefs.getString(key, null) ?: return emptyList()
            val type = object : TypeToken<List<Alarm>>() {}.type
            val alarms: List<Alarm> = gson.fromJson(json, type) ?: emptyList()
            return alarms
        } catch (e: Exception) {
            LogFileWriter.logError(context, TAG, "Failed to deserialize alarms from SharedPreferences", e)
            return emptyList()
        }
    }
    
    fun getAlarm(alarmId: Long): Alarm? {
        return getAllAlarms().find { it.id == alarmId }
    }
    
    private fun saveAlarms(alarms: List<Alarm>) {
        val json = gson.toJson(alarms)
        prefs.edit().putString(key, json).apply()
    }
    
    fun toggleAlarm(alarmId: Long): Alarm? {
        LogFileWriter.logInfo(context, TAG, "toggleAlarm() called for ID $alarmId")
        val alarm = getAlarm(alarmId) ?: return null
        val updatedAlarm = alarm.copy(isEnabled = !alarm.isEnabled)
        saveAlarm(updatedAlarm)
        LogFileWriter.logInfo(context, TAG, "Alarm $alarmId toggled to enabled=${updatedAlarm.isEnabled}")
        return updatedAlarm
    }
}

