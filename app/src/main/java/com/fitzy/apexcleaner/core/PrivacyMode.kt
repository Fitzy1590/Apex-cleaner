package com.fitzy.apexcleaner.core

import android.content.Context

/** Pure rules for Privacy Mode: only camera and microphone, nothing else. */
object PrivacyModeRules {
    val SENSORS = listOf("camera", "microphone")
    fun isSensor(s: String) = s in SENSORS
    /** Reads the phone's reply: "true" = blocked, "false" = allowed, anything else = unknown. */
    fun parseBlocked(out: String): Int = when (out.trim().lowercase()) { "true" -> 1; "false" -> 0; else -> -1 }
    /** Sensors Privacy Mode should block: only those that were still allowed. */
    fun toBlock(before: Map<String, Int>) = SENSORS.filter { before[it] == 0 }
}

/**
 * Privacy Mode: one tap blocks the camera and microphone for every app, using Android's own privacy switch
 * through the Shizuku helper. Switching off puts back only what Privacy Mode itself blocked.
 * Clocks and alarms do not use either sensor, so they are unaffected. Needs Android 12 or newer.
 */
object PrivacyMode {
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("privacy_mode", Context.MODE_PRIVATE)

    fun isOn(ctx: Context) = p(ctx).getBoolean("on", false)

    /** Returns true if every change was accepted. Call off the main thread. If a step fails, it is undone. */
    fun set(ctx: Context, on: Boolean): Boolean {
        val ok = ShizukuBridge.withService(ctx) { svc ->
            if (on) {
                val targets = PrivacyModeRules.toBlock(PrivacyModeRules.SENSORS.associateWith { svc.getSensorBlocked(it) })
                val done = mutableListOf<String>()
                for (t in targets) { if (svc.setSensorBlocked(t, true)) done += t else { done.forEach { svc.setSensorBlocked(it, false) }; return@withService false } }
                p(ctx).edit().putStringSet("mine", done.toSet()).apply()
                true
            } else {
                val mine = p(ctx).getStringSet("mine", emptySet()).orEmpty()
                mine.filter { PrivacyModeRules.isSensor(it) }.all { svc.setSensorBlocked(it, false) }
            }
        } ?: false
        if (ok) {
            p(ctx).edit().putBoolean("on", on).apply()
            if (!on) p(ctx).edit().remove("mine").apply()
        }
        return ok
    }
}
