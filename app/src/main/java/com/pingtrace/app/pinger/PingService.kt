package com.pingtrace.app.pinger

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Result of a single ping attempt.
 *
 * @property index monotonically increasing sample counter (used as the x-axis).
 * @property timestampMs wall-clock time the sample was produced (ms).
 * @property rttMs round-trip time in milliseconds, or `null` when the host was
 *   unreachable / timed out.
 * @property unreachable whether the host could not be reached on this attempt.
 */
data class PingResult(
    val index: Long,
    val timestampMs: Long,
    val rttMs: Long?,
    val unreachable: Boolean,
) {
    val succeeded: Boolean get() = rttMs != null
}

/**
 * Lightweight reachability + round-trip-time measurement.
 *
 * A real raw ICMP echo on Android requires root, so this uses a TCP connect to
 * a port that is expected to respond (443, then 80) and measures the wall-clock
 * duration of the connect handshake. If no TCP port connects, it falls back to a
 * short-timeout [InetAddress.isReachable] probe. Either way it returns a
 * [PingResult] with rtt measured in milliseconds, and a `null` rtt + `true`
 * unreachable flag when the host did not answer.
 *
 * This is a blocking call — always invoke it on a background dispatcher
 * (the pinger loop runs it on [kotlinx.coroutines.Dispatchers.IO]).
 */
object PingService {

    /** Per-attempt timeout for connect and reachability probes (ms). */
    private const val TIMEOUT_MS = 2_000L

    /** Ports to try, in order, for the TCP-connect reachability probe. */
    private val PROBE_PORTS = intArrayOf(443, 80)

    fun ping(host: String, counter: Long): PingResult {
        val start = System.currentTimeMillis()
        return try {
            val resolved = InetAddress.getByName(host)
            val rtt = tcpConnectRtt(resolved)
            if (rtt != null) {
                PingResult(counter, System.currentTimeMillis(), rtt, unreachable = false)
            } else {
                val reachable = try {
                    resolved.isReachable(TIMEOUT_MS.toInt())
                } catch (e: Exception) {
                    false
                }
                if (reachable) {
                    PingResult(counter, System.currentTimeMillis(), System.currentTimeMillis() - start, unreachable = false)
                } else {
                    PingResult(counter, System.currentTimeMillis(), null, unreachable = true)
                }
            }
        } catch (e: Exception) {
            // DNS resolution failures, malformed host, etc. — not a reachable target.
            PingResult(counter, System.currentTimeMillis(), null, unreachable = true)
        }
    }

    /** Returns the RTT of a successful TCP connect, or null if none of the probe ports answered. */
    private fun tcpConnectRtt(address: InetAddress): Long? {
        for (port in PROBE_PORTS) {
            val t0 = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), TIMEOUT_MS.toInt())
                    return System.currentTimeMillis() - t0
                }
            } catch (e: Exception) {
                // Try the next port.
            }
        }
        return null
    }
}
