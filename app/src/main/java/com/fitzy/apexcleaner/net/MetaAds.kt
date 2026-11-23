package com.fitzy.apexcleaner.net

/**
 * Meta (Facebook) banners inside other apps load through names the Facebook app itself needs
 * (graph.facebook.com and the "xx" picture servers). So these names are blocked only when the
 * lookup comes from an app that is NOT made by Meta and is not a web browser. Facebook,
 * Instagram, Messenger and WhatsApp are never touched. Your Allow tap in the log always wins.
 */
object MetaAds {
    private val names = DomainTrie.fromList(sequenceOf(
        "graph.facebook.com", "b-graph.facebook.com", "an.facebook.com", "connect.facebook.net",
        "scontent.xx.fbcdn.net", "video.xx.fbcdn.net", "z-m-scontent.xx.fbcdn.net",
    ))
    private val metaPrefixes = listOf("com.facebook.", "com.instagram.", "com.whatsapp", "com.meta.")

    fun isMetaAdName(domain: String) = names.matches(domain)

    fun isMetaApp(pkg: String) = metaPrefixes.any { pkg.startsWith(it) }

    /** True when every app behind this lookup is an ordinary third-party app (unknown owner = leave alone). */
    fun shouldBlockFor(domain: String, packages: List<String>, isBrowser: (String) -> Boolean): Boolean =
        isMetaAdName(domain) && packages.isNotEmpty() && packages.none { isMetaApp(it) || isBrowser(it) }
}
