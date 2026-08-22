package com.pingviz.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_ADD = 1
        private const val REQ_EDIT = 2
    }

    private lateinit var prefs: Prefs
    private lateinit var targets: MutableList<Target>
    private lateinit var settings: AppSettings
    private lateinit var engine: PingEngine
    private lateinit var chart: LiveChartView
    private lateinit var targetList: LinearLayout
    private lateinit var alarmSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = Prefs(this)
        settings = prefs.loadSettings()
        applyTheme(settings.themeMode)

        targets = prefs.loadTargets()

        engine = PingEngine(this)
        engine.intervalMs = settings.pingIntervalSeconds * 1000L
        engine.alarmEnabled = settings.alarmEnabled
        engine.updateTargets(targets)
        engine.onDataChanged = { if (::chart.isInitialized) chart.invalidate() }

        buildUi()
        engine.start()
    }

    override fun onResume() {
        super.onResume()
        // Pick up settings (interval, alarm, theme) changed in SettingsActivity.
        val latest = prefs.loadSettings()
        engine.intervalMs = latest.pingIntervalSeconds * 1000L
        if (latest.alarmEnabled != settings.alarmEnabled) {
            settings.alarmEnabled = latest.alarmEnabled
        }
        engine.alarmEnabled = latest.alarmEnabled
        if (::alarmSwitch.isInitialized) alarmSwitch.isChecked = latest.alarmEnabled
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.stop()
    }

    private fun applyTheme(mode: Int) {
        if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != mode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
            recreate()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        // Top bar: title, alarm toggle, settings.
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = "PingViz"
            setTextColor(Color.rgb(0, 100, 90))
            textSize = 26f
            setTextColor(resolveColorAttr(android.R.attr.textColorPrimary))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        alarmSwitch = SwitchCompat(this).apply { text = "Alarm" }
        top.addView(alarmSwitch)
        top.addView(Button(this).apply {
            text = "\u2699\uFE0F"
            textSize = 18f
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        root.addView(top)

        // Chart.
        chart = LiveChartView(this).apply {
            dataSource = { engine.series.values.toList() }
        }
        root.addView(chart, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Add target button.
        root.addView(Button(this).apply {
            text = "+  Add Target"
            setOnClickListener {
                startActivityForResult(Intent(this@MainActivity, AddTargetActivity::class.java), REQ_ADD)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        // Target list.
        targetList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(targetList) }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        alarmSwitch.isChecked = settings.alarmEnabled
        alarmSwitch.setOnCheckedChangeListener { _, checked ->
            settings.alarmEnabled = checked
            engine.alarmEnabled = checked
            prefs.saveSettings(settings)
        }

        setContentView(root)
        refreshTargetList()
    }

    private fun refreshTargetList() {
        targetList.removeAllViews()
        if (targets.isEmpty()) {
            targetList.addView(TextView(this).apply {
                text = "No targets. Tap \"+ Add Target\" to start monitoring."
                setTextColor(resolveColorAttr(android.R.attr.textColorSecondary))
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
        } else {
            for (t in targets) addTargetRow(t)
        }
    }

    private fun addTargetRow(t: Target) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(5), dp(2), dp(5))
        }

        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(t.color)
            }
        }
        row.addView(dot, LinearLayout.LayoutParams(dp(30), dp(30)).apply {
            setMargins(0, 0, dp(10), 0)
        })

        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(this).apply {
            text = t.displayName()
            textSize = 17f
            setTextColor(resolveColorAttr(android.R.attr.textColorPrimary))
        })
        textCol.addView(TextView(this).apply {
            text = "${t.host}  ·  ${t.transportDetail()}"
            textSize = 13f
            setTextColor(resolveColorAttr(android.R.attr.textColorSecondary))
        })
        row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(Button(this).apply { text = "Edit" }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(Button(this).apply {
            text = "Remove"
            setOnClickListener {
                targets.removeAll { it.id == t.id }
                prefs.saveTargets(targets)
                engine.updateTargets(targets)
                refreshTargetList()
            }
        })
        row.setOnClickListener { openEditor(t.id) }

        targetList.addView(row)
    }

    private fun openEditor(id: String) {
        val i = Intent(this, AddTargetActivity::class.java)
        i.putExtra("editId", id)
        startActivityForResult(i, REQ_EDIT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        val name = data.getStringExtra("name").orEmpty()
        val host = data.getStringExtra("host").orEmpty().trim()
        val port = data.getIntExtra("port", 0)
        val transport = runCatching { Transport.valueOf(data.getStringExtra("transport") ?: "ICMP") }
            .getOrDefault(Transport.ICMP)
        val color = data.getIntExtra("color", 0xFF2196F3.toInt())
        if (host.isEmpty()) {
            Toast.makeText(this, "Target host is required", Toast.LENGTH_SHORT).show()
            return
        }
        val editId = data.getStringExtra("editId")
        if (editId != null) {
            val idx = targets.indexOfFirst { it.id == editId }
            if (idx >= 0) {
                val t = targets[idx]
                t.name = name
                t.host = host
                t.port = port
                t.transport = transport
                t.color = color
            }
        } else {
            targets.add(Target(name = name, host = host, port = port, transport = transport, color = color))
        }
        prefs.saveTargets(targets)
        engine.updateTargets(targets)
        refreshTargetList()
    }

    private fun resolveColorAttr(attr: Int): Int {
        val a = TypedValue()
        theme.resolveAttribute(attr, a, true)
        return a.data
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
