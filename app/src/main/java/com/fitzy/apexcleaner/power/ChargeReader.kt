package com.fitzy.apexcleaner.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** One reading of the battery. Current is already in microamps, always positive. */
data class ChargeSample(
    val level: Int,
    val plugged: Boolean,
    val full: Boolean,
    val tempC: Float,
    val voltageMv: Int,
    val currentUa: Long,
    val chargeCounterUa: Long,
    val thermalStatus: Int?,
)

/** Read-only battery telemetry. Light enough to call every few seconds while charging. */
class ChargeReader(private val context: Context) {
    fun read(): ChargeSample {
        val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val full = status == BatteryManager.BATTERY_STATUS_FULL || pct >= 100
        val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val volt = b?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val current = ChargeMath.normalizeMicroAmps(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
        val counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .let { if (it == Long.MIN_VALUE || it < 0) 0L else it }

        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        } else {
            null
        }
        return ChargeSample(pct, plugged, full, temp, volt, current, counter, thermal)
    }
}
