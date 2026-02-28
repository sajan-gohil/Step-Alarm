# Changelog

## [Unreleased]

### Bug Fixes
- **Fix crash on launch**: Fixed `ClassCastException` in `MainActivity` — `feedbackButton` was declared as `FloatingActionButton` but the layout uses `ExtendedFloatingActionButton` (which extends `MaterialButton`, not `FloatingActionButton`).
- **Fix AddAlarmActivity back button**: Added `onSupportNavigateUp()` override so the toolbar back arrow actually finishes the activity.
- **Fix infinite recursion in crash handler** (`StepAlarmApplication`): The global uncaught exception handler previously called `getDefaultUncaughtExceptionHandler()` inside the lambda, which returned the newly-set handler itself — causing a stack overflow on any crash. Now saves the previous handler reference before replacing it.
- **Fix AlarmReceiver crashes**: Replaced `throw RuntimeException` calls with graceful `return` when intent is null, alarm ID is missing, or alarm is not found in the database. Previously these would crash the app instead of failing gracefully.
- **Fix StepCounterService crashes**: Replaced `throw RuntimeException` with `stopSelf()` + `return` when `SensorManager` is null or no step sensors are found. Same for failed sensor listener registration.
- **Fix AlarmOverlayService crash on destroy**: Added null/state checks before calling `windowManager.removeView()` in `onDestroy()` to prevent crashes when the overlay was never added.
- **Fix AlarmOverlayService crash in onCreate**: Wrapped overlay creation in try-catch; if `WindowManager` is null or overlay creation fails, the service logs the error instead of crashing.
- **Fix MediaPlayer crash in AlarmReceiver**: Wrapped entire MediaPlayer setup (data source, audio attributes, prepare, start) in a single try-catch block so a failure doesn't prevent vibration and activity from starting.

### New Features
- **Send Feedback button**: Added a feedback FAB (`ExtendedFloatingActionButton`) on the main screen that opens an email compose intent with app logs attached.
- **Material Toolbar**: Added `MaterialToolbar` with back navigation to `AddAlarmActivity`, and a toolbar to `MainActivity`.
- **Logs written to Downloads folder**: `LogFileWriter` now writes logs to the public Downloads directory (via MediaStore on API 29+, direct file on older APIs) so users can easily find and share log files. Falls back to app-specific or internal storage if Downloads is unavailable.
- **Log file per session**: Each app process creates a new timestamped log file (`step_alarm_log_YYYYMMDD_HHmmss.txt`) instead of a single rotating file.
- **Device info in logs**: Application startup now logs device manufacturer, model, Android version, SDK level, and app version.
- **Thread name in log entries**: Each log line now includes the thread name for easier debugging of concurrency issues.

### Improvements
- **Pervasive try-catch and logging**: Added structured logging and exception handling throughout all activities, services, and receivers (`MainActivity`, `AddAlarmActivity`, `AlarmDatabase`, `AlarmScheduler`, `AlarmReceiver`, `AlarmOverlayService`, `BootReceiver`, `StepCounterService`).
- **Rate-limited sensor logging**: `StepCounterService` and `AlarmActivity` now throttle log output for sensor/step-update events to every 2 seconds, preventing log file flooding.
- **Removed noisy log calls**: Removed per-call logging from `getStepCount()`, removed "onSensorChanged called" log on every sensor event, and removed log when `isCounting` is false.
- **AlarmOverlayService state tracking**: Added `overlayAdded` flag to track whether the overlay view was successfully added, preventing updates to a non-existent overlay.
- **AlarmScheduler detailed scheduling logs**: Logs now include the exact trigger time (formatted date) and request codes for scheduled alarms.
- **Boot receiver logging**: `BootReceiver` now logs via `LogFileWriter` instead of `Log.d`, and handles unexpected actions gracefully.

### Permissions
- Added `WRITE_EXTERNAL_STORAGE` permission (max SDK 28) for writing logs to the public Downloads folder on older Android versions.
