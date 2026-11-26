package com.fitzy.apexcleaner.core

import android.content.Context

/** Simple on/off choices from the Settings page, kept on the device. */
object AppSettings {
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun nightClean(ctx: Context) = p(ctx).getBoolean("night_clean", true)
    fun setNightClean(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("night_clean", on).apply()

    fun encryptedDns(ctx: Context) = p(ctx).getBoolean("doh", true)
    fun setEncryptedDns(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("doh", on).apply()

    /** Blocked names answered "this site does not exist" (most ad kits then report "no ad" and collapse the slot). On by default. */
    fun nxdomainReply(ctx: Context) = p(ctx).getBoolean("nxdomain", true)
    fun setNxdomainReply(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("nxdomain", on).apply()

    /** Stops apps sidestepping the shield with their own private lookup service or a hard-wired public one (they fall back to the phone's lookup). On by default. */
    fun blockSidestep(ctx: Context) = p(ctx).getBoolean("block_sidestep", true)
    fun setBlockSidestep(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("block_sidestep", on).apply()

    /** Lets the small ad-blocker-detection scripts load so sites show the page instead of "please allow ads". On by default. */
    fun allowAdblockChecks(ctx: Context) = p(ctx).getBoolean("allow_adblock_checks", true)
    fun setAllowAdblockChecks(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("allow_adblock_checks", on).apply()

    /** Cover leftover empty ad boxes (needs the Accessibility service switched on). Off by default. */
    fun hideAdBoxes(ctx: Context) = p(ctx).getBoolean("hide_ad_boxes", false)
    fun setHideAdBoxes(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("hide_ad_boxes", on).apply()

    /** Close or black out "we noticed ads aren't being displayed" notices in web browsers (needs the Accessibility service). Off by default. */
    fun hideAdNotices(ctx: Context) = p(ctx).getBoolean("hide_ad_notices", false)
    fun setHideAdNotices(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("hide_ad_notices", on).apply()

    /** Streaming priority (speed limiter for other apps while a video, music or game app is on screen). On by default. */
    fun limiter(ctx: Context) = p(ctx).getBoolean("limiter", true)
    fun setLimiter(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("limiter", on).apply()

    /** Instant Mode (phone animations off via the Shizuku helper). Off by default. */
    fun instantMode(ctx: Context) = p(ctx).getBoolean("instant_mode", false)
    fun setInstantMode(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("instant_mode", on).apply()

    /** Web page cleaner for browsers (needs the local certificate installed in Android). Off by default. */
    fun httpsCleaner(ctx: Context) = p(ctx).getBoolean("https_cleaner", false)
    fun setHttpsCleaner(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("https_cleaner", on).apply()

    /** Soft chime when charging reaches the limit. Off by default. */
    fun chargeChime(ctx: Context) = p(ctx).getBoolean("charge_chime", false)
    fun setChargeChime(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("charge_chime", on).apply()
}
