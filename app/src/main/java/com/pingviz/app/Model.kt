package com.pingviz.app

import java.util.UUID

/** How a target is probed. */
enum class Transport(val label: String) {
    ICMP("ICMP"),
    HTTP("HTTP"),
    HTTPS("HTTPS")
}

/** A single monitored target. */
data class Target(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var host: String = "",
    var port: Int = 0,               // 0 = use default for transport
    var transport: Transport = Transport.ICMP,
    var color: Int = 0xFF2196F3.toInt()
) {
    fun effectivePort(): Int = when {
        port > 0 -> port
        transport == Transport.HTTPS -> 443
        transport == Transport.HTTP -> 80
        else -> 0
    }

    fun displayName(): String = if (name.isBlank()) host else name

    fun transportDetail(): String {
        val base = transport.label
        val p = effectivePort()
        return if (p > 0) "$base :$p" else base
    }
}

/** Ring buffer of ping samples for one target, used by the live chart. */
class Series(val targetId: String) {
    @Volatile var color: Int = 0xFF2196F3.toInt()
    @Volatile var label: String = ""
    private val times = ArrayDeque<Long>()
    private val values = ArrayDeque<Float>()
    private val lock = Any()
    private val capacity = 200

    fun add(rtt: Float) {
        synchronized(lock) {
            times.addLast(System.currentTimeMillis())
            values.addLast(rtt)
            while (times.size > capacity) {
                times.removeFirst()
                values.removeFirst()
            }
        }
    }

    fun points(): Pair<List<Long>, List<Float>> =
        synchronized(lock) { times.toList() to values.toList() }

    fun clear() = synchronized(lock) { times.clear(); values.clear() }
}

/** User-configurable settings. themeMode uses AppCompatDelegate.MODE_NIGHT_* ints. */
data class AppSettings(
    var pingIntervalSeconds: Int = 1,
    var themeMode: Int = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    var alarmEnabled: Boolean = false
)
