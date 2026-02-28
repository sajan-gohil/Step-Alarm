package com.example.stepalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        LogFileWriter.logInfo(context, TAG, "=== BootReceiver.onReceive() called ===");
        try {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                LogFileWriter.logInfo(context, TAG, "Boot completed, rescheduling alarms");
                AlarmScheduler.rescheduleAllAlarms(context);
                LogFileWriter.logInfo(context, TAG, "Alarms rescheduled after boot");
            } else {
                LogFileWriter.logWarning(context, TAG, "Unexpected action: " + intent.getAction());
            }
        } catch (Exception e) {
            LogFileWriter.logError(context, TAG, "Failed to reschedule alarms after boot", e);
        }
    }
}

