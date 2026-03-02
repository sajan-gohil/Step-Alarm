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
    private static final String LOG_FILE_NAME = "step_alarm_logs.txt";
    private static final int MAX_LINES = 1500;
    private static final Object LOCK = new Object();

    // Persistent log file (single file across all app runs)
    private static File persistentLogFile = null;
    private static boolean initAttempted = false;

    private static String getLogFileName() {
        return LOG_FILE_NAME;
    }

    /**
     * Creates the persistent log file on first call.
     * Uses internal storage (always works, persists across app runs).
     */
    private static void ensureInitialized(Context context) {
        if (initAttempted) return;
        initAttempted = true;

        String fileName = getLogFileName();
        persistentLogFile = new File(context.getFilesDir(), fileName);
        Log.i(TAG, "Log file (persistent): " + persistentLogFile.getAbsolutePath());
    }

    /**
     * Trims the log file to keep only the last MAX_LINES lines.
     * Called after writing to prevent unbounded growth.
     */
    private static void trimLogFile() {
        if (persistentLogFile == null || !persistentLogFile.exists()) return;

        try {
            // Read all lines
            BufferedReader reader = new BufferedReader(new FileReader(persistentLogFile));
            java.util.List<String> lines = new java.util.ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();

            // Keep only last MAX_LINES
            java.util.List<String> trimmedLines;
            if (lines.size() > MAX_LINES) {
                trimmedLines = lines.subList(lines.size() - MAX_LINES, lines.size());
                try (FileWriter writer = new FileWriter(persistentLogFile, false)) {
                    for (String l : trimmedLines) {
                        writer.append(l).append("\n");
                    }
                    writer.flush();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to trim log file", e);
        }
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

                // Write to persistent file
                if (persistentLogFile != null) {
                    try (FileWriter writer = new FileWriter(persistentLogFile, true)) {
                        writer.append(logEntry.toString());
                        writer.flush();
                    } catch (Exception e) {
                        Log.e(TAG, "File write failed", e);
                    }
                    // Trim file if needed
                    trimLogFile();
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
            if (persistentLogFile != null && persistentLogFile.exists()) {
                return androidx.core.content.FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        persistentLogFile);
            }
            return null;
        }
    }

    /** Returns the persistent log file. */
    public static File getLogFile(Context context) {
        synchronized (LOCK) {
            ensureInitialized(context);
            return persistentLogFile;
        }
    }

    /** Reads all log content from the persistent file. */
    public static String readLogContent(Context context) {
        synchronized (LOCK) {
            ensureInitialized(context);

            if (persistentLogFile != null && persistentLogFile.exists()) {
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(persistentLogFile));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();
                    return sb.toString();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read log file", e);
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

