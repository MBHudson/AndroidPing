package com.pingtrace.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pingtrace.app.pinger.PingResult

private val LineColor = Color(0xFF4CAF50)
private val GapColor = Color(0xFFE53935)
private val GridColor = Color(0x22777777)

/**
 * A lightweight, dependency-free live line chart rendered on a [Canvas].
 *
 * Each [PingResult] is a point on the x-axis (evenly spaced by sample index).
 * The y-value is the measured rtt in ms. Successful pings are connected by a
 * line and drawn as dots; failed/unreachable pings are drawn as small red dots,
 * and the connecting line is broken across them (i.e. the failed sample forms a
 * visual gap, never a crash). Auto-scales to the max observed rtt.
 */
@Composable
fun PingChart(
    samples: List<PingResult>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(260.dp)) {
        if (samples.isEmpty()) return@Canvas

        val rtts = samples.map { it.rttMs?.toFloat() }
        val valid = rtts.filterNotNull()
        if (valid.isEmpty()) return@Canvas

        val yMax = ((valid.maxOrNull() ?: 1f) * 1.15f).coerceAtLeast(1f)
        val n = samples.size
        val stepX = if (n > 1) size.width / (n - 1) else size.width

        fun yFor(v: Float): Float = size.height - (v / yMax) * (size.height * 0.88f) - (size.height * 0.06f)

        // Horizontal grid line at the bottom.
        drawLine(
            color = GridColor,
            start = Offset(0f, yFor(0f)),
            end = Offset(size.width, yFor(0f)),
            strokeWidth = 1.dp.toPx(),
        )

        // Connecting line between consecutive successful samples (breaks at gaps).
        val path = Path()
        for (i in 0 until n - 1) {
            val a = rtts[i]
            val b = rtts[i + 1]
            if (a != null && b != null) {
                path.moveTo(i * stepX, yFor(a))
                path.lineTo((i + 1) * stepX, yFor(b))
            }
        }
        drawPath(path, color = LineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // Individual sample points; red for failures placed mid-height.
        rtts.forEachIndexed { i, v ->
            val x = i * stepX
            if (v != null) {
                drawCircle(LineColor, radius = 3.dp.toPx(), center = Offset(x, yFor(v)))
            } else {
                drawCircle(GapColor, radius = 3.dp.toPx(), center = Offset(x, size.height * 0.5f))
            }
        }
    }
}
