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
    private const val PREFS = "power"

    fun pauseTuningFor(ctx: Context, minutes: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("paused_until", System.currentTimeMillis() + minutes * 60_000).apply()
    }

    fun isPaused(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("paused_until", 0) > System.currentTimeMillis()

    /** Light check: every ~30 min, any network/battery state, tiny work. */
    fun scheduleLightCheck(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<TuneWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("light_check", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Deep clean: only while charging and the phone is idle, so it never causes lag or drain. */
    fun scheduleDeepClean(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<DeepCleanWorker>(1, TimeUnit.DAYS)
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
