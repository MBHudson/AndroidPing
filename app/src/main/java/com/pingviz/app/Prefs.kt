package com.pingviz.app

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject

/** Persists targets and settings in SharedPreferences (JSON for targets). */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("pingviz_prefs", Context.MODE_PRIVATE)

    fun saveTargets(list: List<Target>) {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("name", t.name)
            o.put("host", t.host)
            o.put("port", t.port)
            o.put("transport", t.transport.name)
            o.put("color", t.color)
            arr.put(o)
        }
        sp.edit().putString("targets", arr.toString()).apply()
    }

    fun loadTargets(): MutableList<Target> {
        val out = mutableListOf<Target>()
        val raw = sp.getString("targets", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Target(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        name = o.optString("name", ""),
                        host = o.optString("host", ""),
                        port = o.optInt("port", 0),
                        transport = runCatching { Transport.valueOf(o.optString("transport", "ICMP")) }
                            .getOrDefault(Transport.ICMP),
                        color = o.optInt("color", 0xFF2196F3.toInt())
                    )
                )
            }
        } catch (_: Exception) {
            // corrupt JSON - start fresh
        }
        return out
    }

    fun saveSettings(s: AppSettings) {
        sp.edit()
            .putInt("interval", s.pingIntervalSeconds)
            .putInt("theme", s.themeMode)
            .putBoolean("alarm", s.alarmEnabled)
            .apply()
    }

    fun loadSettings(): AppSettings = AppSettings(
        pingIntervalSeconds = (sp.getInt("interval", 1)).coerceAtLeast(1),
        themeMode = sp.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        alarmEnabled = sp.getBoolean("alarm", false)
    )
}
