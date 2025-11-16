package com.example.stepalarm

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AlarmDatabase(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "alarms_list"
    
    fun saveAlarm(alarm: Alarm): Long {
        val alarms = getAllAlarms().toMutableList()
        val existingIndex = alarms.indexOfFirst { it.id == alarm.id && alarm.id != 0L }
        
        val savedAlarm = if (existingIndex >= 0) {
            alarms[existingIndex] = alarm
            alarm
        } else {
            val newId = if (alarms.isEmpty()) 1L else (alarms.maxOfOrNull { it.id } ?: 0L) + 1
            val newAlarm = alarm.copy(id = newId)
            alarms.add(newAlarm)
            newAlarm
        }
        
        saveAlarms(alarms)
        return savedAlarm.id
    }
    
    fun deleteAlarm(alarmId: Long) {
        val alarms = getAllAlarms().toMutableList()
        alarms.removeAll { it.id == alarmId }
        saveAlarms(alarms)
    }
    
    fun getAllAlarms(): List<Alarm> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<Alarm>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    fun getAlarm(alarmId: Long): Alarm? {
        return getAllAlarms().find { it.id == alarmId }
    }
    
    private fun saveAlarms(alarms: List<Alarm>) {
        val json = gson.toJson(alarms)
        prefs.edit().putString(key, json).apply()
    }
    
    fun toggleAlarm(alarmId: Long): Alarm? {
        val alarm = getAlarm(alarmId) ?: return null
        val updatedAlarm = alarm.copy(isEnabled = !alarm.isEnabled)
        saveAlarm(updatedAlarm)
        return updatedAlarm
    }
}

