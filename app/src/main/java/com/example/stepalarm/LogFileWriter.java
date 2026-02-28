package com.example.stepalarm;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Writes logs to the public Downloads folder so they are accessible
 * from the phone's file manager: Internal storage / Download / step_alarm_log_TIMESTAMP.txt
 *
 * Uses MediaStore on API 29+ (no permissions needed).
 * Falls back to direct file write on older APIs.
 */
public class LogFileWriter {
    private static final String TAG = "LogFileWriter";
    private static final String LOG_FILE_PREFIX = "step_alarm_log_";
    private static final Object LOCK = new Object();

    // Session state (one log file per app process lifetime)
    private static Uri mediaStoreUri = null;
    private static OutputStream mediaStoreStream = null;
    private static File directLogFile = null;
    private static String sessionTimestamp = null;
    private static boolean initAttempted = false;

    private static String getSessionTimestamp() {
        if (sessionTimestamp == null) {
            sessionTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        }
        return sessionTimestamp;
    }

    private static String getLogFileName() {
        return LOG_FILE_PREFIX + getSessionTimestamp() + ".txt";
    }

    /**
     * Creates the log file on first call. Tries, in order:
     * 1. MediaStore Downloads (API 29+, no permission needed, visible in file manager)
     * 2. Direct file in public Downloads (API < 29, needs WRITE_EXTERNAL_STORAGE)
     * 3. App-specific external dir (always works but hidden on Android 11+)
     * 4. Internal storage (always works, not visible in file manager)
     */
    private static void ensureInitialized(Context context) {
        if (initAttempted) return;
        initAttempted = true;

        String fileName = getLogFileName();

        // 1. MediaStore Downloads (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                mediaStoreUri = context.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (mediaStoreUri != null) {
                    mediaStoreStream = context.getContentResolver()
                            .openOutputStream(mediaStoreUri, "wa");
                    if (mediaStoreStream != null) {
                        Log.i(TAG, "Log file created: Download/" + fileName + " (MediaStore)");
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "MediaStore init failed", e);
                mediaStoreUri = null;
                mediaStoreStream = null;
            }
        }

        // 2. Direct file in public Downloads (API < 29)
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null) {
                dir.mkdirs();
                File f = new File(dir, fileName);
                new FileWriter(f, true).close(); // test write
                directLogFile = f;
                Log.i(TAG, "Log file created: " + f.getAbsolutePath());
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Direct Downloads write failed", e);
        }

        // 3. App-specific external dir
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir != null) {
                directLogFile = new File(dir, fileName);
                Log.i(TAG, "Log file (app external): " + directLogFile.getAbsolutePath());
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "External app dir failed", e);
        }

        // 4. Internal storage
        directLogFile = new File(context.getFilesDir(), fileName);
        Log.i(TAG, "Log file (internal): " + directLogFile.getAbsolutePath());
    }

    public static void log(Context context, String level, String tag, String message) {
        log(context, level, tag, message, null);
    }

    public static void log(Context context, String level, String tag, String message, Throwable throwable) {
        try {
            // Logcat first (always works, even if file write fails)
            if ("ERROR".equals(level))  Log.e(tag, message, throwable);
            else if ("WARN".equals(level))  Log.w(tag, message, throwable);
            else if ("DEBUG".equals(level)) Log.d(tag, message, throwable);
            else Log.i(tag, message, throwable);

            if (context == null) return;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            String timestamp = sdf.format(new Date());

            StringBuilder logEntry = new StringBuilder();
            logEntry.append(timestamp).append(" [").append(level).append("] ");
            logEntry.append("[").append(Thread.currentThread().getName()).append("] ");
            logEntry.append(tag).append(": ").append(message);
            if (throwable != null) {
                logEntry.append("\n").append(getStackTraceString(throwable));
            }
            logEntry.append("\n");

            synchronized (LOCK) {
                ensureInitialized(context);

                // Write via cached MediaStore stream
                if (mediaStoreStream != null) {
                    try {
                        mediaStoreStream.write(logEntry.toString().getBytes());
                        mediaStoreStream.flush();
                        return;
                    } catch (Exception e) {
                        Log.e(TAG, "MediaStore write failed", e);
                        try { mediaStoreStream.close(); } catch (Exception ignored) {}
                        mediaStoreStream = null;
                    }
                }

                // Write to direct file
                if (directLogFile != null) {
                    try (FileWriter writer = new FileWriter(directLogFile, true)) {
                        writer.append(logEntry.toString());
                        writer.flush();
                    } catch (Exception e) {
                        Log.e(TAG, "Direct file write failed", e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Log failed completely", e);
        }
    }

    public static void logError(Context context, String tag, String message) {
        log(context, "ERROR", tag, message);
    }

    public static void logError(Context context, String tag, String message, Throwable throwable) {
        log(context, "ERROR", tag, message, throwable);
    }

    public static void logWarning(Context context, String tag, String message) {
        log(context, "WARN", tag, message);
    }

    public static void logInfo(Context context, String tag, String message) {
        log(context, "INFO", tag, message);
    }

    public static void logDebug(Context context, String tag, String message) {
        log(context, "DEBUG", tag, message);
    }

    /** Returns a URI that can be used for sharing / viewing the log. */
    public static Uri getLogFileUri(Context context) {
        synchronized (LOCK) {
            ensureInitialized(context);
            if (mediaStoreUri != null) return mediaStoreUri;
            if (directLogFile != null) return Uri.fromFile(directLogFile);
            return null;
        }
    }

    /** Returns the direct File, or null when using MediaStore. */
    public static File getLogFile(Context context) {
        synchronized (LOCK) {
            ensureInitialized(context);
            return directLogFile;
        }
    }

    /** Reads all log content (works for both MediaStore and direct file). */
    public static String readLogContent(Context context) {
        synchronized (LOCK) {
            ensureInitialized(context);

            // Try MediaStore URI
            if (mediaStoreUri != null) {
                try {
                    InputStream is = context.getContentResolver().openInputStream(mediaStoreUri);
                    if (is != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        reader.close();
                        return sb.toString();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read MediaStore log", e);
                }
            }

            // Try direct file
            if (directLogFile != null && directLogFile.exists()) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(directLogFile));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();
                    return sb.toString();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read direct log file", e);
                }
            }

            return "";
        }
    }

    private static String getStackTraceString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}

