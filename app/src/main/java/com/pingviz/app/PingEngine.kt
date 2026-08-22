package com.pingviz.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Background worker that pings all configured targets at the configured
 * interval and feeds results into per-target [Series] buffers for the chart.
 * Also sounds an audible alert when a target becomes unreachable and the
 * alarm is enabled.
 */
class PingEngine(private val context: Context) {

    val series = ConcurrentHashMap<String, Series>()

    @Volatile var intervalMs: Long = 1000L
    @Volatile var alarmEnabled: Boolean = false
    @Volatile var targets: List<Target> = emptyList()

    /** Called on the main thread after each ping cycle produces new data. */
    @Volatile var onDataChanged: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var stop = false
    @Volatile private var alive = false
    @Volatile private var toneGen: ToneGenerator? = null

    fun start() {
        if (alive) return
        alive = true
        stop = false
        rebuildSeries()

        thread(name = "ping-engine", isDaemon = true) {
            while (!stop) {
                val tickStart = System.currentTimeMillis()
                val snapshot = targets.filter { it.host.isNotBlank() }
                val workers = snapshot.map { t ->
                    thread(name = "ping-worker", isDaemon = true) {
                        val timeout = ((intervalMs - 50).toInt()).coerceAtLeast(300)
                        val rtt = Pinger.ping(t, timeout)
                        val s = series.getOrPut(t.id) { Series(t.id) }
                        s.color = t.color
                        s.label = t.displayName()
                        s.add(rtt.toFloat())
                        if (rtt < 0L) onFail(t)
                    }
                }
                workers.forEach { it.join() }
                main.post { onDataChanged?.invoke() }

                val elapsed = System.currentTimeMillis() - tickStart
                val sleep = (intervalMs - elapsed).coerceAtLeast(100L)
                Thread.sleep(sleep)
            }
            alive = false
        }
    }

    fun updateTargets(newTargets: List<Target>) {
        targets = newTargets
        rebuildSeries()
    }

    fun clearData() {
        series.values.forEach { it.clear() }
        main.post { onDataChanged?.invoke() }
    }

    fun stop() {
        stop = true
        try { toneGen?.release() } catch (_: Exception) {}
        toneGen = null
    }

    private fun rebuildSeries() {
        val ids = targets.map { it.id }.toSet()
        for (t in targets) series.getOrPut(t.id) { Series(t.id).apply { color = t.color; label = t.displayName() } }
        series.keys.removeAll { it !in ids }
    }

    private fun onFail(t: Target) {
        if (!alarmEnabled) return
        try {
            main.post {
                try {
                    val gen = toneGen ?: ToneGenerator(AudioManager.STREAM_ALARM, 90)
                        .also { toneGen = it }
                    gen.startTone(ToneGenerator.TONE_PROP_BEEP, 400)
                } catch (_: Exception) {
                    // audio not available
                }
            }
        } catch (_: Exception) {
        }
    }
}
