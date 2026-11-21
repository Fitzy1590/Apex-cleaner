package com.fitzy.apexshield.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fitzy.apexshield.MainActivity
import com.fitzy.apexshield.R
import com.fitzy.apexshield.vpn.ShieldEngine
import com.fitzy.apexshield.vpn.ShieldSettings
import com.fitzy.apexshield.vpn.ShieldVpnService

const val ACTION_BOOST = "com.fitzy.apexshield.BOOST"
const val ACTION_CLEAR_CACHE = "com.fitzy.apexshield.CLEAR_CACHE"
const val ACTION_COOL_DOWN = "com.fitzy.apexshield.COOL_DOWN"
const val ACTION_WHITELIST = "com.fitzy.apexshield.WHITELIST"
const val ACTION_SHIELD_ON = "com.fitzy.apexshield.SHIELD_ON"
const val ACTION_SHIELD_OFF = "com.fitzy.apexshield.SHIELD_OFF"
const val ACTION_SHIELD_PAUSE = "com.fitzy.apexshield.SHIELD_PAUSE_15"

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

    fun serviceNotification(ctx: Context): Notification {
        ensureChannels(ctx)
        val st = ShieldSettings(ctx)
        val running = ShieldEngine.ui.value.running
        val paused = !running && st.pausedUntil > System.currentTimeMillis()
        val title = when {
            running -> "Apex Shield is ON"
            paused -> "Apex Shield is paused"
            else -> "Apex Shield is OFF"
        }
        val text = when {
            running || paused -> "${st.blockedToday} ads & trackers blocked today"
            else -> "Tap Turn on to block ads again"
        }
        val b = Notification.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (running) {
            b.addAction(Notification.Action.Builder(null, "Pause 15 min", action(ctx, ACTION_SHIELD_PAUSE, 20)).build())
            b.addAction(Notification.Action.Builder(null, "Turn off", action(ctx, ACTION_SHIELD_OFF, 21)).build())
        } else {
            b.addAction(Notification.Action.Builder(null, "Turn on", action(ctx, ACTION_SHIELD_ON, 22)).build())
        }
        return b.build()
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
        when (intent.action) {
            ACTION_SHIELD_OFF -> { ShieldVpnService.stop(context); return }
            ACTION_SHIELD_PAUSE -> { ShieldVpnService.pause(context); return }
            ACTION_SHIELD_ON -> {
                // Android asks for VPN permission once; if it is missing, open the app so it can ask.
                if (android.net.VpnService.prepare(context) == null) ShieldVpnService.start(context)
                else context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        val pending = goAsync()
        Thread {
            try {
                val msg = when (intent.action) {
                    ACTION_BOOST -> Actions.boost(context)
                    ACTION_CLEAR_CACHE -> Actions.clearCache(context)
                    ACTION_COOL_DOWN -> Actions.coolDown(context)
                    ACTION_WHITELIST -> Actions.whitelistLast(context)
                    else -> null
                }
                if (msg != null) Notifier.postResult(context, msg)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
