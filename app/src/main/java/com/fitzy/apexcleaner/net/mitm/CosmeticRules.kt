package com.fitzy.apexcleaner.net.mitm

/** What gets added to web pages: a style rule that hides well-known ad containers, and two tiny stubs that stop ad scripts erroring. */
object CosmeticRules {
    /** Conservative selectors (ad-network containers and obvious ad boxes). Wide guesses are left out so real content is not hidden. */
    val hideSelectors = listOf(
        "ins.adsbygoogle", ".adsbygoogle", "[id^=\"google_ads_iframe\"]", "[id^=\"div-gpt-ad\"]", ".google-auto-placed", "[data-google-query-id]",
        "iframe[src*=\"doubleclick.net\"]", "iframe[src*=\"googlesyndication.com\"]", "iframe[src*=\"adservice.google.\"]", "iframe[src*=\"amazon-adsystem.com\"]",
        "[data-ad-slot]", "[data-ad-client]", ".ad-banner", ".ad-container", ".advertisement", ".adsbox", "#ad-banner", ".banner-ad", ".top-ads-container",
        ".taboola-widget", "[id^=\"taboola-\"]", "[id^=\"outbrain\"]", ".OUTBRAIN", "[id^=\"google_image_div\"]")

    fun css() = hideSelectors.joinToString(",") + "{display:none!important}"

    /** Not a full scriptlet engine: only stubs the Google ad queue and a common "can ads run" flag. */
    fun script() = "window.adsbygoogle=window.adsbygoogle||{push:function(){}};window.canRunAds=true;"

    /** Puts the style and script just before </head> (else after <head>, else before <body>). Null if the page has none of those. */
    fun inject(html: ByteArray, nonce: String): ByteArray? {
        val s = String(html, Charsets.ISO_8859_1)
        val low = s.lowercase()
        val add = "<style nonce=\"$nonce\">${css()}</style><script nonce=\"$nonce\">${script()}</script>"
        val at = low.indexOf("</head>").takeIf { it >= 0 }
            ?: low.indexOf("<head").takeIf { it >= 0 }?.let { h -> low.indexOf('>', h).takeIf { it >= 0 }?.plus(1) }
            ?: low.indexOf("<body").takeIf { it >= 0 } ?: return null
        return (s.substring(0, at) + add + s.substring(at)).toByteArray(Charsets.ISO_8859_1)
    }

    /** Lets our own style and script run under the page's Content-Security-Policy by adding a one-time nonce. Nothing else is loosened. */
    fun patchCsp(policy: String, nonce: String): String {
        val dirs = policy.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val names = dirs.map { it.substringBefore(' ').lowercase() }
        val needDefault = ("style-src" !in names && "style-src-elem" !in names) || ("script-src" !in names && "script-src-elem" !in names)
        val targets = setOf("script-src", "script-src-elem", "style-src", "style-src-elem") + if (needDefault) setOf("default-src") else emptySet()
        return dirs.joinToString("; ") { d ->
            val n = d.substringBefore(' ').lowercase()
            if (n in targets && !d.contains("'none'")) "$d 'nonce-$nonce'" else d
        }
    }
}
