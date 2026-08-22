package com.pingviz.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Hand-rolled live line graph. Draws one colored line per series over a
 * rolling time window (default ~90s). Any sample equal to -1 (unreachable)
 * breaks the line, leaving a gap.
 */
class LiveChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var dataSource: (() -> List<Series>)? = null

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = -0x44DDDDDE; strokeWidth = 1.5f; style = Paint.Style.STROKE
    }
    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = -0x61616162; textSize = 26f
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = -0x61616162; textSize = 34f; textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private val windowMs = 90_000L

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val list = dataSource?.invoke() ?: return
        if (list.isEmpty()) {
            canvas.drawText("No targets yet. Press + to add one.", width / 2f, height / 2f, emptyPaint)
            return
        }

        val padLeft = dp(52f)
        val padBottom = dp(24f)
        val padTop = dp(56f)
        val padRight = dp(10f)
        val chartW = (width - padLeft - padRight).coerceAtLeast(1f)
        val chartH = (height - padTop - padBottom).coerceAtLeast(1f)

        // Time window anchored to the newest data point.
        var now = System.currentTimeMillis()
        for (s in list) {
            val (times, _) = s.points()
            if (times.isNotEmpty()) now = max(now, times.last())
        }
        val x0 = now - windowMs

        // Vertical scale from the largest recent RTT.
        var maxV = 50f
        for (s in list) {
            val (_, vals) = s.points()
            for (v in vals) if (v > maxV) maxV = v
        }
        val niceMax = niceMax(maxV)

        drawGrid(canvas, padLeft, padTop, chartW, chartH, niceMax, x0, now)

        for (s in list) {
            val (times, vals) = s.points()
            if (times.isEmpty()) continue
            linePaint.color = s.color
            val path = Path()
            var started = false
            for (i in times.indices) {
                val t = times[i]
                val v = vals[i]
                if (v < 0f || t < x0) { started = false; continue }
                val x = padLeft + ((t - x0).toFloat() / windowMs) * chartW
                val y = yFor(v, niceMax, padTop, chartH)
                val cx = x.coerceIn(padLeft, padLeft + chartW)
                val cy = y.coerceIn(padTop, padTop + chartH)
                if (!started) { path.moveTo(cx, cy); started = true }
                else path.lineTo(cx, cy)
            }
            canvas.drawPath(path, linePaint)

            // Legend label next to the latest point.
            val last = times.last()
            val lastV = vals.last()
            if (lastV >= 0f) {
                val x = padLeft + ((last - x0).toFloat() / windowMs) * chartW
                val y = yFor(lastV, niceMax, padTop, chartH)
                labelPaint.color = s.color
                val lx = x.coerceIn(padLeft + 2f, padLeft + chartW - 220f)
                val ly = (y - dp(10f)).coerceAtLeast(padTop + dp(12f))
                canvas.drawText("${s.label}  ${lastV.toInt()}ms", lx, ly, labelPaint)
            }
        }
    }

    private fun drawGrid(canvas: Canvas, left: Float, top: Float, w: Float, h: Float,
                         niceMax: Float, x0: Long, now: Long) {
        val steps = 4
        for (i in 0..steps) {
            val v = niceMax * i / steps
            val y = top + h - (v / niceMax) * h
            canvas.drawLine(left, y, left + w, y, gridPaint)
            axisTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${v.toInt()}ms", left - dp(6f), y + dp(8f), axisTextPaint)
        }
        // Vertical separators every 15s.
        var t = x0 + (15_000L - (x0 % 15_000L)) % 15_000L
        while (t <= now) {
            val x = left + ((t - x0).toFloat() / windowMs) * w
            canvas.drawLine(x, top, x, top + h, gridPaint)
            t += 15_000L
        }
    }

    private fun yFor(v: Float, maxV: Float, top: Float, h: Float): Float =
        top + h - (v / maxV) * h

    private fun niceMax(v: Float): Float {
        val bands = listOf(50f, 100f, 150f, 200f, 300f, 500f, 750f, 1000f,
            1500f, 2000f, 3000f, 5000f, 10_000f, 30_000f)
        for (b in bands) if (v <= b) return b
        return bands.last()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
