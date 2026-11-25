package com.fitzy.apexcleaner.net

/**
 * Guesses when a blocked ad name is stopping an app or page from loading. An app that cannot get an answer
 * usually asks again and again, so the same blocked name asked for several times in a short while by the
 * same app is treated as "stuck". Pure (no Android), so it is unit-tested. Looks at names only, never content.
 */
class TroubleMonitor(
    private val retries: Int = 4,
    private val windowMs: Long = 20_000,
    private val quietMs: Long = 30 * 60_000,
    private val maxPerHour: Int = 3,
) {
    private val asks = HashMap<String, ArrayList<Long>>()
    private val quietUntil = HashMap<String, Long>()
    private val prompts = ArrayList<Long>()

    /** True when the person should be asked about [domain] for [pkg]. */
    @Synchronized fun onBlocked(pkg: String, domain: String, now: Long): Boolean {
        val d = domain.lowercase()
        if ((quietUntil[d] ?: 0L) > now) return false
        val list = asks.getOrPut("$pkg|$d") { ArrayList() }
        list.add(now); list.removeAll { now - it > windowMs }
        if (asks.size > 200) asks.entries.removeAll { e -> e.value.none { now - it <= windowMs } }
        if (list.size < retries) return false
        prompts.removeAll { now - it > 3_600_000L }
        if (prompts.size >= maxPerHour) return false
        prompts.add(now); quietUntil[d] = now + quietMs; list.clear()
        return true
    }

    /** "Leave blocked": do not ask about this name again for a day. */
    @Synchronized fun dismiss(domain: String, now: Long) { quietUntil[domain.lowercase()] = now + 24 * 3_600_000L }
}

/** The one shared watcher for the running shield. */
object TroubleWatch {
    val monitor = TroubleMonitor()
}
