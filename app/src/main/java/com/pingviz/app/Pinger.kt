package com.pingviz.app

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Performs a single round-trip probe against a target.
 *
 * ICMP on Android: raw ICMP_ECHO requires root/priveleged socket access that a
 * normal (non-root) app does not have, so we use [InetAddress.isReachable],
 * which performs a best-effort ICMP echo where the platform permits and falls
 * back to a TCP-connect probe otherwise. It is allowed on any normal device
 * and safely times out.
 *
 * HTTP/HTTPS: performs a real HTTP HEAD round trip and measures elapsed time.
 */
object Pinger {

    /**
     * Returns the round-trip time in milliseconds, or -1 if the target is
     * unreachable, times out, or an error occurs.
     */
    fun ping(t: Target, timeoutMs: Int): Long {
        return try {
            when (t.transport) {
                Transport.ICMP -> icmpPing(t, timeoutMs)
                Transport.HTTP, Transport.HTTPS -> httpPing(t, timeoutMs)
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun icmpPing(t: Target, timeoutMs: Int): Long {
        val host = t.host.trim()
        if (host.isEmpty()) return -1L
        val start = System.currentTimeMillis()
        val reachable = InetAddress.getByName(host).isReachable(timeoutMs)
        val elapsed = System.currentTimeMillis() - start
        return if (reachable) elapsed else -1L
    }

    private fun httpPing(t: Target, timeoutMs: Int): Long {
        val scheme = if (t.transport == Transport.HTTPS) "https" else "http"
        val host = t.host.trim()
        val port = t.effectivePort()
        if (host.isEmpty()) return -1L
        val url = URL("$scheme://$host:$port/")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "HEAD"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            val start = System.currentTimeMillis()
            conn.connect()
            val code = conn.responseCode        // forces the round trip
            val elapsed = System.currentTimeMillis() - start
            // Any HTTP status (even 4xx/5xx) means the host answered.
            return if (code != -1) elapsed else -1L
        } finally {
            conn.disconnect()
        }
    }
}
