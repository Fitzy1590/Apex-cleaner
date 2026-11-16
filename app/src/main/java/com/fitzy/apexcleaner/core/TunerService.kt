package com.fitzy.apexcleaner.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.fitzy.apexcleaner.power.ChargeEngine
import com.fitzy.apexcleaner.power.PowerEventReceiver

/** Very light foreground service: checks health every 2 minutes (10 when the screen is off). */
class TunerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val screenReceiver = ScreenStateReceiver()
    private val powerReceiver = PowerEventReceiver()
    private var lastTipAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!PowerPolicy.isPaused(this@TunerService) && !PowerPolicy.chargeHold(this@TunerService)) {
                val h = HealthMonitor(this@TunerService).read()
                val now = System.currentTimeMillis()
                if (h.needsAttention && now - lastTipAt > 30 * 60_000) {
                    lastTipAt = now
                    Notifier.postTip(this@TunerService, h)
                }
            }
            handler.postDelayed(this, if (PowerPolicy.screenOn) 2 * 60_000L else 10 * 60_000L)
        }
    }

    /** Refreshes the charging card every 15 s while plugged in; only every 5 min otherwise. */
    private val chargeTick = object : Runnable {
        override fun run() {
            ChargeEngine.update(this@TunerService)
            val plugged = ChargeEngine.ui.value.pluggedIn
            handler.postDelayed(this, if (!plugged) 5 * 60_000L else if (PowerPolicy.screenOn) 15_000L else 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val n = Notifier.serviceNotification(this)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(Notifier.ID_SERVICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID_SERVICE, n)
        }
        ScreenStateReceiver.register(this, screenReceiver)
        PowerEventReceiver.register(this, powerReceiver)
        handler.post(tick)
        handler.post(chargeTick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(chargeTick)
        runCatching { unregisterReceiver(powerReceiver) }
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, TunerService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TunerService::class.java))
        }
    }
}
