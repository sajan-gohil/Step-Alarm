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
    private long lastLogTime = 0;
    private static final long LOG_INTERVAL = 2000; // Only log sensor events every 2 seconds

    // === Enhanced step counting: debouncing ===
    private long lastConfirmedStepTime = 0;
    private static final long MIN_STEP_DELAY = 500; // 500ms minimum between counted steps

    // === Ensemble mode: step detector + accelerometer confirmation ===
    private boolean useEnsembleMode = false;
    private boolean accelerometerMotionConfirmed = false;
    private long lastAccelMotionTime = 0;
    private static final long MOTION_CONFIRM_WINDOW = 1000; // ms window for accel to confirm a step
    private int pendingStepDetectorEvents = 0; // step detector events awaiting accel confirmation

    // === Improved accelerometer analysis ===
    private static final int MAG_BUFFER_SIZE = 15; // circular buffer for smoothing
    private final float[] magnitudeBuffer = new float[MAG_BUFFER_SIZE];
    private int magBufIdx = 0;
    private boolean magBufFilled = false;
    private boolean inPeakPhase = false; // true = saw peak, waiting for valley to complete step cycle
    private static final float ACCEL_PEAK_THRESHOLD = 1.6f;  // magnitude must exceed this
    private static final float ACCEL_VALLEY_THRESHOLD = 0.6f; // magnitude must drop below this
    private static final float ENSEMBLE_MOTION_THRESHOLD = 1.2f; // lower threshold for ensemble confirmation

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
            LogFileWriter.logError(this, TAG, "SensorManager is null — service cannot function");
            stopSelf();
            return;
        }
        
        // Always try to get accelerometer for ensemble/fallback use
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        // Priority 1: Step Detector + Accelerometer ensemble (best accuracy)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        if (stepDetectorSensor != null && accelerometerSensor != null) {
            useStepDetector = true;
            useEnsembleMode = true;
            LogFileWriter.logInfo(this, TAG, "Step detector + accelerometer ensemble mode (Priority 1)");
        } else if (stepDetectorSensor != null) {
            useStepDetector = true;
            LogFileWriter.logInfo(this, TAG, "Step detector only (no accelerometer for ensemble)");
        } else {
            // Priority 2: Step Counter with debounce
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (stepCounterSensor != null) {
                useStepCounter = true;
                LogFileWriter.logInfo(this, TAG, "Step counter sensor found, will use it (Priority 2)");
            } else if (accelerometerSensor != null) {
                // Priority 3: Accelerometer only (improved peak detection)
                LogFileWriter.logInfo(this, TAG, "Accelerometer-only mode with peak detection (Priority 3)");
            } else {
                LogFileWriter.logError(this, TAG, "No step counting sensors found on this device — service cannot function");
                stopSelf();
                return;
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
        if (!isCounting) {
            return;
        }
        if (event == null) {
            LogFileWriter.logError(this, TAG, "onSensorChanged called with null event");
            return;
        }
        
        // Rate-limit logging to avoid flooding the log file
        long now = System.currentTimeMillis();
        boolean shouldLog = (now - lastLogTime) >= LOG_INTERVAL;
        if (shouldLog) {
            lastLogTime = now;
        }
        
        if (useStepCounter && event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            handleStepCounterEvent(event, shouldLog);
        } else if (useStepDetector && event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            handleStepDetectorEvent(event, shouldLog);
        }
        
        // Accelerometer processing for ensemble confirmation OR standalone mode
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            handleAccelerometerEvent(event, shouldLog);
        }
    }

    /**
     * Handle TYPE_STEP_COUNTER events (cumulative since reboot).
     * Applies MIN_STEP_DELAY debounce to avoid jitter.
     */
    private void handleStepCounterEvent(SensorEvent event, boolean shouldLog) {
        long stepsSinceLastReboot = (long) event.values[0];
        
        if (!initialValueSet) {
            initialStepCounterValue = stepsSinceLastReboot;
            initialValueSet = true;
            stepCount = 0;
            LogFileWriter.logInfo(this, TAG, "Initial step counter value set: " + initialStepCounterValue);
            return;
        }
        
        long rawSteps = stepsSinceLastReboot - initialStepCounterValue;
        if (rawSteps < 0) {
            initialStepCounterValue = stepsSinceLastReboot;
            rawSteps = 0;
            LogFileWriter.logWarning(this, TAG, "Step counter reset detected, reinitializing");
        }
        
        // Debounce: only update if MIN_STEP_DELAY has passed since last counted step
        long now = System.currentTimeMillis();
        if (rawSteps > stepCount && (now - lastConfirmedStepTime) >= MIN_STEP_DELAY) {
            stepCount = rawSteps;
            lastConfirmedStepTime = now;
        }
        
        if (shouldLog) {
            LogFileWriter.logInfo(this, TAG, String.format("StepCounter: total=%d, initial=%d, count=%d",
                stepsSinceLastReboot, initialStepCounterValue, stepCount));
        }
        if (stepCount >= 10) {
            onTargetReached();
        }
    }

    /**
     * Handle TYPE_STEP_DETECTOR events.
     * In ensemble mode: requires accelerometer motion confirmation within a time window.
     * Otherwise: applies MIN_STEP_DELAY debounce.
     */
    private void handleStepDetectorEvent(SensorEvent event, boolean shouldLog) {
        if (event.values[0] != 1.0f) return;
        
        long now = System.currentTimeMillis();
        
        // Enforce minimum delay between counted steps
        if ((now - lastConfirmedStepTime) < MIN_STEP_DELAY) {
            if (shouldLog) {
                LogFileWriter.logInfo(this, TAG, "Step detector event debounced (too fast)");
            }
            return;
        }
        
        if (useEnsembleMode) {
            // Ensemble: require accelerometer motion confirmation
            if (accelerometerMotionConfirmed && (now - lastAccelMotionTime) < MOTION_CONFIRM_WINDOW) {
                // Accelerometer recently confirmed motion — count this step
                stepCount++;
                lastConfirmedStepTime = now;
                accelerometerMotionConfirmed = false; // consume the confirmation
                LogFileWriter.logInfo(this, TAG, String.format(
                    "Ensemble step confirmed! Steps: %d", stepCount));
            } else {
                // No recent accelerometer confirmation — hold as pending
                pendingStepDetectorEvents++;
                if (shouldLog) {
                    LogFileWriter.logInfo(this, TAG, String.format(
                        "Step detector event pending accel confirmation (%d pending)", pendingStepDetectorEvents));
                }
                return; // don't count yet
            }
        } else {
            // Non-ensemble: just debounce
            stepCount++;
            lastConfirmedStepTime = now;
            LogFileWriter.logInfo(this, TAG, String.format("Step detected (debounced)! Steps: %d", stepCount));
        }
        
        if (stepCount >= 10) {
            onTargetReached();
        }
    }

    /**
     * Handle accelerometer events.
     * Uses smoothed magnitude with peak-valley detection.
     * In ensemble mode: confirms step detector events.
     * In standalone mode: counts steps directly.
     */
    private void handleAccelerometerEvent(SensorEvent event, boolean shouldLog) {
        // Low-pass filter to isolate gravity
        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];
        
        // Remove gravity → linear acceleration
        linearAcceleration[0] = event.values[0] - gravity[0];
        linearAcceleration[1] = event.values[1] - gravity[1];
        linearAcceleration[2] = event.values[2] - gravity[2];
        
        float rawMagnitude = (float) Math.sqrt(
            linearAcceleration[0] * linearAcceleration[0] +
            linearAcceleration[1] * linearAcceleration[1] +
            linearAcceleration[2] * linearAcceleration[2]
        );
        
        // Smooth magnitude with circular buffer moving average
        magnitudeBuffer[magBufIdx] = rawMagnitude;
        magBufIdx = (magBufIdx + 1) % MAG_BUFFER_SIZE;
        if (magBufIdx == 0) magBufFilled = true;
        
        int count = magBufFilled ? MAG_BUFFER_SIZE : magBufIdx;
        if (count == 0) return;
        
        float sum = 0;
        for (int i = 0; i < count; i++) sum += magnitudeBuffer[i];
        float smoothedMagnitude = sum / count;
        
        long currentTime = System.currentTimeMillis();
        
        if (useEnsembleMode) {
            // Ensemble: use accelerometer to confirm step detector events
            if (smoothedMagnitude > ENSEMBLE_MOTION_THRESHOLD) {
                accelerometerMotionConfirmed = true;
                lastAccelMotionTime = currentTime;
                
                // Check if there are pending step detector events to confirm
                if (pendingStepDetectorEvents > 0 && (currentTime - lastConfirmedStepTime) >= MIN_STEP_DELAY) {
                    stepCount++;
                    lastConfirmedStepTime = currentTime;
                    pendingStepDetectorEvents--;
                    accelerometerMotionConfirmed = false;
                    LogFileWriter.logInfo(this, TAG, String.format(
                        "Pending step confirmed by accel! Steps: %d, smoothedMag: %.3f",
                        stepCount, smoothedMagnitude));
                    if (stepCount >= 10) {
                        onTargetReached();
                    }
                }
            }
        } else if (!useStepCounter && !useStepDetector) {
            // Standalone accelerometer mode: peak-valley detection
            if ((currentTime - lastConfirmedStepTime) < MIN_STEP_DELAY) {
                lastMagnitude = smoothedMagnitude;
                return;
            }
            
            if (!inPeakPhase) {
                // Looking for peak: smoothed magnitude crosses above peak threshold
                if (smoothedMagnitude > ACCEL_PEAK_THRESHOLD && lastMagnitude <= ACCEL_PEAK_THRESHOLD) {
                    inPeakPhase = true;
                    if (shouldLog) {
                        LogFileWriter.logInfo(this, TAG, String.format(
                            "Accel peak detected, magnitude: %.3f", smoothedMagnitude));
                    }
                }
            } else {
                // Looking for valley: magnitude drops below valley threshold → step complete
                if (smoothedMagnitude < ACCEL_VALLEY_THRESHOLD) {
                    inPeakPhase = false;
                    stepCount++;
                    lastConfirmedStepTime = currentTime;
                    if (shouldLog) {
                        LogFileWriter.logInfo(this, TAG, String.format(
                            "Accel step (peak-valley)! Steps: %d, mag: %.3f", stepCount, smoothedMagnitude));
                    }
                    if (stepCount >= 10) {
                        onTargetReached();
                    }
                }
            }
            lastMagnitude = smoothedMagnitude;
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
            LogFileWriter.logInfo(this, TAG, "Registering step detector sensor");
            registered = sensorManager.registerListener(this, stepDetectorSensor, sensorDelay);
            if (!registered) {
                LogFileWriter.logError(this, TAG, "Failed to register step detector sensor listener");
                return;
            }
            // Ensemble: also register accelerometer for motion confirmation
            if (useEnsembleMode && accelerometerSensor != null) {
                boolean accelRegistered = sensorManager.registerListener(this, accelerometerSensor, sensorDelay);
                if (accelRegistered) {
                    LogFileWriter.logInfo(this, TAG, "Ensemble mode: step detector + accelerometer registered");
                } else {
                    useEnsembleMode = false;
                    LogFileWriter.logWarning(this, TAG, "Failed to register accelerometer for ensemble, falling back to step detector only");
                }
            } else {
                LogFileWriter.logInfo(this, TAG, "Started step counting using step detector (no ensemble)");
            }
        } else if (useStepCounter) {
            LogFileWriter.logInfo(this, TAG, "Registering step counter sensor");
            registered = sensorManager.registerListener(this, stepCounterSensor, sensorDelay);
            if (!registered) {
                LogFileWriter.logError(this, TAG, "Failed to register step counter sensor listener");
                return;
            }
            LogFileWriter.logInfo(this, TAG, "Started step counting using step counter sensor");
        } else {
            LogFileWriter.logInfo(this, TAG, "Registering accelerometer sensor (standalone peak-valley detection)");
            registered = sensorManager.registerListener(this, accelerometerSensor, sensorDelay);
            if (!registered) {
                LogFileWriter.logError(this, TAG, "Failed to register accelerometer sensor listener");
                return;
            }
            LogFileWriter.logInfo(this, TAG, "Started step counting using accelerometer peak-valley detection");
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
        // Reset enhanced counting state
        lastConfirmedStepTime = 0;
        accelerometerMotionConfirmed = false;
        lastAccelMotionTime = 0;
        pendingStepDetectorEvents = 0;
        magBufIdx = 0;
        magBufFilled = false;
        inPeakPhase = false;
        for (int i = 0; i < MAG_BUFFER_SIZE; i++) magnitudeBuffer[i] = 0;
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