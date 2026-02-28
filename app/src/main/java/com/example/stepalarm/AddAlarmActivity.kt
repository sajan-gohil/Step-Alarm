package com.example.stepalarm

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class AddAlarmActivity : AppCompatActivity() {
    private val TAG = "AddAlarmActivity"
    private lateinit var timePicker: TimePicker
    private lateinit var repeatCheckBox: CheckBox
    private lateinit var repeatDaysContainer: android.widget.LinearLayout
    private lateinit var saveAlarmButton: Button
    private lateinit var dayCheckBoxes: List<CheckBox>
    
    private var editingAlarmId: Long? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        LogFileWriter.logInfo(this, TAG, "=== AddAlarmActivity.onCreate() START ===")
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_add_alarm)
            LogFileWriter.logInfo(this, TAG, "Layout set")
            
            // Set up toolbar with back button
            val toolbar = findViewById<MaterialToolbar>(R.id.topAppBarAddAlarm)
            if (toolbar != null) {
                setSupportActionBar(toolbar)
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
            }
            
            timePicker = findViewById(R.id.timePicker)
            repeatCheckBox = findViewById(R.id.repeatCheckBox)
            repeatDaysContainer = findViewById(R.id.repeatDaysContainer)
            saveAlarmButton = findViewById(R.id.saveAlarmButton)
            
            // Get day checkboxes
            dayCheckBoxes = listOf(
                findViewById(R.id.daySunday),
                findViewById(R.id.dayMonday),
                findViewById(R.id.dayTuesday),
                findViewById(R.id.dayWednesday),
                findViewById(R.id.dayThursday),
                findViewById(R.id.dayFriday),
                findViewById(R.id.daySaturday)
            )
            LogFileWriter.logInfo(this, TAG, "Views initialized")
            
            // Check if editing existing alarm
            editingAlarmId = intent.getLongExtra("alarm_id", -1L).takeIf { it != -1L }
            LogFileWriter.logInfo(this, TAG, "Editing alarm ID: $editingAlarmId")
            
            if (editingAlarmId != null) {
                loadAlarmData()
            }
            
            repeatCheckBox.setOnCheckedChangeListener { _, isChecked ->
                repeatDaysContainer.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            }
            
            saveAlarmButton.setOnClickListener {
                saveAlarm()
            }
            LogFileWriter.logInfo(this, TAG, "=== AddAlarmActivity.onCreate() COMPLETED ===")
        } catch (e: Exception) {
            LogFileWriter.logError(this, TAG, "FATAL: onCreate() crashed", e)
            throw e
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        LogFileWriter.logInfo(this, TAG, "=== AddAlarmActivity.onDestroy() ===")
        super.onDestroy()
    }
    
    private fun loadAlarmData() {
        val alarmId = editingAlarmId ?: return
        val alarmDatabase = AlarmDatabase(this)
        val alarm = alarmDatabase.getAlarm(alarmId) ?: return
        
        timePicker.hour = alarm.hour
        timePicker.minute = alarm.minute
        repeatCheckBox.isChecked = alarm.isRepeating
        
        if (alarm.isRepeating) {
            repeatDaysContainer.visibility = android.view.View.VISIBLE
            // Calendar.SUNDAY = 1, Calendar.MONDAY = 2, etc.
            // dayCheckBoxes[0] = Sunday, dayCheckBoxes[1] = Monday, etc.
            alarm.repeatDays.forEach { dayOfWeek ->
                val index = dayOfWeek - 1 // Convert Calendar.DAY_OF_WEEK (1-7) to index (0-6)
                if (index in dayCheckBoxes.indices) {
                    dayCheckBoxes[index].isChecked = true
                }
            }
        }
    }
    
    private fun saveAlarm() {
        LogFileWriter.logInfo(this, TAG, "saveAlarm() called")
        try {
            val hour = timePicker.hour
            val minute = timePicker.minute
            val isRepeating = repeatCheckBox.isChecked
            LogFileWriter.logInfo(this, TAG, "Alarm: $hour:$minute, repeating=$isRepeating")
            
            val repeatDays = if (isRepeating) {
                dayCheckBoxes.mapIndexedNotNull { index, checkBox ->
                    if (checkBox.isChecked) {
                        index + 1 // Convert index (0-6) to Calendar.DAY_OF_WEEK (1-7)
                    } else {
                        null
                    }
                }.toSet()
            } else {
                emptySet<Int>()
            }
            
            if (isRepeating && repeatDays.isEmpty()) {
                Toast.makeText(this, "Please select at least one day for repeating alarm", Toast.LENGTH_SHORT).show()
                return
            }
            
            val alarmDatabase = AlarmDatabase(this)
            
            // Get existing alarm to preserve enabled state if editing
            val existingAlarm = editingAlarmId?.let { alarmDatabase.getAlarm(it) }
            val isEnabled = existingAlarm?.isEnabled ?: true
            
            val alarm = Alarm(
                id = editingAlarmId ?: 0L,
                hour = hour,
                minute = minute,
                isRepeating = isRepeating,
                repeatDays = repeatDays,
                isEnabled = isEnabled
            )
            
            val savedId = alarmDatabase.saveAlarm(alarm)
            LogFileWriter.logInfo(this, TAG, "Alarm saved with ID: $savedId")
            
            // Cancel old alarm and schedule new one
            if (existingAlarm != null) {
                AlarmScheduler.cancelAlarm(this, existingAlarm)
            }
            
            // Get the saved alarm with correct ID
            val savedAlarm = alarmDatabase.getAlarm(savedId) ?: alarm.copy(id = savedId)
            
            // Schedule the alarm
            AlarmScheduler.scheduleAlarm(this, savedAlarm)
            LogFileWriter.logInfo(this, TAG, "Alarm scheduled successfully")
            
            Toast.makeText(this, "Alarm saved", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        } catch (e: Exception) {
            LogFileWriter.logError(this, TAG, "Failed to save alarm", e)
            Toast.makeText(this, "Error saving alarm: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

