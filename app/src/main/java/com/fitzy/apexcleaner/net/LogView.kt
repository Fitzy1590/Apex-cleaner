package com.fitzy.apexcleaner.net

/** Plain-English labels and filtering for the Network Logs screen. */
object LogView {
    private val ads = listOf("doubleclick", "googlesyndication", "googleadservices", "adservice", "unityads", "vungle", "inmobi", "applovin", "mopub", "taboola", "outbrain", "criteo", "adnxs", "adsrvr", "amazon-adsystem", "chartboost", "ironsrc", "pubmatic", "openx", "rubicon", "smaato", "admob", "ads.", ".ads", "adserver", "advert")
    private val analytics = listOf("analytics", "crashlytics", "app-measurement", "scorecardresearch", "mixpanel", "flurry", "appsflyer", "adjust", "branch.io", "kochava", "hotjar", "segment", "telemetry", "metrics", "stats.", "measure", "sentry", "bugsnag", "amplitude", "firebase-settings")
    private val tvSpy = listOf("samsungacr", "lgsmartad", "lgtvsdp", "tvinteractive", "samsungads", "acr.")

    fun category(domain: String): String {
        val d = domain.lowercase()
        return when {
            tvSpy.any { it in d } -> "TV tracking"
            ads.any { it in d } -> "Ads"
            analytics.any { it in d } -> "Analytics"
            else -> "Tracker"
        }
    }

    /** Newest first. Search matches any part of the name. "Blocked only" keeps names that were blocked or that you blocked. */
    fun filter(items: List<DomainStat>, query: String, blockedOnly: Boolean, rules: Map<String, Rule>): List<DomainStat> {
        val q = query.trim().lowercase()
        return items.filter { q.isEmpty() || q in it.domain }
            .filter { !blockedOnly || it.blocked > 0 || rules[it.domain] == Rule.DENY }
            .sortedByDescending { it.last }
    }
}
