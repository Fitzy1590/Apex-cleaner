package com.fitzy.apexcleaner.net

data class DomainStat(val domain: String, val blocked: Int, val allowed: Int, val last: Long) {
    val total get() = blocked + allowed
}

/** Remembers how often each name was looked up and whether it was blocked. Kept on this device only. */
class DnsHistory(private val max: Int = 300) {
    private val map = HashMap<String, DomainStat>()

    @Synchronized fun record(domain: String, blocked: Boolean, now: Long) {
        val d = domain.lowercase()
        val old = map[d]
        map[d] = DomainStat(d, (old?.blocked ?: 0) + if (blocked) 1 else 0, (old?.allowed ?: 0) + if (blocked) 0 else 1, now)
        if (map.size > max) map.values.minByOrNull { it.last }?.let { map.remove(it.domain) }
    }

    /** Busiest names first. */
    @Synchronized fun top(n: Int): List<DomainStat> = map.values.sortedWith(compareByDescending<DomainStat> { it.total }.thenByDescending { it.last }).take(n)

    @Synchronized fun toLines(): List<String> = map.values.map { "${it.domain}\t${it.blocked}\t${it.allowed}\t${it.last}" }

    @Synchronized fun load(lines: List<String>) {
        map.clear()
        for (l in lines) {
            val p = l.split('\t')
            if (p.size == 4) map[p[0]] = DomainStat(p[0], p[1].toIntOrNull() ?: continue, p[2].toIntOrNull() ?: continue, p[3].toLongOrNull() ?: continue)
        }
    }

    @Synchronized fun forget() = map.clear()
}
