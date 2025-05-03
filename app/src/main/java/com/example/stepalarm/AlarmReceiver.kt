package com.example.stepalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "StepAlarm::AlarmWakeLock"
        )
        wakeLock.acquire(10*1000L)

        // Start the step counter activity
        val stepIntent = Intent(context, StepCounterActivity::class.java)
        stepIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(stepIntent)

        wakeLock.release()
    }
} 