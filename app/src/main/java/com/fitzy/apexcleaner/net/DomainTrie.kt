package com.fitzy.apexcleaner.net

/** In-memory trie over reversed domain labels. "ads.example.com" is stored as com > example > ads. */
class DomainTrie {
    private class Node { val kids = HashMap<String, Node>(); var end = false }
    private val root = Node()
    var size = 0; private set

    fun add(domain: String) {
        var n = root
        for (label in labels(domain)) n = n.kids.getOrPut(label) { Node() }
        if (!n.end) { n.end = true; size++ }
    }

    /** True if the domain, or any parent domain of it, was added. */
    fun matches(domain: String): Boolean {
        var n = root
        for (label in labels(domain)) {
            n = n.kids[label] ?: return false
            if (n.end) return true
        }
        return false
    }

    private fun labels(d: String) = d.trim().trimEnd('.').lowercase().split('.').filter { it.isNotEmpty() }.asReversed()

    companion object {
        /** Reads a hosts-style or plain list ("0.0.0.0 ads.example.com", "ads.example.com", "# comment"). */
        fun fromList(lines: Sequence<String>): DomainTrie = DomainTrie().apply {
            for (raw in lines) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val parts = line.split(Regex("\\s+"))
                val host = if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) parts[1] else parts[0]
                if (host.contains('.') && host != "0.0.0.0" && host != "localhost") add(host)
            }
        }
    }
}
