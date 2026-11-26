package com.fitzy.apexcleaner.core

import android.content.Context

/** Pure rules for Instant Mode: only the three animation speeds can ever be changed. */
object InstantModeRules {
    val ANIM_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    fun isAnimKey(key: String) = key in ANIM_KEYS
    /** A plain number from 0 to 10, nothing else (stops shell tricks). */
    fun isValidScale(v: String) = Regex("""\d{1,2}(\.\d{1,2})?""").matches(v) && v.toFloat() <= 10f
    /** Old values are kept as they were; a missing or "null" value means Android's normal 1.0. */
    fun restoreValue(saved: String?) = if (saved != null && isValidScale(saved)) saved else "1.0"
}

/**
 * Instant Mode: phone animations off so apps open with no slide or fade. Needs the Shizuku helper.
 * Remembers the old speeds and puts them back when switched off. Clocks and alarms are not touched.
 */
object InstantMode {
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("instant_mode", Context.MODE_PRIVATE)

    fun isOn(ctx: Context) = AppSettings.instantMode(ctx)

    /** Returns true if it worked. Call off the main thread (talks to the helper). */
    fun set(ctx: Context, on: Boolean): Boolean {
        val ok = ShizukuBridge.withService(ctx) { svc ->
            if (on) {
                val saved = p(ctx).edit()
                for (k in InstantModeRules.ANIM_KEYS) {
                    if (!p(ctx).contains(k)) saved.putString(k, svc.getAnimScale(k))
                }
                saved.apply()
                InstantModeRules.ANIM_KEYS.all { svc.setAnimScale(it, "0") }
            } else {
                InstantModeRules.ANIM_KEYS.all { svc.setAnimScale(it, InstantModeRules.restoreValue(p(ctx).getString(it, null))) }
                    .also { if (it) p(ctx).edit().clear().apply() }
            }
        } ?: false
        if (ok) AppSettings.setInstantMode(ctx, on)
        return ok
    }
}
