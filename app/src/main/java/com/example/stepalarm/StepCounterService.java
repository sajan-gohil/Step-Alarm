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
    private Sensor accelerometerSensor;
    private Sensor stepDetectorSensor;
    private int stepCount = 0;
    private boolean isCounting = false;
    private float lastMagnitude = 0;
    private static final float STEP_THRESHOLD = 2.0f; // Threshold for step detection (m/s^2 above gravity)
    private static final long MIN_STEP_INTERVAL = 300; // Minimum time between steps (ms)
    private long lastStepTime = 0;
    private float[] gravity = new float[3]; // For filtering gravity
    private float[] linearAcceleration = new float[3]; // For step detection
    private static final float ALPHA = 0.8f; // Low-pass filter constant
    private Sensor stepCounterSensor;
    private boolean useStepCounter = false;
    private boolean useStepDetector = false;
    private int initialStepCounterValue = -1;

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
            // Try step counter first (most accurate)
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (stepCounterSensor != null) {
                useStepCounter = true;
                Log.d(TAG, "Step counter sensor found, will use it");
            } else {
                // Try step detector (detects individual steps)
                stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
                if (stepDetectorSensor != null) {
                    useStepDetector = true;
                    Log.d(TAG, "Step detector sensor found, will use it");
                } else {
                    // Fallback to accelerometer
                    accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                    if (accelerometerSensor == null) {
                        Log.e(TAG, "No step counting sensors found");
                    } else {
                        Log.d(TAG, "Accelerometer sensor initialized (fallback)");
                    }
                }
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isCounting) return;
        
        if (useStepCounter && event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            int value = (int) event.values[0];
            if (initialStepCounterValue < 0) {
                initialStepCounterValue = value;
                Log.d(TAG, "Initial step counter value: " + initialStepCounterValue);
            }
            stepCount = value - initialStepCounterValue;
            Log.d(TAG, String.format("StepCounter: total=%d, initial=%d, count=%d", value, initialStepCounterValue, stepCount));
        } else if (useStepDetector && event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            // Step detector fires once per step
            if (event.values[0] == 1.0f) {
                stepCount++;
                Log.d(TAG, String.format("Step detected! Total steps: %d", stepCount));
            }
        } else if (!useStepCounter && !useStepDetector && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Use accelerometer with low-pass filter to detect steps
            // Apply low-pass filter to separate gravity from linear acceleration
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];
            
            // Remove gravity from acceleration
            linearAcceleration[0] = event.values[0] - gravity[0];
            linearAcceleration[1] = event.values[1] - gravity[1];
            linearAcceleration[2] = event.values[2] - gravity[2];
            
            // Calculate magnitude of linear acceleration
            float magnitude = (float) Math.sqrt(
                linearAcceleration[0] * linearAcceleration[0] +
                linearAcceleration[1] * linearAcceleration[1] +
                linearAcceleration[2] * linearAcceleration[2]
            );
            
            long currentTime = System.currentTimeMillis();
            
            // Check if enough time has passed since the last step
            if (currentTime - lastStepTime < MIN_STEP_INTERVAL) {
                return;
            }
            
            // Detect step: magnitude crosses threshold upward
            if (magnitude > STEP_THRESHOLD && lastMagnitude <= STEP_THRESHOLD) {
                stepCount++;
                lastStepTime = currentTime;
                Log.d(TAG, String.format("Accelerometer step detected! Total steps: %d, Magnitude: %.3f", stepCount, magnitude));
            }
            lastMagnitude = magnitude;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Sensor accuracy changed: " + accuracy);
    }

    public void startCounting() {
        if (!isCounting && sensorManager != null) {
            stepCount = 0;
            lastMagnitude = 0;
            lastStepTime = 0;
            initialStepCounterValue = -1;
            // Reset gravity filter
            gravity[0] = 0;
            gravity[1] = 0;
            gravity[2] = 0;
            
            if (useStepCounter && stepCounterSensor != null) {
                sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
                Log.d(TAG, "Started step counting using step counter sensor");
            } else if (useStepDetector && stepDetectorSensor != null) {
                sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
                Log.d(TAG, "Started step counting using step detector sensor");
            } else if (accelerometerSensor != null) {
                sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME);
                Log.d(TAG, "Started step counting using accelerometer fallback");
            } else {
                Log.e(TAG, "No available sensor for step counting");
            }
            isCounting = true;
        }
    }

    public void stopCounting() {
        if (isCounting && sensorManager != null) {
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
        initialStepCounterValue = -1;
        gravity[0] = 0;
        gravity[1] = 0;
        gravity[2] = 0;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCounting();
    }
} 