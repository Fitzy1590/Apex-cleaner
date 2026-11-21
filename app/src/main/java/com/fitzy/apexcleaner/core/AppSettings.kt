package com.fitzy.apexcleaner.core

import android.content.Context

/** Simple on/off choices from the Settings page, kept on the device. */
object AppSettings {
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun nightClean(ctx: Context) = p(ctx).getBoolean("night_clean", true)
    fun setNightClean(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("night_clean", on).apply()

    fun encryptedDns(ctx: Context) = p(ctx).getBoolean("doh", true)
    fun setEncryptedDns(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("doh", on).apply()

    /** Streaming priority (speed limiter for other apps while a video, music or game app is on screen). On by default. */
    fun limiter(ctx: Context) = p(ctx).getBoolean("limiter", true)
    fun setLimiter(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("limiter", on).apply()
}
