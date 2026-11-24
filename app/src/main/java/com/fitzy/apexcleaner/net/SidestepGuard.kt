package com.fitzy.apexcleaner.net

/**
 * Names and addresses apps use to talk to a private lookup service of their own (encrypted DNS), which would let
 * their ad lookups slip past the shield. Blocking them makes apps fall back to the phone's normal lookup, which the
 * shield filters. Pure, so it is unit-tested. This app's own encrypted lookups are not affected: it sits outside the tunnel.
 */
object SidestepGuard {
    /** Firefox asks this name; an "does not exist" answer is the official way to tell it to keep using the phone's lookup. */
    const val FIREFOX_CANARY = "use-application-dns.net"

    private val privateLookupNames = DomainTrie().apply {
        listOf(
            "dns.google", "dns64.dns.google", "cloudflare-dns.com", "mozilla.cloudflare-dns.com", "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com",
            "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net", "doh.opendns.com", "dns.opendns.com",
            "dns.adguard.com", "dns.adguard-dns.com", "dns-unfiltered.adguard.com", "doh.dns.sb", "dns.nextdns.io",
            "doh.cleanbrowsing.org", "dns.cloudflare.com", "chrome.cloudflare-dns.com", "doh.mullvad.net", "dns.mullvad.net",
            "doh.libredns.gr", "dns.controld.com", "freedns.controld.com", "dnsforge.de", "doh.applied-privacy.net",
        ).forEach(::add)
    }

    /** Public lookup servers that apps sometimes call straight by address. Their traffic is sent into the tunnel and only plain lookups are answered. */
    val publicResolverAddresses = listOf("8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112", "208.67.222.222", "208.67.220.220")

    fun isPrivateLookupName(name: String) = privateLookupNames.matches(name)
}

/**
 * Small checking scripts some sites use to detect ad blockers ("please allow ads on this site"). When they cannot load,
 * the site shows a wall instead of the article. Letting only these through lets the page open while the real ad servers stay blocked.
 * Your own Block choice in the log still wins. Pure, so it is unit-tested.
 */
object AdblockCheckNames {
    private val names = DomainTrie().apply {
        listOf("html-load.com", "css-load.com", "img-load.com", "content-loader.com", "error-report.com", "getadmiral.com").forEach(::add)
    }
    fun matches(name: String) = names.matches(name)
}
