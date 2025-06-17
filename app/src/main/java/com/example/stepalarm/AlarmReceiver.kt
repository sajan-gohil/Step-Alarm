package com.example.stepalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received")
        
        // Get a wake lock to keep the device awake
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "StepAlarm::AlarmWakeLock"
        )

        try {
            // Acquire the wake lock for 10 seconds
            wakeLock.acquire(10 * 1000L)

            // Start the step counter activity
            val stepIntent = Intent(context, StepCounterActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(stepIntent)
        } finally {
            // Release the wake lock
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
} 