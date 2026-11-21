package com.fitzy.apexcleaner.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Battery-friendly rules for this app's own work. Nothing here stops other apps.
 *  - Screen off: our own background activity is held back and batched together.
 *  - Heavy cleaning waits for charging + idle (Doze-aligned) windows.
 */
object PowerPolicy {
    @Volatile var screenOn: Boolean = true
    @Volatile var screenOffSince: Long = 0L
    private const val PREFS = "power"

    fun pauseTuningFor(ctx: Context, minutes: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("paused_until", System.currentTimeMillis() + minutes * 60_000).apply()
    }

    fun isPaused(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("paused_until", 0) > System.currentTimeMillis()

    /** While fast-charge care is on, our own background work waits so all power goes to the battery. */
    fun setChargeHold(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("charge_hold", on).apply()
    }

    /** True only while a charger is really plugged in and fast-charge care is on. Self-heals if we missed an unplug. */
    fun chargeHold(ctx: Context): Boolean {
        if (!ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("charge_hold", false)) return false
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (b?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    /** Light check: every ~30 min, any network/battery state, tiny work. */
    fun scheduleLightCheck(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<TuneWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("light_check", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancelDeepClean(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork("deep_clean") }

    /** Deep clean: only while charging and the phone is idle, so it never causes lag or drain. */
    fun scheduleDeepClean(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<DeepCleanWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(NightlyRules.delayToNextThreeAm(java.util.Calendar.getInstance()), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(true)
                    .setRequiresDeviceIdle(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("deep_clean", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}


/** Tracks screen on/off so the tuner can go quiet when the screen is off. */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PowerPolicy.screenOn = intent.action != Intent.ACTION_SCREEN_OFF
        PowerPolicy.screenOffSince = if (PowerPolicy.screenOn) 0L else System.currentTimeMillis()
    }

    companion object {
        fun register(ctx: Context, r: ScreenStateReceiver) {
            ctx.registerReceiver(r, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            })
        }
    }
}
