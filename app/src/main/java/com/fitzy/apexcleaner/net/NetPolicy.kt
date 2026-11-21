package com.fitzy.apexcleaner.net

enum class Link { WIFI, CELLULAR }
enum class AppState { FOREGROUND, BACKGROUND }

/** Per-app speed caps in bytes per second (0 = no cap). */
data class AppCaps(val fgDown: Long = 0, val bgDown: Long = 0, val fgUp: Long = 0, val bgUp: Long = 0, val cellularFactor: Double = 1.0, val chargingFactor: Double = 1.0)

/** Pure rules that decide the cap an app gets right now. Protected apps and DNS are never paced. */
object NetPolicy {
    fun capFor(caps: AppCaps, down: Boolean, state: AppState, link: Link, charging: Boolean, isProtected: Boolean, isDns: Boolean): Long {
        if (isProtected || isDns) return 0L
        val base = when {
            down && state == AppState.FOREGROUND -> caps.fgDown
            down -> caps.bgDown
            state == AppState.FOREGROUND -> caps.fgUp
            else -> caps.bgUp
        }
        if (base <= 0) return 0L
        var v = base.toDouble()
        if (link == Link.CELLULAR) v *= caps.cellularFactor
        if (charging) v *= caps.chargingFactor
        return v.toLong().coerceAtLeast(1024L)
    }
}
