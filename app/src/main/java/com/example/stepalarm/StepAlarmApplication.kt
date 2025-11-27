package com.example.stepalarm

import android.app.Application
import android.util.Log

class StepAlarmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Set up global exception handler to log crashes
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                LogFileWriter.logError(this, "UncaughtException", 
                    "Uncaught exception in thread: ${thread.name}", exception)
            } catch (e: Exception) {
                Log.e("StepAlarmApplication", "Error logging uncaught exception", e)
            }
            
            // Call the default handler
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            defaultHandler?.uncaughtException(thread, exception)
        }
        
        LogFileWriter.logInfo(this, "StepAlarmApplication", "Application started")
    }
}

