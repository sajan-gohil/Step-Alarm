package com.example.stepalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class StepCounterService extends Service implements SensorEventListener {
    private static final String TAG = "StepCounterService";
    private static final String CHANNEL_ID = "StepCounterServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private final IBinder binder = new LocalBinder();
    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private Sensor stepDetectorSensor;
    private long stepCount = 0;
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
    private long initialStepCounterValue = -1;
    private boolean initialValueSet = false;

    public class LocalBinder extends Binder {
        StepCounterService getService() {
            return StepCounterService.this;
        }
    }

    @Override
    public void onCreate() {
        LogFileWriter.logInfo(this, TAG, "=== StepCounterService.onCreate() called ===");
        super.onCreate();
        createNotificationChannel();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        
        if (sensorManager == null) {
            RuntimeException e = new RuntimeException("SensorManager is null");
            LogFileWriter.logError(this, TAG, "SensorManager is null", e);
            throw e;
        }
        
        // Priority 1: Step Detector (Immediate feedback, best for "count 10 steps")
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        if (stepDetectorSensor != null) {
            useStepDetector = true;
            LogFileWriter.logInfo(this, TAG, "Step detector sensor found, will use it (Priority 1)");
        } else {
            // Priority 2: Step Counter (Cumulative, might have latency/batching)
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (stepCounterSensor != null) {
                useStepCounter = true;
                LogFileWriter.logInfo(this, TAG, "Step counter sensor found, will use it (Priority 2)");
            } else {
                // Priority 3: Accelerometer (Fallback)
                accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (accelerometerSensor == null) {
                    RuntimeException e = new RuntimeException("No step counting sensors found");
                    LogFileWriter.logError(this, TAG, "No step counting sensors found", e);
                    throw e;
                } else {
                    LogFileWriter.logInfo(this, TAG, "Accelerometer sensor initialized (fallback)");
                }
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Step Counter Service Channel",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        LogFileWriter.logInfo(this, TAG, "=== onSensorChanged() called ===");
        if (!isCounting) {
            LogFileWriter.logWarning(this, TAG, "onSensorChanged called but isCounting is false");
            return;
        }
        if (event == null) {
            LogFileWriter.logError(this, TAG, "onSensorChanged called with null event");
            return;
        }
        
        LogFileWriter.logInfo(this, TAG, "Sensor type: " + event.sensor.getType() + ", useStepCounter: " + useStepCounter);
        
        if (useStepCounter && event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            // Step counter returns cumulative steps since last reboot
            long stepsSinceLastReboot = (long) event.values[0];
            LogFileWriter.logInfo(this, TAG, "Step counter event received, stepsSinceLastReboot: " + stepsSinceLastReboot);
            
            // Set initial value on first reading
            if (!initialValueSet) {
                initialStepCounterValue = stepsSinceLastReboot;
                initialValueSet = true;
                stepCount = 0;
                LogFileWriter.logInfo(this, TAG, "Initial step counter value set: " + initialStepCounterValue);
            } else {
                // Calculate steps since we started counting
                stepCount = stepsSinceLastReboot - initialStepCounterValue;
                if (stepCount < 0) {
                    // Handle case where device was rebooted (step counter resets)
                    initialStepCounterValue = stepsSinceLastReboot;
                    stepCount = 0;
                    LogFileWriter.logWarning(this, TAG, "Step counter reset detected, reinitializing");
                }
            }
            LogFileWriter.logInfo(this, TAG, String.format("StepCounter: total=%d, initial=%d, count=%d", 
                stepsSinceLastReboot, initialStepCounterValue, stepCount));
            if (stepCount >= 10) {
                onTargetReached();
            }
        
        } else if (useStepDetector && event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            // Step detector fires once per step (value is 1.0 when step detected)
            LogFileWriter.logInfo(this, TAG, "Step detector event received, value: " + event.values[0]);
            if (event.values[0] == 1.0f) {
                stepCount++;
                if (stepCount >= 10) {
                    onTargetReached();
                }
                LogFileWriter.logInfo(this, TAG, String.format("Step detected! Total steps: %d", stepCount));
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
                LogFileWriter.logInfo(this, TAG, String.format("Accelerometer step detected! Total steps: %d, Magnitude: %.3f", stepCount, magnitude));
            }
            lastMagnitude = magnitude;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Sensor accuracy changed: " + accuracy);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogFileWriter.logInfo(this, TAG, "=== StepCounterService.onStartCommand() called ===");
        // Start as foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }
        LogFileWriter.logInfo(this, TAG, "Service started as foreground");
        return START_STICKY;
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, AlarmActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Step Counter Active")
            .setContentText("Counting steps for alarm")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build();
    }

    public void startCounting() {
        LogFileWriter.logInfo(this, TAG, "=== startCounting() called ===");
        
        if (isCounting) {
            LogFileWriter.logWarning(this, TAG, "Step counting already in progress");
            return;
        }
        
        stepCount = 0;
        lastMagnitude = 0;
        lastStepTime = 0;
        initialStepCounterValue = -1;
        initialValueSet = false;
        // Reset gravity filter
        gravity[0] = 0;
        gravity[1] = 0;
        gravity[2] = 0;
        
        boolean registered = false;
        
        // Use SENSOR_DELAY_GAME for lower latency to avoid batching issues
        int sensorDelay = SensorManager.SENSOR_DELAY_GAME;
        
        if (useStepDetector) {
            LogFileWriter.logInfo(this, TAG, "Attempting to register step detector sensor");
            registered = sensorManager.registerListener(this, stepDetectorSensor, sensorDelay);
            if (!registered) {
                RuntimeException e = new RuntimeException("Failed to register step detector sensor listener");
                LogFileWriter.logError(this, TAG, "Failed to register step detector sensor listener", e);
                throw e;
            }
            LogFileWriter.logInfo(this, TAG, "Started step counting using step detector sensor");
        } else if (useStepCounter) {
            LogFileWriter.logInfo(this, TAG, "Attempting to register step counter sensor");
            registered = sensorManager.registerListener(this, stepCounterSensor, sensorDelay);
            if (!registered) {
                RuntimeException e = new RuntimeException("Failed to register step counter sensor listener");
                LogFileWriter.logError(this, TAG, "Failed to register step counter sensor listener", e);
                throw e;
            }
            LogFileWriter.logInfo(this, TAG, "Started step counting using step counter sensor");
        } else {
            LogFileWriter.logInfo(this, TAG, "Attempting to register accelerometer sensor");
            registered = sensorManager.registerListener(this, accelerometerSensor, sensorDelay);
            if (!registered) {
                RuntimeException e = new RuntimeException("Failed to register accelerometer sensor listener");
                LogFileWriter.logError(this, TAG, "Failed to register accelerometer sensor listener", e);
                throw e;
            }
            LogFileWriter.logInfo(this, TAG, "Started step counting using accelerometer fallback");
        }
        
        isCounting = true;
        LogFileWriter.logInfo(this, TAG, "Step counting is now active");
    }

    public void stopCounting() {
        if (isCounting) {
            sensorManager.unregisterListener(this);
            isCounting = false;
            LogFileWriter.logInfo(this, TAG, "Stopped step counting");
        }
    }

    public long getStepCount() {
        LogFileWriter.logInfo(this, TAG, "getStepCount() called, returning: " + stepCount);
        return stepCount;
    }

    public void resetStepCount() {
        stepCount = 0;
        lastMagnitude = 0;
        lastStepTime = 0;
        initialStepCounterValue = -1;
        initialValueSet = false;
        gravity[0] = 0;
        gravity[1] = 0;
        gravity[2] = 0;
    }

    @Override
    public void onDestroy() {
        LogFileWriter.logInfo(this, TAG, "=== StepCounterService.onDestroy() called ===");
        super.onDestroy();
        stopCounting();
    }

    private void onTargetReached() {
        LogFileWriter.logInfo(this, TAG, "Target step count reached, stopping alarm");

        // 1. Stop counting
        stopCounting();

        // 2. Notify app to stop alarm sound (choose one mechanism):

        // a) Send broadcast
        Intent intent = new Intent("com.example.stepalarm.ACTION_STEPS_COMPLETED");
        sendBroadcast(intent);

        // 3. Optionally stop foreground & service
        stopForeground(true);
        stopSelf();
    }
}