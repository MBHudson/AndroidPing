package com.pingviz.app

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AddTargetActivity : AppCompatActivity() {

    private val palette = listOf(
        0xFF2196F3.toInt(), 0xFFF44336.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt(),
        0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(), 0xFFFFEB3B.toInt(), 0xFF607D8B.toInt(),
        0xFFFF5722.toInt(), 0xFF3F51B5.toInt()
    )

    private lateinit var nameInput: EditText
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var transportSpinner: Spinner
    private lateinit var prefs: Prefs
    private var selectedColor = palette.first()
    private val colorViews = mutableListOf<Pair<View, Boolean>>()
    private var editId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = Prefs(this)
        editId = intent.getStringExtra("editId")

        val t: Target? = editId?.let { id -> prefs.loadTargets().firstOrNull { it.id == id } }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(TextView(this).apply { text = if (t == null) "Add Target" else "Edit Target"; textSize = 22f })
        root.addView(space())

        root.addView(label("Name (optional)"))
        nameInput = EditText(this).apply { hint = "e.g. Router" }
        root.addView(nameInput)

        root.addView(label("Host / IP / Domain"))
        hostInput = EditText(this).apply { hint = "e.g. 8.8.8.8 or example.com" }
        root.addView(hostInput)

        root.addView(label("Port (optional, blank = default)"))
        portInput = EditText(this).apply {
            hint = "80 / 443 / 8080 ..."
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(portInput)

        root.addView(label("Probe method"))
        transportSpinner = Spinner(this)
        val adapter = ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item,
            Transport.values().map { it.label })
        transportSpinner.adapter = adapter
        root.addView(transportSpinner)

        root.addView(label("Line color"))
        root.addView(buildPalette())

        if (t != null) {
            selectedColor = t.color
            nameInput.setText(t.name)
            hostInput.setText(t.host)
            if (t.port > 0) portInput.setText(t.port.toString())
            val pos = Transport.values().indexOfFirst { it == t.transport }
            if (pos >= 0) transportSpinner.setSelection(pos)
        }

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(Button(this).apply {
            text = "Cancel"
            setOnClickListener { setResult(Activity.RESULT_CANCELED); finish() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        btnRow.addView(Button(this).apply {
            text = "Save"
            setOnClickListener { onSave() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(btnRow)

        setContentView(root)
        refreshPalette()
    }

    private fun onSave() {
        val host = hostInput.text.toString().trim()
        if (host.isEmpty()) {
            Toast.makeText(this, "Host is required", Toast.LENGTH_SHORT).show()
            return
        }
        val port = portInput.text.toString().trim().toIntOrNull() ?: 0
        val transport = Transport.values()[transportSpinner.selectedItemPosition]
        val result = IntentResult(host, nameInput.text.toString().trim(), port, transport, selectedColor, editId)
        setResult(Activity.RESULT_OK, result.toIntent())
        finish()
    }

    private fun buildPalette(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in palette) {
            val cell = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
                tag = c
                setOnClickListener {
                    selectedColor = c as Int
                    refreshPalette()
                }
            }
            val size = dp(40)
            row.addView(cell, LinearLayout.LayoutParams(size, size).apply {
                setMargins(dp(6), dp(4), dp(6), dp(4))
            })
            colorViews.add(cell to false)
        }
        return row
    }

    private fun refreshPalette() {
        for ((view, _) in colorViews) {
            val c = view.tag as Int
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                if (c == selectedColor) {
                    setStroke(dp(4), Color.WHITE)
                }
            }
            view.background = gd
        }
    }

    private class IntentResult(
        val host: String, val name: String, val port: Int,
        val transport: Transport, val color: Int, val editId: String?
    ) {
        fun toIntent(): android.content.Intent = android.content.Intent().apply {
            putExtra("host", host)
            putExtra("name", name)
            putExtra("port", port)
            putExtra("transport", transport.name)
            putExtra("color", color)
            if (editId != null) putExtra("editId", editId)
        }
    }

    private fun label(s: String): TextView = TextView(this).apply {
        text = s
        textSize = 14f
        setTextColor(Color.GRAY)
        setPadding(0, dp(10), 0, dp(2))
    }

    private fun space(): View = View(this).apply { }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
