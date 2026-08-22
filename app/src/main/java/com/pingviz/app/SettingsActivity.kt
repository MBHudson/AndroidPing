package com.pingviz.app

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var intervalInput: EditText
    private lateinit var themeGroup: RadioGroup
    private lateinit var alarmGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val s = prefs.loadSettings()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Settings"
            textSize = 22f
            setTextColor(Color.rgb(0, 100, 90))
        })

        // Ping interval
        root.addView(label("Ping interval (seconds)"))
        intervalInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(s.pingIntervalSeconds.toString())
        }
        root.addView(intervalInput)
        root.addView(TextView(this).apply {
            text = "How often each target is probed (default 1s)."
            textSize = 12f
            setTextColor(Color.GRAY)
        })

        // Theme
        root.addView(label("Theme"))
        themeGroup = RadioGroup(this).apply { orientation = LinearLayout.VERTICAL }
        val modes = mapOf(
            "System default" to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            "Light" to AppCompatDelegate.MODE_NIGHT_NO,
            "Dark" to AppCompatDelegate.MODE_NIGHT_YES
        )
        for ((label, mode) in modes) {
            val rb = RadioButton(this).apply { text = label; tag = mode }
            themeGroup.addView(rb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            if (s.themeMode == mode) themeGroup.check(rb.id)
        }
        root.addView(themeGroup)

        root.addView(label("Alarm"))
        alarmGroup = RadioGroup(this).apply { orientation = LinearLayout.VERTICAL }
        val alarms = mapOf(
            "Off" to false,
            "On (beep when a target is unreachable)" to true
        )
        for ((label, on) in alarms) {
            val rb = RadioButton(this).apply { text = label; tag = on }
            alarmGroup.addView(rb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            if (s.alarmEnabled == on) alarmGroup.check(rb.id)
        }
        root.addView(alarmGroup)

        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener { onSave() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })

        setContentView(root)
    }

    private fun onSave() {
        val interval = intervalInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
        val themeMode = (themeGroup.findViewById<RadioButton>(themeGroup.checkedRadioButtonId)
            ?.tag as? Int) ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        val alarm = (alarmGroup.findViewById<RadioButton>(alarmGroup.checkedRadioButtonId)
            ?.tag as? Boolean) ?: false

        val s = prefs.loadSettings()
        s.pingIntervalSeconds = interval
        s.themeMode = themeMode
        s.alarmEnabled = alarm
        prefs.saveSettings(s)

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()

        // Apply theme: switch mode then finish back to MainActivity which reapplies.
        AppCompatDelegate.setDefaultNightMode(themeMode)
        finish()
    }

    private fun label(txt: String): TextView = TextView(this).apply {
        text = txt
        textSize = 14f
        setTextColor(Color.GRAY)
        setPadding(0, dp(12), 0, dp(2))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
