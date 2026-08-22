package com.pingviz.app

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Add / edit a target. Builds its whole UI programmatically (no XML layout),
 * wrapped in a ScrollView so every field is reachable on any device.
 *
 * Fields: optional custom name, host / URL / IP / domain (required), optional
 * port (blank = transport default), probe method (ICMP / HTTP / HTTPS), and a
 * real color-picker spectrum (hue spectrum + brightness slider) for the line.
 */
class AddTargetActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var transportSpinner: Spinner
    private lateinit var spectrum: SpectrumView
    private lateinit var brightnessBar: SeekBar
    private lateinit var preview: View
    private lateinit var prefs: Prefs

    private var selectedHue = 219f   // default blue
    private var selectedBrightness = 1f
    private var editId: String? = null

    private val selectedColor: Int
        get() = Color.HSVToColor(floatArrayOf(selectedHue, 1f, selectedBrightness))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = Prefs(this)
        editId = intent.getStringExtra("editId")
        val t: Target? = editId?.let { id -> prefs.loadTargets().firstOrNull { it.id == id } }
        if (t != null) {
            val hsv = FloatArray(3)
            Color.colorToHSV(t.color, hsv)
            selectedHue = hsv[0]
            selectedBrightness = hsv[2]
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = if (t == null) "Add Target" else "Edit Target"
            textSize = 22f
            setTextColor(resolveColorAttr(android.R.attr.textColorPrimary))
        })
        root.addView(label("Name (optional, blank = use host)"))

        nameInput = EditText(this).apply { hint = "e.g. Router" }
        root.addView(nameInput)

        root.addView(label("Host / URL / IP / Domain"))
        hostInput = EditText(this).apply {
            hint = "e.g. 8.8.8.8 or example.com"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(hostInput)

        root.addView(label("Port (optional, blank = transport default)"))
        portInput = EditText(this).apply {
            hint = "80 / 443 / 8080 ..."
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(portInput)

        root.addView(label("Probe method"))
        transportSpinner = Spinner(this)
        transportSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item,
            Transport.values().map { it.label })
        root.addView(transportSpinner)

        root.addView(label("Line color — tap the spectrum, then set brightness"))
        spectrum = SpectrumView(this).apply {
            hue = selectedHue
            onHueChanged = { selectedHue = it; updatePreview() }
        }
        root.addView(spectrum, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(4) })

        val brightnessRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        brightnessRow.addView(TextView(this).apply {
            text = "Brightness"
            textSize = 14f
        })
        brightnessBar = SeekBar(this).apply {
            max = 100
            progress = (selectedBrightness * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    selectedBrightness = progress / 100f
                    updatePreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        brightnessRow.addView(brightnessBar, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
        root.addView(brightnessRow)

        preview = View(this).apply { background = circle(selectedColor, stroke = false) }
        root.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(28)).apply { topMargin = dp(8) })

        if (t != null) {
            nameInput.setText(t.name)
            hostInput.setText(t.host)
            if (t.port > 0) portInput.setText(t.port.toString())
            val pos = Transport.values().indexOfFirst { it == t.transport }
            if (pos >= 0) transportSpinner.setSelection(pos)
            selectedHue = hueOf(t.color)
            selectedBrightness = brightnessOf(t.color)
            spectrum.hue = selectedHue
            brightnessBar.progress = (selectedBrightness * 100).toInt()
            updatePreview()
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

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun onSave() {
        val host = hostInput.text.toString().trim()
        if (host.isEmpty()) {
            Toast.makeText(this, "Host is required", Toast.LENGTH_SHORT).show()
            return
        }
        val port = portInput.text.toString().trim().toIntOrNull() ?: 0
        val transport = Transport.values()[transportSpinner.selectedItemPosition]
        val result = IntentResult(host, nameInput.text.toString().trim(), port,
            transport, selectedColor, editId)
        setResult(Activity.RESULT_OK, result.toIntent())
        finish()
    }

    private fun updatePreview() {
        preview.background = circle(selectedColor, stroke = false)
        spectrum.invalidate()
    }

    private fun circle(color: Int, stroke: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(8).toFloat()
        if (stroke) setStroke(dp(3), Color.WHITE)
    }

    private fun label(s: String): TextView = TextView(this).apply {
        text = s
        textSize = 14f
        setTextColor(resolveColorAttr(android.R.attr.textColorSecondary))
        setPadding(0, dp(10), 0, dp(2))
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

    private fun resolveColorAttr(attr: Int): Int {
        val a = android.util.TypedValue()
        theme.resolveAttribute(attr, a, true)
        return a.data
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun hueOf(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0]
    }

    private fun brightnessOf(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[2]
    }

    /**
     * A horizontal hue spectrum (rainbow gradient). Tap anywhere to pick a hue.
     * A white tick shows the current selection.
     */
    class SpectrumView(context: android.content.Context) : View(context) {
        var hue: Float = 219f
            set(value) { field = value; invalidate() }
        var onHueChanged: ((Float) -> Unit)? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = context.resources.displayMetrics.density * 3f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val stops = 13
            val colors = IntArray(stops) { i ->
                Color.HSVToColor(floatArrayOf(i * 360f / (stops - 1), 1f, 1f))
            }
            paint.shader = LinearGradient(0f, 0f, w, 0f, colors, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            // Selection tick.
            val x = (hue / 360f) * w
            canvas.drawLine(x, h * 0.15f, x, h * 0.85f, tickPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val w = width.coerceAtLeast(1).toFloat()
                    val f = (event.x / w).coerceIn(0f, 1f)
                    hue = f * 360f
                    onHueChanged?.invoke(hue)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
