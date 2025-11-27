package com.example.stepalarm;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogFileWriter {
    private static final String TAG = "LogFileWriter";
    private static final String LOG_FILE_NAME = "step_alarm_logs.txt";
    private static final int MAX_LOG_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    
    public static void log(Context context, String level, String tag, String message) {
        log(context, level, tag, message, null);
    }
    
    public static void log(Context context, String level, String tag, String message, Throwable throwable) {
        try {
            File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
            
            // Rotate log file if it's too large
            if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                File backupFile = new File(context.getFilesDir(), "step_alarm_logs_backup.txt");
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                logFile.renameTo(backupFile);
                logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
            }
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            String timestamp = sdf.format(new Date());
            
            StringBuilder logEntry = new StringBuilder();
            logEntry.append(timestamp).append(" [").append(level).append("] ");
            logEntry.append(tag).append(": ").append(message);
            
            if (throwable != null) {
                logEntry.append("\n").append(getStackTraceString(throwable));
            }
            
            logEntry.append("\n");
            
            // Also log to Android logcat
            if ("ERROR".equals(level)) {
                Log.e(tag, message, throwable);
            } else if ("WARN".equals(level)) {
                Log.w(tag, message, throwable);
            } else if ("DEBUG".equals(level)) {
                Log.d(tag, message, throwable);
            } else {
                Log.i(tag, message, throwable);
            }
            
            // Write to file
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.append(logEntry.toString());
                writer.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file", e);
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
    
    private static String getStackTraceString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
    
    public static File getLogFile(Context context) {
        return new File(context.getFilesDir(), LOG_FILE_NAME);
    }
}

