package com.example.stepalarm

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.AppBarLayout
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
                if (isChecked) {
                    repeatDaysContainer.visibility = android.view.View.VISIBLE
                    repeatDaysContainer.alpha = 0f
                    repeatDaysContainer.animate().alpha(1f).setDuration(250).start()
                } else {
                    repeatDaysContainer.animate().alpha(0f).setDuration(200).withEndAction {
                        repeatDaysContainer.visibility = android.view.View.GONE
                    }.start()
                }
            }
            
            saveAlarmButton.setOnClickListener {
                // Button click animation
                it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    saveAlarm()
                }.start()
            }

            // Apply color palette
            applyTheme(toolbar)

            // Handle back navigation
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                    applyCloseTransition()
                }
            })

            LogFileWriter.logInfo(this, TAG, "=== AddAlarmActivity.onCreate() COMPLETED ===")
        } catch (e: Exception) {
            LogFileWriter.logError(this, TAG, "FATAL: onCreate() crashed", e)
            throw e
        }
    }

    private fun applyTheme(toolbar: MaterialToolbar?) {
        val palette = ThemeManager.getSelectedPalette(this)

        // Window
        @Suppress("DEPRECATION")
        window.statusBarColor = palette.statusBar
        @Suppress("DEPRECATION")
        window.navigationBarColor = palette.background
        val r = Color.red(palette.statusBar) / 255.0
        val g = Color.green(palette.statusBar) / 255.0
        val b = Color.blue(palette.statusBar) / 255.0
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = luminance > 0.5

        // Root background
        val rootView = window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)
        rootView?.setBackgroundColor(palette.background)

        // Toolbar
        toolbar?.setBackgroundColor(palette.toolbarBackground)
        toolbar?.setTitleTextColor(palette.toolbarText)
        toolbar?.setNavigationIconTint(palette.toolbarText)
        val appBar = toolbar?.parent
        if (appBar is AppBarLayout) {
            appBar.setBackgroundColor(palette.toolbarBackground)
        }

        // Determine if this is a dark theme (all except Pure White)
        val paletteKey = ThemeManager.getSelectedPaletteKey(this)
        val isDarkTheme = paletteKey != ThemeManager.PALETTE_PURE_WHITE
        val textColor = if (isDarkTheme) Color.WHITE else palette.textPrimary

        // Find the main content linear layout
        val contentLayout = timePicker.parent as? android.view.ViewGroup
        contentLayout?.setBackgroundColor(palette.background)

        // Title text
        val titleText = contentLayout?.getChildAt(0)
        if (titleText is TextView) {
            titleText.setTextColor(textColor)
        }

        // TimePicker text color for dark themes
        if (isDarkTheme) {
            setTimePickerTextColor(timePicker, Color.WHITE)
        }

        // Repeat checkbox
        repeatCheckBox.setTextColor(textColor)
        repeatCheckBox.buttonTintList = ColorStateList.valueOf(palette.accent)

        // Day checkboxes
        dayCheckBoxes.forEach { cb ->
            cb.setTextColor(textColor)
            cb.buttonTintList = ColorStateList.valueOf(palette.accent)
        }

        // Select repeat days label
        val selectDaysLabel = repeatDaysContainer.getChildAt(0)
        if (selectDaysLabel is TextView) {
            selectDaysLabel.setTextColor(if (isDarkTheme) Color.parseColor("#CCCCCC") else palette.subtleAccent)
        }

        // Save button
        saveAlarmButton.backgroundTintList = ColorStateList.valueOf(palette.buttonBackground)
        saveAlarmButton.setTextColor(palette.buttonText)
        // Update drawable tint
        saveAlarmButton.compoundDrawablesRelative.filterNotNull().forEach {
            it.setTint(palette.buttonText)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        applyCloseTransition()
        return true
    }

    /**
     * Forces white text on the TimePicker for dark themes.
     * Uses a ViewTreeObserver to re-apply after each layout pass (scroll/fling resets).
     */
    private fun setTimePickerTextColor(picker: TimePicker, color: Int) {
        val applyColor = Runnable {
            try {
                val queue: java.util.ArrayDeque<android.view.View> = java.util.ArrayDeque()
                queue.add(picker)
                while (queue.isNotEmpty()) {
                    val v = queue.poll()
                    if (v is android.widget.EditText) {
                        v.setTextColor(color)
                        v.highlightColor = Color.argb(80, 255, 255, 255)
                    }
                    if (v is android.widget.NumberPicker) {
                        try {
                            // Use reflection to set the text color on the NumberPicker's
                            // selectorWheelPaint and divider
                            val selectorWheelPaint = android.widget.NumberPicker::class.java
                                .getDeclaredField("mSelectorWheelPaint")
                            selectorWheelPaint.isAccessible = true
                            (selectorWheelPaint.get(v) as? android.graphics.Paint)?.color = color
                            v.invalidate()
                        } catch (_: Exception) { }
                    }
                    if (v is android.view.ViewGroup) {
                        for (i in 0 until v.childCount) {
                            queue.add(v.getChildAt(i))
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        // Apply now
        applyColor.run()
        // Re-apply after every layout pass so scrolling doesn't revert to black
        picker.viewTreeObserver.addOnGlobalLayoutListener { applyColor.run() }
    }

    private fun applyCloseTransition() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
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
            applyCloseTransition()
        } catch (e: Exception) {
            LogFileWriter.logError(this, TAG, "Failed to save alarm", e)
            Toast.makeText(this, "Error saving alarm: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

