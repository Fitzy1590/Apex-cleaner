package com.fitzy.apexcleaner.net.mitm

/** Paywall reader (owner voted 2026-11-27, full). Browsers only, off by default, rides on the web page cleaner. */
object PaywallRules {
    /** News sites that show search crawlers the full article. Only these names are touched; everything else passes unchanged. */
    val sites = listOf(
        "nytimes.com", "washingtonpost.com", "wsj.com", "barrons.com", "bloomberg.com", "economist.com", "theatlantic.com", "newyorker.com",
        "wired.com", "businessinsider.com", "medium.com", "latimes.com", "bostonglobe.com", "seattletimes.com", "forbes.com", "hbr.org",
        "foreignpolicy.com", "newsweek.com", "time.com", "politico.com", "nymag.com", "thetimes.co.uk", "telegraph.co.uk", "smh.com.au",
        "theage.com.au", "afr.com", "scmp.com", "haaretz.com", "nationalpost.com", "theathletic.com", "irishtimes.com", "foreignaffairs.com")

    /** Paywall script providers, blocked at the name lookup while the reader is on. */
    val scriptHosts = listOf("tinypass.com", "piano.io", "cdn.tinypass.com", "c2.piano.io", "buy.tinypass.com", "experience.tinypass.com")

    const val CRAWLER_UA = "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
    const val REFERER = "https://www.google.com/"

    fun matches(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return sites.any { h == it || h.endsWith(".$it") }
    }

    /** Request headers with the crawler look-alike and Google referrer set (replacing any existing ones). */
    fun rewrite(hs: List<Pair<String, String>>): List<Pair<String, String>> =
        hs.filter { !it.first.equals("user-agent", true) && !it.first.equals("referer", true) } + ("User-Agent" to CRAWLER_UA) + ("Referer" to REFERER)

    private val hide = listOf(".tp-modal", ".tp-backdrop", ".tp-iframe-wrapper", ".tp-container-inner", ".subscription-gate", "#piano-offer", "[id^=\"piano-\"]",
        ".paywall", "#paywall", "[class*=\"paywall-overlay\"]", ".gateway-content", ".regwall", "#gateway-content", ".fc-ab-root")

    /** Hides paywall pop-ups and gives scrolling back, plus a small button that opens an archived copy for hard paywalls. */
    fun html(nonce: String): String {
        val css = hide.joinToString(",") + "{display:none!important}html,body{overflow:auto!important;position:static!important;height:auto!important}" +
            "#apex-arch{position:fixed;right:12px;bottom:12px;z-index:2147483647;display:flex;gap:6px;font:600 13px system-ui,sans-serif}" +
            "#apex-arch a{min-height:44px;min-width:44px;padding:0 14px;display:flex;align-items:center;border-radius:10px;background:#7C3AED;color:#fff;text-decoration:none}"
        val js = "(function(){if(window.top!==window)return;function add(){if(document.getElementById('apex-arch')||!document.body)return;var u=encodeURIComponent(location.href);" +
            "var d=document.createElement('div');d.id='apex-arch';d.innerHTML='<a href=\"https://archive.ph/newest/'+location.href+'\">Archived copy</a><a href=\"https://smry.ai/'+location.href+'\">Smry.ai</a>';document.body.appendChild(d)}" +
            "document.addEventListener('DOMContentLoaded',add);setTimeout(add,1500)})();"
        return "<style nonce=\"$nonce\">$css</style><script nonce=\"$nonce\">$js</script>"
    }
}
