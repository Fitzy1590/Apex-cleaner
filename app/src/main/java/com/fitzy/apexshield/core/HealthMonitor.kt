package com.fitzy.apexshield.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs

data class Health(
    val ramFreePct: Int,
    val storageFreePct: Int,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val batteryPct: Int,
    val batteryTempC: Float,
    val thermalHot: Boolean,
    val lowRam: Boolean,
) {
    val needsAttention: Boolean get() = lowRam || storageFreePct < 15 || thermalHot
}

/** Read-only look at how the phone is doing. Light enough to run often. */
class HealthMonitor(private val context: Context) {
    fun read(): Health {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val ramFree = if (mi.totalMem > 0) (mi.availMem * 100 / mi.totalMem).toInt() else 0

        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val storagePct = if (total > 0) (free * 100 / total).toInt() else 0

        val batt = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batt?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (batt?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val hot = temp >= 40f ||
            (android.os.Build.VERSION.SDK_INT >= 29 &&
                pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE)

        return Health(
            ramFreePct = ramFree,
            storageFreePct = storagePct,
            storageFreeBytes = free,
            storageTotalBytes = total,
            batteryPct = if (level >= 0) level * 100 / scale else 0,
            batteryTempC = temp,
            thermalHot = hot,
            lowRam = mi.lowMemory || ramFree < 12,
        )
    }
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(b / (1L shl 10).toDouble())
    else -> "$b B"
}
