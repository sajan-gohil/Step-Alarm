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
    private AlarmOverlayService overlayService;
    private boolean isBound = false;
    private TextView stepCountText;
    private TextView remainingStepsText;
    private Handler handler;
    private Runnable updateRunnable;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            StepCounterService.LocalBinder binder = (StepCounterService.LocalBinder) service;
            stepCounterService = binder.getService();
            isBound = true;
            stepCounterService.startCounting();
            startStepCountUpdates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            stepCounterService = null;
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        // Keep screen on and show over lock screen
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        stepCountText = findViewById(R.id.stepCountText);
        remainingStepsText = findViewById(R.id.remainingStepsText);
        handler = new Handler(Looper.getMainLooper());

        // Check for overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
        } else {
            startOverlayService();
        }

        // Start and bind to StepCounterService
        Intent intent = new Intent(this, StepCounterService.class);
        startService(intent); // Start the service first
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void startOverlayService() {
        Intent intent = new Intent(this, AlarmOverlayService.class);
        startService(intent);
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
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateStepCount();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.post(updateRunnable);
    }

    private void stopStepCountUpdates() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    private void updateStepCount() {
        if (stepCounterService != null) {
            int steps = stepCounterService.getStepCount();
            int remaining = REQUIRED_STEPS - steps;
            
            stepCountText.setText("Steps taken: " + steps);
            remainingStepsText.setText("Steps remaining: " + remaining);

            // Update overlay service
            Intent intent = new Intent(this, AlarmOverlayService.class);
            intent.putExtra("steps", steps);
            startService(intent);

            if (steps >= REQUIRED_STEPS) {
                stopAlarm();
            }
        }
    }

    private void stopAlarm() {
        stopStepCountUpdates();
        if (stepCounterService != null) {
            stepCounterService.stopCounting();
        }
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        
        // Stop the overlay service
        stopService(new Intent(this, AlarmOverlayService.class));
        
        // Stop the alarm sound using the STOP_ALARM action
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction("STOP_ALARM");
        sendBroadcast(intent);
        
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStepCountUpdates();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        stopService(new Intent(this, AlarmOverlayService.class));
    }
} 