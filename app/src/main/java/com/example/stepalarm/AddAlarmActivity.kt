package com.example.stepalarm

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AddAlarmActivity : AppCompatActivity() {
    private lateinit var timePicker: TimePicker
    private lateinit var repeatCheckBox: CheckBox
    private lateinit var repeatDaysContainer: android.widget.LinearLayout
    private lateinit var saveAlarmButton: Button
    private lateinit var dayCheckBoxes: List<CheckBox>
    
    private var editingAlarmId: Long? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_alarm)
        
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
        
        // Check if editing existing alarm
        editingAlarmId = intent.getLongExtra("alarm_id", -1L).takeIf { it != -1L }
        
        if (editingAlarmId != null) {
            loadAlarmData()
        }
        
        repeatCheckBox.setOnCheckedChangeListener { _, isChecked ->
            repeatDaysContainer.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        saveAlarmButton.setOnClickListener {
            saveAlarm()
        }
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
        val hour = timePicker.hour
        val minute = timePicker.minute
        val isRepeating = repeatCheckBox.isChecked
        
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
        
        // Cancel old alarm and schedule new one
        if (existingAlarm != null) {
            AlarmScheduler.cancelAlarm(this, existingAlarm)
        }
        
        // Get the saved alarm with correct ID
        val savedAlarm = alarmDatabase.getAlarm(savedId) ?: alarm.copy(id = savedId)
        
        // Schedule the alarm
        AlarmScheduler.scheduleAlarm(this, savedAlarm)
        
        Toast.makeText(this, "Alarm saved", Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
    }
}

