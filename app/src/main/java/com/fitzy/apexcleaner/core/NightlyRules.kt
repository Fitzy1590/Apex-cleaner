package com.fitzy.apexcleaner.core

import java.util.Calendar

/** Pure rules for the 3 AM maintenance run. No Android calls, so it is unit-tested. */
object NightlyRules {
    const val MIN_BATTERY = 80
    const val MIN_IDLE_MINUTES = 30L

    /** Runs only when plugged in, battery above 80%, screen off and idle for 30+ minutes. */
    fun canRun(plugged: Boolean, batteryPct: Int, screenOn: Boolean, idleMinutes: Long): Boolean =
        plugged && batteryPct > MIN_BATTERY && !screenOn && idleMinutes >= MIN_IDLE_MINUTES

    /** Milliseconds from [now] until the next 3:00 AM. */
    fun delayToNextThreeAm(now: Calendar): Long {
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 3); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
