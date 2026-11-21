package com.fitzy.apexshield.vpn

/** Reads list files. Lines are plain domains or hosts-style ("0.0.0.0 domain"); "[ads]" style headers set the category. */
object ShieldLists {
    fun parse(text: String, default: Category = Category.ADS): List<Pair<String, Category>> {
        var cat = default
        val out = ArrayList<Pair<String, Category>>()
        for (raw in text.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                val key = line.removeSurrounding("[", "]").uppercase().replace(' ', '_')
                cat = Category.values().firstOrNull { it.name == key } ?: default
                continue
            }
            val d = line.split(Regex("\\s+")).last().lowercase()
            if ('.' in d && d != "0.0.0.0" && d != "127.0.0.1") out += d to cat
        }
        return out
    }
}

/** Decides what to do with a looked-up name. Passthrough and "allowed" names always win over block lists. */
class DnsFilter(val block: DomainTrie = DomainTrie(), val allow: DomainTrie = DomainTrie()) {
    /** Returns the block category, or null when the name should be answered normally. */
    fun blockedCategory(host: String): Category? =
        if (allow.match(host) != null) null else block.match(host)
}
