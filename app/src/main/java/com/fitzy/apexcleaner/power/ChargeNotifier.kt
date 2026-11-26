package com.fitzy.apexcleaner.power

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fitzy.apexcleaner.MainActivity
import com.fitzy.apexcleaner.R

/** Lock-screen charging card and the "time to unplug" alerts. */
object ChargeNotifier {
    private const val CH_HUD = "charging_hud"
    private const val CH_ALERT = "charging_alert"
    private const val CH_CHIME = "charging_chime"
    private const val ID_HUD = 3
    private const val ID_ALERT = 4

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_HUD, "Live charging", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Unplug alerts", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_CHIME, "Charge limit chime", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun hud(ctx: Context, c: ChargeUi) {
        ensureChannels(ctx)
        val title = when {
            c.full -> "Fully charged"
            c.watts != null -> "%.1f W · %s".format(c.watts, c.profile.label)
            else -> "Charging · ${c.level}%"
        }
        val text = buildString {
            append(if (c.full) "You can unplug now" else c.etaText)
            append(" · %.1f°C".format(c.tempC))
            if (c.tooWarm) append(" · warm, may charge slower")
        }
        val b = Notification.Builder(ctx, CH_HUD)
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setProgress(100, c.level, false)
        if (c.fastActive) b.setSubText("Fast-Charge Active")
        ctx.getSystemService(NotificationManager::class.java).notify(ID_HUD, b.build())
    }

    fun cancelHud(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_HUD)
    }

    fun alert(ctx: Context, alert: ChargeAlert, level: Int, limit: Int) {
        if (alert == ChargeAlert.NONE) return
        ensureChannels(ctx)
        val title = if (alert == ChargeAlert.FULL) "Fully charged: unplug now" else "Battery at $level%: time to unplug"
        val text = if (alert == ChargeAlert.FULL) {
            "Apex Cleaner has switched fast-charge care off and your phone is back to normal."
        } else {
            "You asked for a reminder at $limit%. Unplugging here is gentler on the battery. You can change this limit in the app."
        }
        val chime = alert == ChargeAlert.REACHED_LIMIT && com.fitzy.apexcleaner.core.AppSettings.chargeChime(ctx)
        val n = Notification.Builder(ctx, if (chime) CH_CHIME else CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_ALERT, n)
    }

    /** High-priority warning when the battery gets hotter than the limit you set. Once per hot spell. */
    fun heatAlert(ctx: Context, tempC: Float) {
        ensureChannels(ctx)
        val text = "Battery is at ${"%.0f".format(tempC)}°C. Take the phone out of its case, move it somewhere cooler, or unplug for a while."
        val n = Notification.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setContentTitle("Phone is getting hot")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_ALERT + 1, n)
    }
}
