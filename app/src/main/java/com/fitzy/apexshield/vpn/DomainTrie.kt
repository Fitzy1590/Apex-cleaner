package com.fitzy.apexshield.vpn

enum class Category(val label: String) {
    ADS("Ads"), TRACKERS("Trackers"), ANALYTICS("Analytics"), SMART_TV("Smart TV"), MALWARE("Malware"), CUSTOM("Blocked by you"),
}

/** Domain matcher. Labels are stored right to left, so blocking "example.com" also blocks "ads.example.com". */
class DomainTrie {
    private class Node {
        var kids: HashMap<String, Node>? = null
        var category: Category? = null
    }

    private val root = Node()
    var size = 0
        private set

    fun add(domain: String, category: Category = Category.ADS) {
        var n = root
        for (label in labels(domain)) {
            val kids = n.kids ?: HashMap<String, Node>(4).also { n.kids = it }
            n = kids.getOrPut(label) { Node() }
        }
        if (n.category == null) size++
        n.category = category
    }

    fun remove(domain: String) {
        var n = root
        for (label in labels(domain)) n = n.kids?.get(label) ?: return
        if (n.category != null) size--
        n.category = null
    }

    /** Category of the closest listed domain that equals [host] or is a parent of it, else null. */
    fun match(host: String): Category? {
        var n = root
        var found: Category? = null
        for (label in labels(host)) {
            n = n.kids?.get(label) ?: break
            n.category?.let { found = it }
        }
        return found
    }

    private fun labels(d: String): List<String> =
        d.trim().trimEnd('.').lowercase().split('.').filter { it.isNotEmpty() }.asReversed()
}
