package com.fitzy.apexcleaner.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fitzy.apexcleaner.MainActivity
import com.fitzy.apexcleaner.R
import com.fitzy.apexcleaner.net.AdBlock
import com.fitzy.apexcleaner.net.ApexVpnService
import com.fitzy.apexcleaner.net.ShieldStats

const val ACTION_ADS_PAUSE = "com.fitzy.apexcleaner.ADS_PAUSE"
const val ACTION_ADS_RESUME = "com.fitzy.apexcleaner.ADS_RESUME"
const val ACTION_ADS_OFF = "com.fitzy.apexcleaner.ADS_OFF"
const val ACTION_ADS_ON = "com.fitzy.apexcleaner.ADS_ON"
const val ACTION_BOOST = "com.fitzy.apexcleaner.BOOST"
const val ACTION_CLEAR_CACHE = "com.fitzy.apexcleaner.CLEAR_CACHE"
const val ACTION_COOL_DOWN = "com.fitzy.apexcleaner.COOL_DOWN"
const val ACTION_WHITELIST = "com.fitzy.apexcleaner.WHITELIST"

object Notifier {
    const val CHANNEL_SERVICE = "service"
    const val CHANNEL_TIPS = "tips"
    const val ID_SERVICE = 1
    const val ID_TIP = 2

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Background care", NotificationManager.IMPORTANCE_MIN),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TIPS, "Quick fixes", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun action(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, ActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @Volatile private var lastKey = ""

    /** Always-on card: how many ads were blocked today, with Pause 15 min / Turn off (or Resume / Turn on). */
    fun serviceNotification(ctx: Context): Notification {
        ensureChannels(ctx)
        val now = System.currentTimeMillis()
        AdBlock.ensureLoaded(ctx)
        val count = AdBlock.blockedToday(ctx, now)
        val on = ShieldStats.running.value
        val paused = on && AdBlock.isPaused(now)
        val text = when {
            !on -> "Ad blocking is off"
            paused -> "Paused until " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(AdBlock.pausedUntil))
            else -> "Ad blocking is on"
        }
        val b = Notification.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setContentTitle(if (count == 1) "1 ad blocked today" else "$count ads blocked today")
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
        if (!on) {
            b.addAction(Notification.Action.Builder(null, "Turn on", action(ctx, ACTION_ADS_ON, 14)).build())
        } else {
            if (paused) b.addAction(Notification.Action.Builder(null, "Resume", action(ctx, ACTION_ADS_RESUME, 15)).build())
            else b.addAction(Notification.Action.Builder(null, "Pause ${AdBlock.PAUSE_MINUTES} min", action(ctx, ACTION_ADS_PAUSE, 16)).build())
            b.addAction(Notification.Action.Builder(null, "Turn off", action(ctx, ACTION_ADS_OFF, 17)).build())
        }
        lastKey = "$on|$paused|$count|${AdBlock.pausedUntil}"
        return b.build()
    }

    /** Updates the always-on card, only when something on it changed (or when forced). Does nothing if it is not showing. */
    fun refresh(ctx: Context, force: Boolean = false) {
        if (!TunerService.running) return
        val c = ctx.applicationContext
        val now = System.currentTimeMillis()
        val on = ShieldStats.running.value
        val key = "$on|${on && AdBlock.isPaused(now)}|${AdBlock.blockedToday(c, now)}|${AdBlock.pausedUntil}"
        if (!force && key == lastKey) return
        c.getSystemService(NotificationManager::class.java).notify(ID_SERVICE, serviceNotification(c))
    }

    /** Actionable card: fix issues straight from the notification shade. */
    fun postTip(ctx: Context, health: Health) {
        ensureChannels(ctx)
        val text = buildString {
            if (health.lowRam) append("Memory is running low. ")
            if (health.storageFreePct < 15) append("Storage is nearly full (${health.storageFreePct}% free). ")
            if (health.thermalHot) append("Your device feels warm. ")
        }.trim().ifEmpty { "Your device could use a tune-up." }
        val n = Notification.Builder(ctx, CHANNEL_TIPS)
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setContentTitle("Quick fix available")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "1-Tap Boost", action(ctx, ACTION_BOOST, 10)).build())
            .addAction(Notification.Action.Builder(null, "Clear Hidden Cache", action(ctx, ACTION_CLEAR_CACHE, 11)).build())
            .addAction(Notification.Action.Builder(null, "Cool Down Device", action(ctx, ACTION_COOL_DOWN, 12)).build())
            .addAction(Notification.Action.Builder(null, "Whitelist App", action(ctx, ACTION_WHITELIST, 13)).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_TIP, n)
    }

    fun postResult(ctx: Context, message: String) {
        ensureChannels(ctx)
        val n = Notification.Builder(ctx, CHANNEL_TIPS)
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setContentTitle("Apex Cleaner")
            .setContentText(message)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_TIP, n)
    }
}

/** Handles the notification buttons. None of these ever kills another app. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val msg = when (intent.action) {
                    ACTION_BOOST -> Actions.boost(context)
                    ACTION_CLEAR_CACHE -> Actions.clearCache(context)
                    ACTION_COOL_DOWN -> Actions.coolDown(context)
                    ACTION_WHITELIST -> Actions.whitelistLast(context)
                    ACTION_ADS_PAUSE -> { AdBlock.pause(context); Notifier.refresh(context, force = true); null }
                    ACTION_ADS_RESUME -> { AdBlock.resume(context); Notifier.refresh(context, force = true); null }
                    ACTION_ADS_OFF -> { AdBlock.resume(context); ApexVpnService.stop(context); null }
                    ACTION_ADS_ON -> {
                        AdBlock.resume(context)
                        // Android needs the one-time VPN permission given inside the app first.
                        if (android.net.VpnService.prepare(context) == null) { ApexVpnService.start(context); null }
                        else "Open Apex Cleaner once to allow ad blocking"
                    }
                    else -> null
                }
                if (msg != null) Notifier.postResult(context, msg)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
