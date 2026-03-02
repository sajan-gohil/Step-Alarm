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
    private Sensor stepCounterSensor;
    private long stepCount = 0;
    private boolean isCounting = false;
    private long lastLogTime = 0;
    private static final long LOG_INTERVAL = 2000;

    // === Ensemble timing window ===
    // Every 1 second we evaluate all sources and advance steps by at most 1
    // (or by the step-detector delta if it reported more).
    private static final long ENSEMBLE_WINDOW_MS = 1000;
    private long windowStartTime = 0;

    // --- Accelerometer peak detection (horizontal + vertical) ---
    private float[] gravity = new float[3];
    private static final float ALPHA = 0.8f;
    private static final int MAG_BUFFER_SIZE = 20; // ~20 samples at GAME rate ≈ window
    private final float[] magnitudeBuffer = new float[MAG_BUFFER_SIZE];
    private int magBufIdx = 0;
    private boolean magBufFilled = false;
    private float prevSmoothed = 0;
    private float prevPrevSmoothed = 0;
    private int accelPeakCountInWindow = 0;  // number of smoothed peaks in the window
    private static final float HORIZONTAL_PEAK_THRESHOLD = 1.2f;  // raised to reduce hand-shake false positives

    // --- Step Counter sensor (cumulative since reboot) ---
    private boolean useStepCounter = false;
    private long initialStepCounterValue = -1;
    private boolean initialValueSet = false;
    private long lastKnownStepCounterSteps = 0; // steps derived from counter at window start
    private boolean stepCounterIncreasedInWindow = false;
    private long stepCounterDeltaInWindow = 0;

    // --- Step Detector sensor (event-based) ---
    private boolean useStepDetector = false;
    private int stepDetectorEventsInWindow = 0;

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
        
        // Always grab accelerometer (required for horizontal peak detection)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        // Try to get step detector and step counter too (ensemble uses all available)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        stepCounterSensor  = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        
        useStepDetector = (stepDetectorSensor != null);
        useStepCounter  = (stepCounterSensor  != null);
        
        StringBuilder sb = new StringBuilder("Ensemble sensors: accel=");
        sb.append(accelerometerSensor != null);
        sb.append(", stepDetector=").append(useStepDetector);
        sb.append(", stepCounter=").append(useStepCounter);
        LogFileWriter.logInfo(this, TAG, sb.toString());

        if (accelerometerSensor == null && !useStepDetector && !useStepCounter) {
            LogFileWriter.logError(this, TAG, "No step counting sensors found — stopping");
            stopSelf();
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
        if (!isCounting || event == null) return;
        
        long now = System.currentTimeMillis();
        boolean shouldLog = (now - lastLogTime) >= LOG_INTERVAL;
        if (shouldLog) lastLogTime = now;
        
        // Initialise window on first event
        if (windowStartTime == 0) {
            windowStartTime = now;
        }
        
        // --- Accumulate evidence within the current 500 ms window ---
        
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            processAccelerometer(event);
        }
        
        if (useStepDetector && event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            if (event.values[0] == 1.0f) {
                stepDetectorEventsInWindow++;
            }
        }
        
        if (useStepCounter && event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            long raw = (long) event.values[0];
            if (!initialValueSet) {
                initialStepCounterValue = raw;
                initialValueSet = true;
                lastKnownStepCounterSteps = 0;
            } else {
                long derived = raw - initialStepCounterValue;
                if (derived < 0) { // reboot
                    initialStepCounterValue = raw;
                    derived = 0;
                }
                if (derived > lastKnownStepCounterSteps) {
                    stepCounterDeltaInWindow += (derived - lastKnownStepCounterSteps);
                    stepCounterIncreasedInWindow = true;
                    lastKnownStepCounterSteps = derived;
                }
            }
        }
        
        // --- End of window? Evaluate and advance step count ---
        if ((now - windowStartTime) >= ENSEMBLE_WINDOW_MS) {
            evaluateWindow(shouldLog);
            // Reset window
            windowStartTime = now;
            accelPeakCountInWindow = 0;
            stepDetectorEventsInWindow = 0;
            stepCounterIncreasedInWindow = false;
            stepCounterDeltaInWindow = 0;
        }
    }

    /**
     * Evaluate one 1-second ensemble window.
     *  - If step detector fired, trust its count (usually 1).
     *  - Else if accelerometer detected at least 1 smoothed local peak
     *    OR step counter increased → +1.
     */
    private void evaluateWindow(boolean shouldLog) {
        int increment = 0;
        String source = "";
        
        if (stepDetectorEventsInWindow > 0) {
            increment = stepDetectorEventsInWindow;
            source = "stepDetector(" + increment + ")";
        } else if (accelPeakCountInWindow >= 1 || stepCounterIncreasedInWindow) {
            // Require at least 1 real smoothed peak from accelerometer, or step counter says so
            increment = 1;
            source = (accelPeakCountInWindow >= 1) ? "accelPeak(" + accelPeakCountInWindow + ")" : "stepCounter";
            if (accelPeakCountInWindow >= 1 && stepCounterIncreasedInWindow) source = "accelPeak+stepCounter";
        }
        
        if (increment > 0) {
            stepCount += increment;
            if (shouldLog) {
                LogFileWriter.logInfo(this, TAG, String.format(
                    "Ensemble step: +%d (%s) total=%d", increment, source, stepCount));
            }
            if (stepCount >= 10) {
                onTargetReached();
            }
        }
    }

    /**
     * Process accelerometer readings to detect local peaks.
     * Uses horizontal (X, Y) and also checks vertical (Y-axis) movement.
     * A peak is a point where the smoothed magnitude exceeds both its neighbours
     * and is above HORIZONTAL_PEAK_THRESHOLD.
     */
    private void processAccelerometer(SensorEvent event) {
        // Low-pass filter to isolate gravity
        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];
        
        // Linear acceleration (remove gravity) — include X, Y, and Z
        float ax = event.values[0] - gravity[0];
        float ay = event.values[1] - gravity[1];
        float az = event.values[2] - gravity[2];
        
        // Full magnitude including vertical component for walking detection
        float rawMag = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        
        // Smooth with circular buffer
        magnitudeBuffer[magBufIdx] = rawMag;
        magBufIdx = (magBufIdx + 1) % MAG_BUFFER_SIZE;
        if (magBufIdx == 0) magBufFilled = true;
        
        int count = magBufFilled ? MAG_BUFFER_SIZE : magBufIdx;
        if (count < 3) {
            prevPrevSmoothed = prevSmoothed;
            prevSmoothed = rawMag;
            return;
        }
        
        float sum = 0;
        for (int i = 0; i < count; i++) sum += magnitudeBuffer[i];
        float smoothed = sum / count;
        
        // Local peak: previous smoothed value is greater than both its neighbours
        // and above the threshold — this filters out hand shaking noise
        if (prevSmoothed > prevPrevSmoothed && prevSmoothed > smoothed
                && prevSmoothed > HORIZONTAL_PEAK_THRESHOLD) {
            accelPeakCountInWindow++;
        }
        
        prevPrevSmoothed = prevSmoothed;
        prevSmoothed = smoothed;
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
        
        resetStepCount();
        
        int sensorDelay = SensorManager.SENSOR_DELAY_GAME;
        
        // Register all available sensors for ensemble evaluation
        if (accelerometerSensor != null) {
            boolean ok = sensorManager.registerListener(this, accelerometerSensor, sensorDelay);
            LogFileWriter.logInfo(this, TAG, "Accelerometer registered: " + ok);
        }
        if (useStepDetector) {
            boolean ok = sensorManager.registerListener(this, stepDetectorSensor, sensorDelay);
            LogFileWriter.logInfo(this, TAG, "Step detector registered: " + ok);
        }
        if (useStepCounter) {
            boolean ok = sensorManager.registerListener(this, stepCounterSensor, sensorDelay);
            LogFileWriter.logInfo(this, TAG, "Step counter registered: " + ok);
        }
        
        isCounting = true;
        LogFileWriter.logInfo(this, TAG, "Step counting is now active (ensemble mode)");
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
        initialStepCounterValue = -1;
        initialValueSet = false;
        lastKnownStepCounterSteps = 0;
        gravity[0] = 0;
        gravity[1] = 0;
        gravity[2] = 0;
        // Reset ensemble window state
        windowStartTime = 0;
        accelPeakCountInWindow = 0;
        stepDetectorEventsInWindow = 0;
        stepCounterIncreasedInWindow = false;
        stepCounterDeltaInWindow = 0;
        prevSmoothed = 0;
        prevPrevSmoothed = 0;
        magBufIdx = 0;
        magBufFilled = false;
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