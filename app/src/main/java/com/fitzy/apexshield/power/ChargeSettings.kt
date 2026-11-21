package com.fitzy.apexshield.power

import android.content.Context

/** The owner's charging choices. Safe defaults: fast-charge care on, alert at 80%. */
class ChargeSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("charging", Context.MODE_PRIVATE)

    var limitPct: Int
        get() = ChargeMath.clampLimit(prefs.getInt("limit", ChargeMath.DEFAULT_LIMIT))
        set(v) = prefs.edit().putInt("limit", ChargeMath.clampLimit(v)).apply()

    var fastEnabled: Boolean
        get() = prefs.getBoolean("fast", true)
        set(v) = prefs.edit().putBoolean("fast", v).apply()
}
