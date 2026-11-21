package com.fitzy.apexcleaner.net

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Counts per calendar day: starts again from zero when the date changes. Pure, so it is unit-tested. */
class DailyCount(var day: String = "", var count: Int = 0) {
    @Synchronized fun add(today: String): Int { roll(today); return ++count }
    @Synchronized fun get(today: String): Int { roll(today); return count }
    private fun roll(today: String) { if (day != today) { day = today; count = 0 } }
}

/** Ads blocked today plus the "pause for 15 minutes" state. Saved privately on the device. */
object AdBlock {
    const val PAUSE_MINUTES = 15
    private const val PREFS = "ad_block"
    private val daily = DailyCount()
    @Volatile private var loaded = false
    @Volatile var pausedUntil = 0L
        private set
    private var sinceSave = 0

    private fun key(now: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

    @Synchronized fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        daily.day = p.getString("day", "").orEmpty()
        daily.count = p.getInt("count", 0)
        pausedUntil = p.getLong("paused_until", 0L)
    }

    fun recordBlocked(ctx: Context, now: Long = System.currentTimeMillis()) {
        ensureLoaded(ctx)
        daily.add(key(now))
        if (++sinceSave >= 10) save(ctx)
    }

    fun blockedToday(ctx: Context, now: Long = System.currentTimeMillis()): Int {
        ensureLoaded(ctx)
        return daily.get(key(now))
    }

    fun isPaused(now: Long = System.currentTimeMillis()) = now < pausedUntil

    fun pause(ctx: Context, now: Long = System.currentTimeMillis()) {
        ensureLoaded(ctx)
        pausedUntil = now + PAUSE_MINUTES * 60_000L
        save(ctx)
    }

    fun resume(ctx: Context) {
        ensureLoaded(ctx)
        pausedUntil = 0L
        save(ctx)
    }

    @Synchronized fun save(ctx: Context) {
        sinceSave = 0
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("day", daily.day).putInt("count", daily.count).putLong("paused_until", pausedUntil).apply()
    }
}
