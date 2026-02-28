package com.example.stepalarm;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AlarmActivity extends Activity {
    private static final String TAG = "AlarmActivity";
    private static final int REQUIRED_STEPS = 10;
    private static final long UPDATE_INTERVAL = 100; // Update every 100ms
    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private boolean permissionRequested = false;

    private StepCounterService stepCounterService;
    private boolean isBound = false;
    private TextView stepCountText;
    private TextView remainingStepsText;
    private Handler handler;
    private Runnable updateRunnable;
    private long lastUpdateLogTime = 0;
    private static final long UPDATE_LOG_INTERVAL = 2000; // Log step updates every 2 seconds

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

        if (savedInstanceState != null) {
            permissionRequested = savedInstanceState.getBoolean("permission_requested", false);
        }

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

        // Apply color palette
        applyTheme();

        // Entrance animation for the alarm screen
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.setAlpha(0f);
            rootView.animate().alpha(1f).setDuration(350).start();
        }

        handler = new Handler(Looper.getMainLooper());
        LogFileWriter.logInfo(this, TAG, "Handler created");

        // Check for overlay permission
        if (!Settings.canDrawOverlays(this)) {
            if (!permissionRequested) {
                LogFileWriter.logWarning(this, TAG, "Overlay permission not granted");
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                permissionRequested = true;
            }
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

    private void applyTheme() {
        ThemeManager.Palette palette = ThemeManager.INSTANCE.getSelectedPalette(this);

        // Root background
        LinearLayout rootLayout = (LinearLayout) ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        if (rootLayout != null) {
            rootLayout.setBackgroundColor(palette.getBackground());
        }

        // Status bar & navigation bar
        getWindow().setStatusBarColor(palette.getStatusBar());
        getWindow().setNavigationBarColor(palette.getBackground());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            double r = Color.red(palette.getStatusBar()) / 255.0;
            double g = Color.green(palette.getStatusBar()) / 255.0;
            double b = Color.blue(palette.getStatusBar()) / 255.0;
            double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
            if (luminance > 0.5) {
                getWindow().getDecorView().setSystemUiVisibility(flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            } else {
                getWindow().getDecorView().setSystemUiVisibility(flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }

        // All text views in the layout
        if (rootLayout != null) {
            for (int i = 0; i < rootLayout.getChildCount(); i++) {
                View child = rootLayout.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    if (child.getId() == R.id.stepCountText || child.getId() == R.id.remainingStepsText) {
                        tv.setTextColor(palette.getAccent());
                    } else if (child.getId() == R.id.slowStepHint) {
                        // Keep the hint in a warm accent color for visibility
                        tv.setTextColor(palette.getAccent());
                    } else {
                        tv.setTextColor(palette.getTextPrimary());
                    }
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted. Restarting app...", Toast.LENGTH_SHORT).show();
                // Restart the app to ensure permission changes take effect
                restartApp();
            } else {
                Toast.makeText(this, "Overlay permission is required for the alarm", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void restartApp() {
        // Restart the app to ensure permission changes take effect
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        // Force process restart
        android.os.Process.killProcess(android.os.Process.myPid());
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
            return;
        }

        long steps = stepCounterService.getStepCount();
        long remaining = REQUIRED_STEPS - steps;

        // Rate-limit logging to avoid flooding log file
        long now = System.currentTimeMillis();
        if (now - lastUpdateLogTime >= UPDATE_LOG_INTERVAL) {
            lastUpdateLogTime = now;
            LogFileWriter.logInfo(this, TAG, "Step update: steps=" + steps + ", remaining=" + remaining);
        }

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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("permission_requested", permissionRequested);
    }

    @Override
    protected void onDestroy() {
        LogFileWriter.logInfo(this, TAG, "=== AlarmActivity.onDestroy() called ===");
        super.onDestroy();
        stopStepCountUpdates();
        
        // Stop the alarm sound and vibration directly
        AlarmReceiver.stopAlarm(this);
        LogFileWriter.logInfo(this, TAG, "AlarmReceiver.stopAlarm() called from onDestroy");
        
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        stopService(new Intent(this, AlarmOverlayService.class));
        
        // Also stop the StepCounterService
        stopService(new Intent(this, StepCounterService.class));
        LogFileWriter.logInfo(this, TAG, "StepCounterService stopped");
    }
}
