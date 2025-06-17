package com.example.stepalarm;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;

public class StepCounterService extends Service implements SensorEventListener {
    private static final String TAG = "StepCounterService";
    private final IBinder binder = new LocalBinder();
    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;
    private int stepCount = 0;
    private boolean isCounting = false;
    private float lastMagnitude = 0;
    private static final float THRESHOLD = 0.2f; // Lowered threshold for more sensitivity
    private static final long MIN_STEP_INTERVAL = 200; // Reduced minimum time between steps
    private long lastStepTime = 0;
    private float[] smoothedValues = new float[3]; // For smoothing the gyroscope values
    private static final float SMOOTHING_FACTOR = 0.2f; // Smoothing factor for gyroscope values

    public class LocalBinder extends Binder {
        StepCounterService getService() {
            return StepCounterService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (gyroscopeSensor == null) {
                Log.e(TAG, "No gyroscope sensor found");
            } else {
                Log.d(TAG, "Gyroscope sensor initialized");
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isCounting || event.sensor.getType() != Sensor.TYPE_GYROSCOPE) {
            return;
        }

        // Apply smoothing to gyroscope values
        for (int i = 0; i < 3; i++) {
            smoothedValues[i] = smoothedValues[i] * (1 - SMOOTHING_FACTOR) + event.values[i] * SMOOTHING_FACTOR;
        }

        // Calculate the magnitude of rotation
        float x = smoothedValues[0];
        float y = smoothedValues[1];
        float z = smoothedValues[2];
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);

        long currentTime = System.currentTimeMillis();
        
        // Check if enough time has passed since the last step
        if (currentTime - lastStepTime < MIN_STEP_INTERVAL) {
            return;
        }

        // Detect step based on magnitude threshold
        if (magnitude > THRESHOLD && lastMagnitude <= THRESHOLD) {
            stepCount++;
            lastStepTime = currentTime;
            Log.d(TAG, String.format("Step detected! Total steps: %d, Magnitude: %.3f", stepCount, magnitude));
        }

        lastMagnitude = magnitude;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Sensor accuracy changed: " + accuracy);
    }

    public void startCounting() {
        if (gyroscopeSensor != null && !isCounting) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
            isCounting = true;
            stepCount = 0;
            lastMagnitude = 0;
            lastStepTime = 0;
            Log.d(TAG, "Started step counting");
        }
    }

    public void stopCounting() {
        if (isCounting) {
            sensorManager.unregisterListener(this);
            isCounting = false;
            Log.d(TAG, "Stopped step counting");
        }
    }

    public int getStepCount() {
        return stepCount;
    }

    public void resetStepCount() {
        stepCount = 0;
        lastMagnitude = 0;
        lastStepTime = 0;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCounting();
    }
} 