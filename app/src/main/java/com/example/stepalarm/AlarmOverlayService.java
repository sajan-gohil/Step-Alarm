package com.example.stepalarm;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AlarmOverlayService extends Service {
    private static final String TAG = "AlarmOverlayService";
    private WindowManager windowManager;
    private View overlayView;
    private TextView stepCountText;
    private TextView remainingStepsText;
    private static final int REQUIRED_STEPS = 10;
    private boolean overlayAdded = false;

    @Override
    public void onCreate() {
        LogFileWriter.logInfo(this, TAG, "=== onCreate() called ===");
        super.onCreate();
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager == null) {
                LogFileWriter.logError(this, TAG, "WindowManager is null — cannot create overlay");
                return;
            }
            createOverlayView();
        } catch (Exception e) {
            LogFileWriter.logError(this, TAG, "Failed to create overlay in onCreate", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogFileWriter.logInfo(this, TAG, "onStartCommand called");
        try {
            if (intent != null && intent.hasExtra("steps")) {
                int steps = intent.getIntExtra("steps", 0);
                updateStepCount(steps);
            }
        } catch (Exception e) {
            LogFileWriter.logError(this, TAG, "Error in onStartCommand", e);
        }
        return START_STICKY;
    }

    private void createOverlayView() {
        LogFileWriter.logInfo(this, TAG, "Creating overlay view");
        
        try {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            );

            params.gravity = Gravity.TOP;
            params.y = 0;

            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            overlayView = inflater.inflate(R.layout.overlay_alarm, null);

            stepCountText = overlayView.findViewById(R.id.stepCountText);
            remainingStepsText = overlayView.findViewById(R.id.remainingStepsText);

            // Apply theme colors to overlay
            applyThemeToOverlay();

            windowManager.addView(overlayView, params);
            overlayAdded = true;
            LogFileWriter.logInfo(this, TAG, "Overlay view created and added successfully");
        } catch (Exception e) {
            LogFileWriter.logError(this, TAG, "Failed to create overlay view", e);
            overlayAdded = false;
        }
    }

    private void applyThemeToOverlay() {
        if (overlayView == null) return;
        try {
            ThemeManager.Palette palette = ThemeManager.INSTANCE.getSelectedPalette(this);
            overlayView.setBackgroundColor(palette.getOverlayBackground());

            // Set text colors for the overlay title and step texts
            if (overlayView instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) overlayView;
                for (int i = 0; i < layout.getChildCount(); i++) {
                    View child = layout.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView tv = (TextView) child;
                        if (tv.getId() == R.id.stepCountText || tv.getId() == R.id.remainingStepsText) {
                            tv.setTextColor(palette.getAccent());
                        } else {
                            tv.setTextColor(palette.getTextPrimary());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogFileWriter.logError(this, TAG, "Failed to apply theme to overlay", e);
        }
    }

    public void updateStepCount(int steps) {
        if (!overlayAdded || stepCountText == null || remainingStepsText == null) {
            return;
        }
        int remaining = REQUIRED_STEPS - steps;
        stepCountText.setText("Steps taken: " + steps);
        remainingStepsText.setText("Steps remaining: " + remaining);
    }

    @Override
    public void onDestroy() {
        LogFileWriter.logInfo(this, TAG, "=== onDestroy() called ===");
        super.onDestroy();
        try {
            if (overlayAdded && overlayView != null && windowManager != null) {
                windowManager.removeView(overlayView);
                overlayAdded = false;
                LogFileWriter.logInfo(this, TAG, "Overlay view removed");
            }
        } catch (Exception e) {
            LogFileWriter.logError(this, TAG, "Error removing overlay view in onDestroy", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 