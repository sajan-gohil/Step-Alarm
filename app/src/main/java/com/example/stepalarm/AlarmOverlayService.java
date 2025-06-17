package com.example.stepalarm;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class AlarmOverlayService extends Service {
    private WindowManager windowManager;
    private View overlayView;
    private TextView stepCountText;
    private TextView remainingStepsText;
    private static final int REQUIRED_STEPS = 10;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createOverlayView();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("steps")) {
            int steps = intent.getIntExtra("steps", 0);
            updateStepCount(steps);
        }
        return START_STICKY;
    }

    private void createOverlayView() {
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

        windowManager.addView(overlayView, params);
    }

    public void updateStepCount(int steps) {
        if (overlayView != null) {
            int remaining = REQUIRED_STEPS - steps;
            stepCountText.setText("Steps taken: " + steps);
            remainingStepsText.setText("Steps remaining: " + remaining);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 