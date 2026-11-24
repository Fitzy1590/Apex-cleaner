package com.fitzy.apexcleaner.net

/**
 * Compact domain set. Each name is stored as one 64-bit fingerprint in a sorted array (about 8 bytes per name),
 * so even the big ad lists fit comfortably on a low-memory TV. A name matches if it, or any parent domain of it, was added
 * ("ads.example.com" matches "x.ads.example.com"). Name kept for the existing callers.
 */
class DomainTrie {
    private var pending = LongArray(16)
    private var pendingCount = 0
    @Volatile private var sorted = LongArray(0)
    @Volatile private var dirty = false

    val size: Int get() { freeze(); return sorted.size }

    @Synchronized fun add(domain: String) {
        val n = normalize(domain)
        if (n.isEmpty()) return
        if (pendingCount == pending.size) pending = pending.copyOf(pending.size * 2)
        pending[pendingCount++] = hash(n, 0)
        dirty = true
    }

    /** True if the domain, or any parent domain of it, was added. */
    fun matches(domain: String): Boolean {
        if (dirty) freeze()
        val s = sorted
        if (s.isEmpty()) return false
        val n = normalize(domain)
        var start = 0
        while (start < n.length) {
            if (java.util.Arrays.binarySearch(s, hash(n, start)) >= 0) return true
            val dot = n.indexOf('.', start)
            if (dot < 0) break
            start = dot + 1
        }
        return false
    }

    @Synchronized private fun freeze() {
        if (!dirty) return
        val merged = LongArray(sorted.size + pendingCount)
        System.arraycopy(sorted, 0, merged, 0, sorted.size)
        System.arraycopy(pending, 0, merged, sorted.size, pendingCount)
        merged.sort()
        var w = 0
        for (i in merged.indices) if (i == 0 || merged[i] != merged[i - 1]) merged[w++] = merged[i]
        sorted = merged.copyOf(w)
        pending = LongArray(16); pendingCount = 0
        dirty = false
    }

    /** Lower-case, no spaces, no trailing dot, no empty labels. */
    private fun normalize(d: String): String {
        val t = d.trim().trimEnd('.').lowercase()
        return if (t.contains("..") || t.startsWith(".")) t.split('.').filter { it.isNotEmpty() }.joinToString(".") else t
    }

    /** 64-bit FNV-1a of the text from [from] to the end. */
    private fun hash(s: String, from: Int): Long {
        var h = -3750763034362895579L
        for (i in from until s.length) { h = h xor s[i].code.toLong(); h *= 1099511628211L }
        return h
    }

    companion object {
        /** Reads a hosts-style or plain list ("0.0.0.0 ads.example.com", "ads.example.com", "# comment"). */
        fun fromList(lines: Sequence<String>): DomainTrie = DomainTrie().apply {
            for (raw in lines) {
                var line = raw.substringBefore('#').trim()
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("@@")) continue
                if (line.startsWith("||")) { // AdGuard style: ||ads.example.com^
                    if (line.any { it == '$' || it == '*' || it == '/' }) continue
                    line = line.removePrefix("||").removeSuffix("^")
                }
                val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
                if (parts.isEmpty()) continue
                val host = if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) parts[1] else parts[0]
                val clean = host.removePrefix("*.")
                if (clean.contains('.') && !clean.contains('*') && clean != "0.0.0.0" && clean != "localhost") add(clean)
            }
        }
    }
}
