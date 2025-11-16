package com.example.stepalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

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