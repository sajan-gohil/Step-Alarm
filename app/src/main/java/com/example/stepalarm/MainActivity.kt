package com.example.stepalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.install.model.AppUpdateType as PlayCoreAppUpdateType

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var alarmsRecyclerView: RecyclerView
    private lateinit var addAlarmButton: FloatingActionButton
    private lateinit var feedbackButton: ExtendedFloatingActionButton
    private lateinit var alarmAdapter: AlarmAdapter
    private lateinit var alarmDatabase: AlarmDatabase
    private var isReturningFromPermissionSettings = false

    private val IN_APP_UPDATE_REQUEST_CODE = 1234
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }

    private val addAlarmLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadAlarms()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isReturningFromPermissionSettings = true
        // Check permission again after returning from settings
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.overlay_permission_granted), Toast.LENGTH_SHORT).show()
            // Restart the app to ensure the permission is properly recognized
            restartApp()
        } else {
            // Permission still not granted, show info dialog
            showOverlayPermissionDialog()
        }
    }

    private val activityRecognitionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, getString(R.string.activity_recognition_permission_granted), Toast.LENGTH_SHORT).show()
        } else {
            // Permission denied, show info dialog
            showActivityRecognitionPermissionDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogFileWriter.logInfo(this, TAG, "=== MainActivity.onCreate() START ===")
        try {
            super.onCreate(savedInstanceState)
            LogFileWriter.logInfo(this, TAG, "super.onCreate() completed")

            setContentView(R.layout.activity_main)
            LogFileWriter.logInfo(this, TAG, "setContentView completed")

            val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
            setSupportActionBar(toolbar)
            LogFileWriter.logInfo(this, TAG, "Toolbar set up")

            try {
                checkForAppUpdate()
                LogFileWriter.logInfo(this, TAG, "checkForAppUpdate completed")
            } catch (e: Exception) {
                LogFileWriter.logError(this, TAG, "checkForAppUpdate failed (non-fatal)", e)
            }

            alarmDatabase = AlarmDatabase(this)
            LogFileWriter.logInfo(this, TAG, "AlarmDatabase initialized")

            alarmsRecyclerView = findViewById(R.id.alarmsRecyclerView)
            addAlarmButton = findViewById(R.id.addAlarmButton)
            LogFileWriter.logInfo(this, TAG, "Views found")

            alarmAdapter = AlarmAdapter(
                alarms = emptyList(),
                onToggleEnabled = { alarm ->
                    toggleAlarm(alarm)
                },
                onDelete = { alarm ->
                    deleteAlarm(alarm)
                }
            )

            alarmsRecyclerView.layoutManager = LinearLayoutManager(this)
            alarmsRecyclerView.adapter = alarmAdapter
            LogFileWriter.logInfo(this, TAG, "RecyclerView set up")

            addAlarmButton.setOnClickListener {
                LogFileWriter.logInfo(this, TAG, "Add alarm button clicked")
                val intent = Intent(this, AddAlarmActivity::class.java)
                addAlarmLauncher.launch(intent)
            }

            feedbackButton = findViewById(R.id.feedbackButton)
            feedbackButton.setOnClickListener {
                sendFeedbackWithLogs()
            }
            LogFileWriter.logInfo(this, TAG, "Buttons set up")

            // Check for required permissions
            LogFileWriter.logInfo(this, TAG, "Checking overlay permission")
            checkOverlayPermission()
            LogFileWriter.logInfo(this, TAG, "Checking activity recognition permission")
            checkActivityRecognitionPermission()

            LogFileWriter.logInfo(this, TAG, "Loading alarms")
            loadAlarms()
            LogFileWriter.logInfo(this, TAG, "=== MainActivity.onCreate() COMPLETED ===")
        } catch (e: Exception) {
            LogFileWriter.logError(this, TAG, "FATAL: onCreate() crashed", e)
            throw e
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.view_logs -> {
                viewLogs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun viewLogs() {
        try {
            val logContent = LogFileWriter.readLogContent(this)
            if (logContent.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("App Logs")
                    .setMessage(logContent.takeLast(5000)) // Show last 5000 characters
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Share") { _, _ ->
                        shareLogFile()
                    }
                    .show()
            } else {
                Toast.makeText(this, "No logs available yet", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading logs: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareLogFile() {
        try {
            val uri = LogFileWriter.getLogFileUri(this)
            if (uri == null) {
                Toast.makeText(this, "No log file available", Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Step Alarm Logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share logs"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing logs: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
        }
    }

    private fun checkActivityRecognitionPermission() {
        // ACTIVITY_RECOGNITION permission is required for Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showActivityRecognitionPermissionDialog()
            }
        }
    }

    private fun showActivityRecognitionPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.activity_recognition_permission_title))
            .setMessage(getString(R.string.activity_recognition_permission_message))
            .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                requestActivityRecognitionPermission()
            }
            .setNegativeButton(getString(R.string.later)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.overlay_permission_title))
            .setMessage(getString(R.string.overlay_permission_message))
            .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                requestOverlayPermission()
            }
            .setNegativeButton(getString(R.string.later)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    override fun onResume() {
        LogFileWriter.logInfo(this, TAG, "=== MainActivity.onResume() ===")
        super.onResume()
        // Only re-check permissions if not returning from permission settings
        // to avoid showing permission dialog again after granting
        if (!isReturningFromPermissionSettings) {
            checkOverlayPermission()
            checkActivityRecognitionPermission()
        }
        isReturningFromPermissionSettings = false
        loadAlarms()
    }

    override fun onDestroy() {
        LogFileWriter.logInfo(this, TAG, "=== MainActivity.onDestroy() ===")
        super.onDestroy()
    }

    private fun restartApp() {
        // Restart the app to ensure permission changes take effect
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
        // Force process restart
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun loadAlarms() {
        try {
            val alarms = alarmDatabase.getAllAlarms().sortedBy { it.hour * 60 + it.minute }
            LogFileWriter.logInfo(this, TAG, "Loaded ${alarms.size} alarms")
            alarmAdapter.updateAlarms(alarms)
        } catch (e: Exception) {
            LogFileWriter.logError(this, TAG, "Failed to load alarms", e)
        }
    }

    private fun toggleAlarm(alarm: Alarm) {
        LogFileWriter.logInfo(this, TAG, "Toggling alarm ${alarm.id} (currently enabled=${alarm.isEnabled})")
        try {
            val updatedAlarm = alarmDatabase.toggleAlarm(alarm.id) ?: return
            
            if (updatedAlarm.isEnabled) {
                AlarmScheduler.scheduleAlarm(this, updatedAlarm)
            } else {
                AlarmScheduler.cancelAlarm(this, alarm)
            }
            
            loadAlarms()
        } catch (e: Exception) {
            LogFileWriter.logError(this, TAG, "Failed to toggle alarm ${alarm.id}", e)
        }
    }

    private fun deleteAlarm(alarm: Alarm) {
        LogFileWriter.logInfo(this, TAG, "Deleting alarm ${alarm.id}")
        try {
            AlarmScheduler.cancelAlarm(this, alarm)
            alarmDatabase.deleteAlarm(alarm.id)
            loadAlarms()
        } catch (e: Exception) {
            LogFileWriter.logError(this, TAG, "Failed to delete alarm ${alarm.id}", e)
        }
    }

    private fun sendFeedbackWithLogs() {
        try {
            val emailIntent = Intent(Intent.ACTION_SEND)
            emailIntent.type = "message/rfc822"
            emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf("9000applications@gmail.com"))
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Feedback: Step Alarm")
            emailIntent.putExtra(Intent.EXTRA_TEXT, "Please share your feedback below:\n\n")
            
            // Attach logs if available
            val logUri = LogFileWriter.getLogFileUri(this)
            if (logUri != null) {
                emailIntent.putExtra(Intent.EXTRA_STREAM, logUri)
                emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooserIntent = Intent.createChooser(emailIntent, "Send Feedback")
            // Note: resolveActivity() returns null on Android 11+ due to package visibility.
            // Just try to start the activity and catch if nothing handles it.
            try {
                startActivity(chooserIntent)
            } catch (e: android.content.ActivityNotFoundException) {
                LogFileWriter.logWarning(this, TAG, "No email app available for feedback")
                Toast.makeText(this, "No email app available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to send feedback: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    /**
     * In-app update logic: check for updates and prompt user if needed
     */
    private fun checkForAppUpdate() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                // Try immediate update first
                if (appUpdateInfo.isUpdateTypeAllowed(PlayCoreAppUpdateType.IMMEDIATE)) {
                    // Start immediate update flow
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            PlayCoreAppUpdateType.IMMEDIATE,
                            this,
                            IN_APP_UPDATE_REQUEST_CODE
                        )
                    } catch (e: Exception) {
                        // Fallback: show dialog to go to Play Store
                        showUpdateDialog()
                    }
                } else {
                    // Immediate update not allowed, show dialog
                    showUpdateDialog()
                }
            }
        }
    }

    private fun showUpdateDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available_title))
            .setMessage(getString(R.string.update_available_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.update_now)) { _, _ ->
                // Open Google Play Store for this app
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Handle update flow result
        if (requestCode == IN_APP_UPDATE_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                // Update flow failed or canceled, show dialog
                showUpdateDialog()
            }
        }
    }
}