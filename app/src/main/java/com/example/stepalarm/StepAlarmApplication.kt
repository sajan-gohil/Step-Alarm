package com.example.stepalarm

import android.app.Application
import android.os.Build
import android.util.Log

class StepAlarmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        LogFileWriter.logInfo(this, "StepAlarmApplication", "========================================")
        LogFileWriter.logInfo(this, "StepAlarmApplication", "Application starting")
        LogFileWriter.logInfo(this, "StepAlarmApplication", "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        LogFileWriter.logInfo(this, "StepAlarmApplication", "Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        LogFileWriter.logInfo(this, "StepAlarmApplication", "App version: ${try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "unknown" }}")
        LogFileWriter.logInfo(this, "StepAlarmApplication", "========================================")
        
        // IMPORTANT: Save reference to the default handler BEFORE replacing it.
        // Previously this called getDefaultUncaughtExceptionHandler() inside the lambda,
        // which returned our own handler (infinite recursion → stack overflow on any crash).
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                LogFileWriter.logError(this, "UncaughtException", 
                    "Uncaught exception in thread: ${thread.name}", exception)
            } catch (e: Exception) {
                Log.e("StepAlarmApplication", "Error logging uncaught exception", e)
            }
            
            // Call the previously-saved default handler (not ourselves)
            previousHandler?.uncaughtException(thread, exception)
        }
        
        LogFileWriter.logInfo(this, "StepAlarmApplication", "Global exception handler installed successfully")
    }
}

