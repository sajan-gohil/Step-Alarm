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
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var alarmsRecyclerView: RecyclerView
    private lateinit var addAlarmButton: FloatingActionButton
    private lateinit var alarmAdapter: AlarmAdapter
    private lateinit var alarmDatabase: AlarmDatabase

    private val addAlarmLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadAlarms()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check permission again after returning from settings
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.overlay_permission_granted), Toast.LENGTH_SHORT).show()
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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        alarmDatabase = AlarmDatabase(this)
        alarmsRecyclerView = findViewById(R.id.alarmsRecyclerView)
        addAlarmButton = findViewById(R.id.addAlarmButton)

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

        addAlarmButton.setOnClickListener {
            val intent = Intent(this, AddAlarmActivity::class.java)
            addAlarmLauncher.launch(intent)
        }

        // Check for required permissions
        checkOverlayPermission()
        checkActivityRecognitionPermission()

        loadAlarms()
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
            val logFile = LogFileWriter.getLogFile(this)
            if (logFile.exists() && logFile.length() > 0) {
                val logContent = logFile.readText()
                AlertDialog.Builder(this)
                    .setTitle("App Logs")
                    .setMessage(logContent.takeLast(5000)) // Show last 5000 characters
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Share") { _, _ ->
                        shareLogFile(logFile)
                    }
                    .show()
            } else {
                Toast.makeText(this, "No logs available yet", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading logs: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareLogFile(logFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                logFile
            )
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
        super.onResume()
        // Re-check permissions in case they were revoked
        checkOverlayPermission()
        checkActivityRecognitionPermission()
        loadAlarms()
    }

    private fun loadAlarms() {
        val alarms = alarmDatabase.getAllAlarms().sortedBy { it.hour * 60 + it.minute }
        alarmAdapter.updateAlarms(alarms)
    }

    private fun toggleAlarm(alarm: Alarm) {
        val updatedAlarm = alarmDatabase.toggleAlarm(alarm.id) ?: return
        
        if (updatedAlarm.isEnabled) {
            AlarmScheduler.scheduleAlarm(this, updatedAlarm)
        } else {
            AlarmScheduler.cancelAlarm(this, alarm)
        }
        
        loadAlarms()
    }

    private fun deleteAlarm(alarm: Alarm) {
        AlarmScheduler.cancelAlarm(this, alarm)
        alarmDatabase.deleteAlarm(alarm.id)
        loadAlarms()
    }
} 