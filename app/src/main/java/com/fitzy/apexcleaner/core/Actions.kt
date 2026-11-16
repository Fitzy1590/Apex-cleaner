package com.fitzy.apexcleaner.core

import android.app.usage.UsageStatsManager
import android.content.Context

/** The four one-tap actions, shared by the buttons in the app and in notifications. */
object Actions {

    /** Boost = tidy our own cache and report free memory. It never stops other apps. */
    fun boost(ctx: Context): String {
        val report = SafeCleaner(ctx).clean()
        val h = HealthMonitor(ctx).read()
        return "Boost done: freed ${formatBytes(report.totalBytes)}. Memory free: ${h.ramFreePct}%."
    }

    fun clearCache(ctx: Context): String {
        val report = SafeCleaner(ctx).clean()
        val others = OtherAppCaches.clear(ctx)
        return "Cleared ${formatBytes(report.totalBytes + others)} of cache. Your data was not touched."
    }

    /** Cool down = pause our own background work and cancel heavy scheduled jobs for a while. */
    fun coolDown(ctx: Context): String {
        PowerPolicy.pauseTuningFor(ctx, 30)
        val h = HealthMonitor(ctx).read()
        return "Cooling down: background tuning paused for 30 min. Battery is ${"%.0f".format(h.batteryTempC)}°C."
    }

    /** Whitelists the app we most recently noticed using resources (needs Usage access), else opens nothing. */
    fun whitelistLast(ctx: Context): String {
        val store = WhitelistStore(ctx)
        val pkg = store.lastFlagged ?: recentApp(ctx)
        return if (pkg == null) {
            "Open Apex Cleaner > Protected apps to choose which apps to protect."
        } else {
            store.add(pkg)
            "Protected $pkg. It will never be interrupted."
        }
    }

    private fun recentApp(ctx: Context): String? = runCatching {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3_600_000, now)
            ?.filter { it.packageName != ctx.packageName }
            ?.maxByOrNull { it.lastTimeUsed }?.packageName
    }.getOrNull()
}
