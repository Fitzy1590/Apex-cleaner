package com.fitzy.apexcleaner.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Quiet health check. Posts a 1-tap card only when something really needs attention. */
class TuneWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (PowerPolicy.isPaused(applicationContext) || PowerPolicy.chargeHold(applicationContext)) return Result.success()
        val health = HealthMonitor(applicationContext).read()
        if (health.needsAttention) Notifier.postTip(applicationContext, health)
        return Result.success()
    }
}

/** Full safe clean, run only while charging and idle. */
class DeepCleanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // Fast-charge care is on: wait so all power goes to the battery, then try again later.
        if (PowerPolicy.chargeHold(applicationContext)) return Result.retry()
        val b = applicationContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val pct = b?.let { it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) } ?: 0
        val plugged = (b?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val idleMin = if (PowerPolicy.screenOn || PowerPolicy.screenOffSince == 0L) 0L
            else (System.currentTimeMillis() - PowerPolicy.screenOffSince) / 60_000
        if (!NightlyRules.canRun(plugged, pct, PowerPolicy.screenOn, idleMin)) return Result.retry()
        SafeCleaner(applicationContext).clean()
        return Result.success()
    }
}
