package com.example.stepalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import java.io.IOException;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static MediaPlayer mediaPlayer;
    private static Vibrator vibrator;
    private static boolean isAlarmActive = false;
    private static final long[] VIBRATION_PATTERN = {0, 1000, 1000}; // Vibrate for 1 second, pause for 1 second

    @Override
    public void onReceive(Context context, Intent intent) {
        LogFileWriter.logInfo(context, TAG, "=== AlarmReceiver.onReceive() called ===");
        
        if (intent == null) {
            RuntimeException e = new RuntimeException("Intent is null in onReceive");
            LogFileWriter.logError(context, TAG, "Intent is null in onReceive", e);
            throw e;
        }
        
        String action = intent.getAction();
        LogFileWriter.logInfo(context, TAG, "Alarm received with action: " + action);

        if (action != null && action.equals("STOP_ALARM")) {
            LogFileWriter.logInfo(context, TAG, "STOP_ALARM action received");
            stopAlarm(context);
            return;
        }

        if (isAlarmActive) {
            LogFileWriter.logWarning(context, TAG, "Alarm already active, ignoring new alarm");
            return;
        }

        // Get alarm ID from intent
        long alarmId = intent.getLongExtra("alarm_id", -1);
        LogFileWriter.logInfo(context, TAG, "Alarm ID from intent: " + alarmId);
        
        if (alarmId == -1) {
            RuntimeException e = new RuntimeException("No alarm_id in intent");
            LogFileWriter.logError(context, TAG, "No alarm_id in intent", e);
            throw e;
        }

        // Check if alarm is enabled
        AlarmDatabase alarmDatabase = new AlarmDatabase(context);
        Alarm alarm = alarmDatabase.getAlarm(alarmId);
        LogFileWriter.logInfo(context, TAG, "Retrieved alarm from database: " + (alarm != null ? "found" : "not found"));
        
        if (alarm == null) {
            RuntimeException e = new RuntimeException("Alarm not found in database for ID: " + alarmId);
            LogFileWriter.logError(context, TAG, "Alarm not found in database for ID: " + alarmId, e);
            throw e;
        }
        
        if (!alarm.isEnabled()) {
            LogFileWriter.logInfo(context, TAG, "Alarm is disabled, ignoring");
            return;
        }

        // If it's a one-time alarm, delete it after triggering
        if (!alarm.isRepeating()) {
            alarmDatabase.deleteAlarm(alarmId);
            LogFileWriter.logInfo(context, TAG, "Deleted one-time alarm");
        } else {
            // Reschedule repeating alarm for next occurrence
            AlarmScheduler.scheduleAlarm(context, alarm);
            LogFileWriter.logInfo(context, TAG, "Rescheduled repeating alarm");
        }

        isAlarmActive = true;
        
        // Get the system's default alarm sound
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            // Fallback to notification sound if alarm sound is not available
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            LogFileWriter.logWarning(context, TAG, "Using notification sound as fallback");
        }
        LogFileWriter.logInfo(context, TAG, "Alarm sound URI: " + alarmSound);

        // Create and configure MediaPlayer
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(context, alarmSound);
        } catch (IOException e) {
            RuntimeException re = new RuntimeException("Failed to set MediaPlayer data source", e);
            LogFileWriter.logError(context, TAG, "Failed to set MediaPlayer data source", re);
            throw re;
        }
        
        // Set audio attributes to use alarm stream
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            );
        } else {
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        
        mediaPlayer.setLooping(true);
        try {
            mediaPlayer.prepare();
        } catch (IOException e) {
            RuntimeException re = new RuntimeException("Failed to prepare MediaPlayer", e);
            LogFileWriter.logError(context, TAG, "Failed to prepare MediaPlayer", re);
            throw re;
        }
        mediaPlayer.start();
        LogFileWriter.logInfo(context, TAG, "MediaPlayer started successfully");

        // Start vibration
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0));
            } else {
                vibrator.vibrate(VIBRATION_PATTERN, 0);
            }
            LogFileWriter.logInfo(context, TAG, "Vibration started");
        }

        // Start the alarm activity
        Intent alarmIntent = new Intent(context, AlarmActivity.class);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        LogFileWriter.logInfo(context, TAG, "Starting AlarmActivity");
        context.startActivity(alarmIntent);
        LogFileWriter.logInfo(context, TAG, "AlarmActivity started successfully");
    }

    public static void stopAlarm() {
        stopAlarm(null);
    }
    
    public static void stopAlarm(Context context) {
        if (context != null) {
            LogFileWriter.logInfo(context, TAG, "Stopping alarm");
        } else {
            Log.d(TAG, "Stopping alarm");
        }
        isAlarmActive = false;
        
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            if (context != null) {
                LogFileWriter.logInfo(context, TAG, "MediaPlayer stopped and released");
            }
            mediaPlayer = null;
        }
        
        if (vibrator != null) {
            vibrator.cancel();
            if (context != null) {
                LogFileWriter.logInfo(context, TAG, "Vibrator cancelled");
            }
        }
    }
} 