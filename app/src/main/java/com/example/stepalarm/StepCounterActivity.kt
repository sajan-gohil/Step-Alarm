package com.example.stepalarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StepCounterActivity : AppCompatActivity(), SensorEventListener {
    companion object {
        private const val TAG = "StepCounterActivity"
        private const val REQUIRED_STEPS = 10
    }

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var mediaPlayer: MediaPlayer? = null
    private var stepCount = 0
    private var initialStepCount = 0
    private lateinit var stepCountText: TextView
    private lateinit var dismissButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step_counter)
        Log.d(TAG, "StepCounterActivity created")

        stepCountText = findViewById(R.id.stepCountText)
        dismissButton = findViewById(R.id.dismissButton)
        dismissButton.isEnabled = false

        // Initialize sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Start alarm sound
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer.create(this, alarmUri).apply {
                isLooping = true
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm sound", e)
        }

        dismissButton.setOnClickListener {
            if (stepCount >= REQUIRED_STEPS) {
                stopAlarm()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            if (initialStepCount == 0) {
                initialStepCount = event.values[0].toInt()
            }
            stepCount = event.values[0].toInt() - initialStepCount
            stepCountText.text = getString(R.string.steps_taken, stepCount)
            
            if (stepCount >= REQUIRED_STEPS) {
                dismissButton.isEnabled = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    private fun stopAlarm() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
} 