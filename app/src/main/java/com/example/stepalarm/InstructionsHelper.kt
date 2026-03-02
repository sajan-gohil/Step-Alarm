package com.example.stepalarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * Shows an instruction / how-to dialog that detects available sensors
 * and tailors the information accordingly. Shown on first launch and
 * accessible from the overflow ⋮ menu.
 */
object InstructionsHelper {

    private const val PREFS_NAME = "step_alarm_prefs"
    private const val KEY_INSTRUCTIONS_SHOWN = "instructions_shown_v1"

    // ── Public API ──────────────────────────────────────────────

    /** Show only on first launch (writes a pref so it won't show again). */
    fun showOnFirstLaunch(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INSTRUCTIONS_SHOWN, false)) {
            // Only mark as seen after the user taps "Got it" so permission flows don't dismiss it silently
            show(context) {
                prefs.edit().putBoolean(KEY_INSTRUCTIONS_SHOWN, true).apply()
            }
        }
    }

    /** Show unconditionally (for the menu action). */
    fun show(context: Context, onAcknowledged: (() -> Unit)? = null) {
        val sensorInfo = detectSensors(context)
        val html = buildInstructionsHtml(sensorInfo)

        val scrollView = ScrollView(context).apply {
            setPadding(dp(context, 24), dp(context, 16), dp(context, 24), dp(context, 8))
        }
        val textView = TextView(context).apply {
            text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            textSize = 15f
            setLineSpacing(0f, 1.3f)
            movementMethod = LinkMovementMethod.getInstance()
        }
        scrollView.addView(textView)

        AlertDialog.Builder(context)
            .setTitle("How to Use Step Alarm")
            .setView(scrollView)
            .setPositiveButton("Got it!") { dialog, _ ->
                onAcknowledged?.invoke()
                dialog?.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    // ── Sensor detection ────────────────────────────────────────

    private data class SensorInfo(
        val hasStepDetector: Boolean,
        val hasStepCounter: Boolean,
        val hasAccelerometer: Boolean
    ) {
        val sensorMode: String
            get() = when {
                hasStepDetector && hasAccelerometer -> "Ensemble (Step Detector + Accelerometer)"
                hasStepDetector -> "Step Detector"
                hasStepCounter -> "Step Counter"
                hasAccelerometer -> "Accelerometer (fallback)"
                else -> "None detected"
            }
    }

    private fun detectSensors(context: Context): SensorInfo {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        return SensorInfo(
            hasStepDetector = sm?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null,
            hasStepCounter = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null,
            hasAccelerometer = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        )
    }

    // ── HTML content builder ────────────────────────────────────

    private fun buildInstructionsHtml(info: SensorInfo): String {
        val sb = StringBuilder()

        // Section 1 – Overview
        sb.append("<b>Welcome to Step Alarm!</b><br><br>")
        sb.append("Step Alarm is a wake-up alarm that <b>only turns off after you walk 10 steps</b>. ")
        sb.append("This forces you to physically get out of bed — no snooze button!<br><br>")

        // Section 2 – How it works
        sb.append("<big><b>How It Works</b></big><br>")
        sb.append("1. Set an alarm time from the main screen.<br>")
        sb.append("2. When the alarm fires, a full-screen alarm page appears.<br>")
        sb.append("3. Walk <b>10 clear, deliberate steps</b> to dismiss the alarm.<br>")
        sb.append("4. The screen will show your progress in real time.<br><br>")

        // Section 3 – Walking tips
        sb.append("<big><b>How to Walk for Best Results</b></big><br>")
        sb.append("&#x2022; <b>Walk slowly and deliberately</b> — quick shuffles may not register.<br>")
        sb.append("&#x2022; Take <b>full strides</b>; each foot should clearly lift and land.<br>")
        sb.append("&#x2022; Keep the phone <b>in your hand or pocket</b> (not lying on a surface).<br>")
        sb.append("&#x2022; Wait roughly <b>half a second</b> between each step for reliable counting.<br><br>")

        // Section 4 – Sensor info & disclaimers
        sb.append("<big><b>Your Device's Sensors</b></big><br>")
        sb.append("Step Alarm uses hardware sensors to count steps. Here is what was detected on your device:<br><br>")

        sb.append("&#x2022; Step Detector: <b>${if (info.hasStepDetector) "✓ Available" else "✗ Not available"}</b><br>")
        sb.append("&#x2022; Step Counter: <b>${if (info.hasStepCounter) "✓ Available" else "✗ Not available"}</b><br>")
        sb.append("&#x2022; Accelerometer: <b>${if (info.hasAccelerometer) "✓ Available" else "✗ Not available"}</b><br><br>")

        sb.append("Active mode: <b>${info.sensorMode}</b><br><br>")

        // Mode-specific disclaimers
        when {
            info.hasStepDetector && info.hasAccelerometer -> {
                sb.append("<i>Your device supports <b>ensemble mode</b> — the step detector and accelerometer ")
                sb.append("work together to confirm each step. This provides the most accurate counting ")
                sb.append("and filters out false triggers.</i><br><br>")
            }
            info.hasStepDetector -> {
                sb.append("<i>Your device has a step detector but no accelerometer confirmation. ")
                sb.append("Step counting should work well, but walk slowly to avoid jitter.</i><br><br>")
            }
            info.hasStepCounter -> {
                sb.append("<i>Your device uses a cumulative step counter. ")
                sb.append("<b>Steps may arrive in small batches</b> rather than one at a time — ")
                sb.append("the count might jump by 2–3 at once. This is normal hardware behavior ")
                sb.append("and does not affect the final count.</i><br><br>")
            }
            info.hasAccelerometer -> {
                sb.append("<i>Your device is using <b>accelerometer-only mode</b> (no dedicated step sensor). ")
                sb.append("Step detection works by analyzing motion patterns. Walk with clear, firm strides ")
                sb.append("for the best results. Accuracy may vary.</i><br><br>")
            }
            else -> {
                sb.append("<i><b>Warning:</b> No step-counting sensors were detected on your device. ")
                sb.append("The alarm may not be able to detect steps.</i><br><br>")
            }
        }

        // Section 5 – General disclaimers
        sb.append("<big><b>Good to Know</b></big><br>")
        sb.append("&#x2022; The alarm requires <b>\"Display over other apps\"</b> permission so it can show on the lock screen.<br>")
        sb.append("&#x2022; On Android 10+, <b>Activity Recognition</b> permission is needed for the step sensor to work.<br>")
        sb.append("&#x2022; Make sure <b>battery optimization</b> is not killing the app in the background.<br>")
        sb.append("&#x2022; Step counting accuracy depends on your device hardware. If steps aren't registering, try walking more slowly.<br>")

        return sb.toString()
    }

    // ── Utility ─────────────────────────────────────────────────

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
