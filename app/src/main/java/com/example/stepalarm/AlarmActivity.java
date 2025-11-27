package com.example.stepalarm;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class AlarmActivity extends Activity {
    private static final String TAG = "AlarmActivity";
    private static final int REQUIRED_STEPS = 10;
    private static final long UPDATE_INTERVAL = 100; // Update every 100ms
    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;

    private StepCounterService stepCounterService;
    private boolean isBound = false;
    private TextView stepCountText;
    private TextView remainingStepsText;
    private Handler handler;
    private Runnable updateRunnable;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LogFileWriter.logInfo(AlarmActivity.this, TAG, "Service connected: " + name);
            StepCounterService.LocalBinder binder = (StepCounterService.LocalBinder) service;
            stepCounterService = binder.getService();
            isBound = true;
            LogFileWriter.logInfo(AlarmActivity.this, TAG, "StepCounterService obtained, starting counting");
            stepCounterService.resetStepCount();
            stepCounterService.startCounting();
            startStepCountUpdates();
            LogFileWriter.logInfo(AlarmActivity.this, TAG, "Step counting started");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LogFileWriter.logWarning(AlarmActivity.this, TAG, "Service disconnected: " + name);
            stepCounterService = null;
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogFileWriter.logInfo(this, TAG, "=== AlarmActivity.onCreate() called ===");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_alarm);
        LogFileWriter.logInfo(this, TAG, "Layout set successfully");

        // Keep screen on and show over lock screen
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );
        LogFileWriter.logInfo(this, TAG, "Window flags set");

        stepCountText = findViewById(R.id.stepCountText);
        remainingStepsText = findViewById(R.id.remainingStepsText);
        LogFileWriter.logInfo(this, TAG, "TextViews found successfully");

        handler = new Handler(Looper.getMainLooper());
        LogFileWriter.logInfo(this, TAG, "Handler created");

        // Check for overlay permission
        if (!Settings.canDrawOverlays(this)) {
            LogFileWriter.logWarning(this, TAG, "Overlay permission not granted");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
        } else {
            LogFileWriter.logInfo(this, TAG, "Overlay permission granted, starting overlay service");
            startOverlayService();
        }

        // Start and bind to StepCounterService
        Intent intent = new Intent(this, StepCounterService.class);
        LogFileWriter.logInfo(this, TAG, "Starting StepCounterService");
        startService(intent); // Start the service first (foreground is handled in service)
        LogFileWriter.logInfo(this, TAG, "Binding to StepCounterService");
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        LogFileWriter.logInfo(this, TAG, "Service binding initiated");
    }

    private void startOverlayService() {
        LogFileWriter.logInfo(this, TAG, "Starting AlarmOverlayService");
        Intent intent = new Intent(this, AlarmOverlayService.class);
        startService(intent);
        LogFileWriter.logInfo(this, TAG, "AlarmOverlayService started");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService();
            } else {
                Toast.makeText(this, "Overlay permission is required for the alarm", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startStepCountUpdates() {
        LogFileWriter.logInfo(this, TAG, "Starting step count update loop");
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateStepCount();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.post(updateRunnable);
        LogFileWriter.logInfo(this, TAG, "Step count update loop started");
    }

    private void stopStepCountUpdates() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    private void updateStepCount() {
        if (stepCounterService == null) {
            LogFileWriter.logWarning(this, TAG, "updateStepCount called but stepCounterService is null");
            return;
        }

        long steps = stepCounterService.getStepCount();
        long remaining = REQUIRED_STEPS - steps;

        LogFileWriter.logInfo(this, TAG, "updateStepCount called, steps: " + steps + ", remaining: " + remaining);

        stepCountText.setText("Steps taken: " + steps);
        remainingStepsText.setText("Steps remaining: " + remaining);

        // Update overlay service
        Intent intent = new Intent(this, AlarmOverlayService.class);
        intent.putExtra("steps", (int) steps);
        startService(intent);

        if (steps >= REQUIRED_STEPS) {
            LogFileWriter.logInfo(this, TAG, "Required steps reached: " + steps);
            stopAlarm();
        }
    }

    private void stopAlarm() {
        LogFileWriter.logInfo(this, TAG, "Stopping alarm");
        stopStepCountUpdates();

        if (stepCounterService != null) {
            stepCounterService.stopCounting();
            LogFileWriter.logInfo(this, TAG, "Step counting stopped");
        }

        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
            LogFileWriter.logInfo(this, TAG, "Service unbound");
        }

        // Stop the overlay service
        stopService(new Intent(this, AlarmOverlayService.class));
        LogFileWriter.logInfo(this, TAG, "Overlay service stopped");

        // Stop the alarm sound using the STOP_ALARM action
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction("STOP_ALARM");
        sendBroadcast(intent);
        LogFileWriter.logInfo(this, TAG, "STOP_ALARM broadcast sent");

        finish();
        LogFileWriter.logInfo(this, TAG, "Activity finished");
    }

    @Override
    protected void onDestroy() {
        LogFileWriter.logInfo(this, TAG, "=== AlarmActivity.onDestroy() called ===");
        super.onDestroy();
        stopStepCountUpdates();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        stopService(new Intent(this, AlarmOverlayService.class));
    }
}
