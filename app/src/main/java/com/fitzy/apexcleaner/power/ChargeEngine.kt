package com.fitzy.apexcleaner.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import com.fitzy.apexcleaner.core.PowerPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Runs one charging session from plug-in to unplug.
 * Plug in: Apex's own background work waits so all power goes to the battery.
 * Unplug or 100%: everything returns to normal. Other apps are never touched.
 */
object ChargeEngine {
    private val _ui = MutableStateFlow(ChargeUi())
    val ui: StateFlow<ChargeUi> = _ui

    private var active = false
    private var startedAt = 0L
    private var smoothedUa: Double? = null
    private var samples = 0
    private var alertedLimit = false
    private var alertedFull = false
    private var alertedHeat = false
    private var lastUpdate = 0L
    private val curve = ArrayList<CurvePoint>()

    @Synchronized
    fun update(ctx: Context, force: Boolean = false) {
        val app = ctx.applicationContext
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastUpdate < 4_000L) return
        lastUpdate = now

        val s = ChargeReader(app).read()
        val settings = ChargeSettings(app)
        val limit = settings.limitPct
        val fastEnabled = settings.fastEnabled

        if (s.plugged && !active) {
            active = true
            startedAt = now
            smoothedUa = null
            samples = 0
            alertedLimit = s.level >= limit
            alertedFull = s.level >= 100
            alertedHeat = false
            curve.clear()
        }
        if (!s.plugged) {
            if (active) {
                active = false
                curve.clear()
                PowerPolicy.setChargeHold(app, false)
                ChargeNotifier.cancelHud(app)
            }
            _ui.value = ChargeUi(
                level = s.level, tempC = s.tempC, voltageV = s.voltageMv / 1000f,
                limitPct = limit, fastEnabled = fastEnabled,
            )
            return
        }

        if (s.currentUa > 0) smoothedUa = ChargeMath.smooth(smoothedUa, s.currentUa.toDouble())
        samples++
        val currentUa = smoothedUa?.toLong() ?: 0L
        val watts = ChargeMath.watts(s.voltageMv, currentUa)
        val fastActive = fastEnabled && !s.full
        PowerPolicy.setChargeHold(app, fastActive)
        if (watts != null && !s.full) {
            val sec = ((now - startedAt) / 1000).toInt()
            if (curve.isEmpty() || sec - curve.last().seconds >= 14) {
                curve.add(CurvePoint(sec, watts))
                if (curve.size > 240) {
                    val thinned = curve.filterIndexed { i, _ -> i % 2 == 0 }
                    curve.clear(); curve.addAll(thinned)
                }
            }
        }

        val settled = now - startedAt >= 60_000L && samples >= 4
        val eta = if (s.full) 0 to 0 else if (settled) ChargeMath.etaRange(s.level, s.chargeCounterUa, currentUa) else null

        if (alertedLimit && s.level < limit) alertedLimit = false
        val alert = ChargeMath.decideAlert(s.level, limit, alertedLimit, alertedFull)
        if (alert == ChargeAlert.REACHED_LIMIT) alertedLimit = true
        if (alert == ChargeAlert.FULL) alertedFull = true

        val ui = ChargeUi(
            pluggedIn = true, level = s.level, full = s.full, tempC = s.tempC,
            voltageV = s.voltageMv / 1000f,
            currentA = if (currentUa > 0) currentUa / 1_000_000f else null,
            watts = watts, profile = ChargeMath.profileFor(watts, true),
            etaMin = eta?.first, etaMax = eta?.second,
            fastActive = fastActive, tooWarm = ChargeMath.isTooWarm(s.tempC, s.thermalStatus, settings.hotC),
            limitPct = limit, fastEnabled = fastEnabled,
            curve = curve.toList(),
        )
        val (heatState, heatNow) = ChargeMath.heatAlertState(ui.tooWarm, s.tempC, settings.hotC, alertedHeat)
        alertedHeat = heatState
        if (heatNow) ChargeNotifier.heatAlert(app, s.tempC)
        _ui.value = ui
        ChargeNotifier.hud(app, ui)
        ChargeNotifier.alert(app, alert, s.level, limit)
    }
}

/** Hears plug-in, unplug and battery changes while the light service is running. */
class PowerEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ChargeEngine.update(context, force = intent.action != Intent.ACTION_BATTERY_CHANGED)
    }

    companion object {
        fun register(ctx: Context, r: PowerEventReceiver) {
            ctx.registerReceiver(r, IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            })
        }
    }
}
