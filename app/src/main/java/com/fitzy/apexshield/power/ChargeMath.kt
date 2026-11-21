package com.fitzy.apexshield.power

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** What kind of charger the phone seems to be on, judged from live power. */
enum class ChargeProfile(val label: String) {
    NONE("Not charging"),
    UNKNOWN("Charging"),
    TRICKLE("Slow charge"),
    USB_5W("5W USB"),
    STANDARD_10W("10W Charge"),
    FAST_15W("15W Fast Charge"),
    FAST_30W("30W Fast Charge"),
    SUPER_45W("45W+ Super Fast Charge"),
}

enum class ChargeAlert { NONE, REACHED_LIMIT, FULL }

/** Pure charging maths and rules. No Android calls, so it is fully unit-tested. */
object ChargeMath {
    const val HOT_C = 40f
    const val THERMAL_MODERATE = 2
    const val MAX_PLAUSIBLE_WATTS = 150f
    const val MIN_LIMIT = 50
    const val MAX_LIMIT = 100
    const val DEFAULT_LIMIT = 80

    /**
     * Android says current is in microamps, but some phones report milliamps and some get the sign wrong.
     * Anything under 20,000 is treated as milliamps (that would be under 20 mA in microamps, never real),
     * and the sign is dropped because we already know from the charging flag which way power flows.
     */
    fun normalizeMicroAmps(raw: Long): Long {
        if (raw == Long.MIN_VALUE) return 0L
        val a = abs(raw)
        return if (a in 1..19_999) a * 1000 else a
    }

    /** Estimated power in watts (battery volts x amps). Null when unreadable or implausible. */
    fun watts(voltageMv: Int, currentUa: Long): Float? {
        if (voltageMv <= 0 || currentUa <= 0) return null
        val w = voltageMv / 1000.0 * currentUa / 1_000_000.0
        return if (w > MAX_PLAUSIBLE_WATTS) null else w.toFloat()
    }

    fun profileFor(watts: Float?, charging: Boolean): ChargeProfile = when {
        !charging -> ChargeProfile.NONE
        watts == null -> ChargeProfile.UNKNOWN
        watts < 3f -> ChargeProfile.TRICKLE
        watts < 7.5f -> ChargeProfile.USB_5W
        watts < 12f -> ChargeProfile.STANDARD_10W
        watts < 20f -> ChargeProfile.FAST_15W
        watts < 35f -> ChargeProfile.FAST_30W
        else -> ChargeProfile.SUPER_45W
    }

    /** Gentle smoothing so the numbers do not jump around. */
    fun smooth(previous: Double?, sample: Double, alpha: Double = 0.2): Double =
        if (previous == null) sample else previous + alpha * (sample - previous)

    /** Phone counts as too warm at 40 C, or when Android itself says it is getting hot. */
    fun isTooWarm(tempC: Float, thermalStatus: Int?): Boolean =
        tempC >= HOT_C || (thermalStatus != null && thermalStatus >= THERMAL_MODERATE)

    /**
     * Minutes to full as a range. Charging slows down above 80%, so that part counts double.
     * Null when we cannot tell yet.
     */
    fun etaRange(level: Int, chargeCounterUa: Long, currentUa: Long): Pair<Int, Int>? {
        if (level >= 100) return 0 to 0
        if (level <= 0 || chargeCounterUa <= 0 || currentUa <= 0) return null
        val capacityUah = chargeCounterUa * 100.0 / level
        val lowPart = capacityUah * (maxOf(0, 80 - level)) / 100.0
        val highPart = capacityUah * (100 - maxOf(level, 80)) / 100.0
        val minutes = (lowPart + highPart * 2.0) / currentUa * 60.0
        if (minutes.isNaN() || minutes.isInfinite() || minutes > 24 * 60) return null
        val lo = (floor(minutes * 0.85 / 5.0) * 5).toInt().coerceAtLeast(5)
        val hi = (ceil(minutes * 1.25 / 5.0) * 5).toInt().coerceAtLeast(lo + 5)
        return lo to hi
    }

    fun formatMinutes(m: Int): String = when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }

    fun formatEta(min: Int, max: Int): String =
        if (max < 60) "$min–$max min" else "${formatMinutes(min)} to ${formatMinutes(max)}"

    fun clampLimit(v: Int): Int = v.coerceIn(MIN_LIMIT, MAX_LIMIT)

    /** One alert at the chosen limit and one at 100%, each at most once per charge. */
    fun decideAlert(level: Int, limit: Int, alertedLimit: Boolean, alertedFull: Boolean): ChargeAlert = when {
        level >= 100 -> if (alertedFull) ChargeAlert.NONE else ChargeAlert.FULL
        limit < 100 && level >= limit && !alertedLimit -> ChargeAlert.REACHED_LIMIT
        else -> ChargeAlert.NONE
    }
}
