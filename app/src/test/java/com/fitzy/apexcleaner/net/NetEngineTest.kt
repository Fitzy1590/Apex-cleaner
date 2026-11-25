package com.fitzy.apexcleaner.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetEngineTest {
    @Test fun bankingListHasAtLeast284Names() = assertTrue(SafeLists.bankingDomains.size >= 284)
    @Test fun clockAndLicenceNamesAreEssential() = assertTrue(SafeLists.essentialDomains.containsAll(listOf("time.samsungcloudsolution.com", "license.widevine.com")))

    @Test fun bucketPacesInsteadOfDropping() {
        var t = 0L
        val b = TokenBucket(1000, 1000) { t }
        assertEquals(0L, b.reserve(1000))            // burst is free
        assertEquals(1_000_000_000L, b.reserve(1000)) // next 1000 bytes wait one second
        t += 2_000_000_000L
        assertEquals(0L, b.reserve(500))              // time refills tokens
    }

    @Test fun zeroRateMeansUnlimited() = assertEquals(0L, TokenBucket(0).reserve(10_000_000))

    @Test fun trieMatchesParentDomains() {
        val t = DomainTrie.fromList(sequenceOf("# c", "0.0.0.0 ads.example.com", "tracker.net", "localhost"))
        assertTrue(t.matches("ads.example.com")); assertTrue(t.matches("x.ads.example.com"))
        assertTrue(t.matches("sub.tracker.net")); assertTrue(!t.matches("example.com")); assertTrue(!t.matches("notads.example.com"))
        assertEquals(2, t.size)
    }

    @Test fun yourChoicesBeatBlocklistsButNotBanking() {
        val e = FilterEngine.withDefaults(sequenceOf("ads.test.com"))
        e.userAllow = DomainTrie().apply { add("ads.test.com") }
        assertEquals(FilterEngine.Verdict.ALLOW, e.decide("ads.test.com"))
        e.userDeny = DomainTrie().apply { add("news.org"); add("chase.com") }
        assertEquals(FilterEngine.Verdict.BLOCK, e.decide("www.news.org"))
        assertEquals(FilterEngine.Verdict.SAFE_PASSTHROUGH, e.decide("chase.com"))
    }

    @Test fun historyCountsSortsAndSurvivesSaving() {
        val h = DnsHistory(max = 3)
        h.record("a.com", true, 1); h.record("a.com", true, 2); h.record("b.com", false, 3); h.record("C.com", false, 4)
        assertEquals(listOf("a.com", "c.com", "b.com"), h.top(5).map { it.domain })
        assertEquals(2, h.top(1)[0].blocked)
        h.record("d.com", false, 5) // over the limit: the name seen longest ago goes
        assertTrue(h.top(10).none { it.domain == "a.com" })
        val h2 = DnsHistory(); h2.load(h.toLines()); assertEquals(h.top(10), h2.top(10))
    }

    @Test fun logLabelsAndFilters() {
        assertEquals("Ads", LogView.category("googleads.g.doubleclick.net"))
        assertEquals("Analytics", LogView.category("app-measurement.com"))
        assertEquals("Analytics", LogView.category("firebase-settings.crashlytics.com"))
        assertEquals("Tracker", LogView.category("something.example"))
        val items = listOf(DomainStat("a.com", 2, 0, 5), DomainStat("b.com", 0, 3, 9), DomainStat("c.net", 1, 1, 7))
        assertEquals(listOf("c.net", "a.com"), LogView.filter(items, "", true, emptyMap()).map { it.domain })
        assertEquals(listOf("b.com", "c.net", "a.com"), LogView.filter(items, "", false, emptyMap()).map { it.domain })
        assertEquals(listOf("b.com"), LogView.filter(items, "B.", false, emptyMap()).map { it.domain })
        assertEquals(listOf("b.com", "c.net", "a.com"), LogView.filter(items, "", true, mapOf("b.com" to Rule.DENY)).map { it.domain })
    }

    @Test fun readsAdGuardStyleLines() {
        val t = DomainTrie.fromList(sequenceOf("! comment", "||ads.one.com^", "||bad.two.com^\$third-party", "@@||ok.three.com^", "||x.com/path^"))
        assertTrue(t.matches("ads.one.com")); assertTrue(!t.matches("bad.two.com")); assertTrue(!t.matches("ok.three.com")); assertTrue(!t.matches("x.com"))
    }

    @Test fun bankingAndPushNeverBlocked() {
        val e = FilterEngine.withDefaults(sequenceOf("chase.com", "mtalk.google.com", "ads.test.com"))
        assertEquals(FilterEngine.Verdict.SAFE_PASSTHROUGH, e.decide("secure.chase.com"))
        assertEquals(FilterEngine.Verdict.SAFE_PASSTHROUGH, e.decide("mtalk.google.com"))
        assertEquals(FilterEngine.Verdict.BLOCK, e.decide("ads.test.com"))
        assertEquals(FilterEngine.Verdict.BLOCK, e.decide("securepubads.g.doubleclick.net"))
        assertEquals(FilterEngine.Verdict.ALLOW, e.decide("example.org"))
    }

    @Test fun dnsQuestionParsedAndBlockedAnswerBuilt() {
        val q = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
            3, 'a'.code.toByte(), 'd'.code.toByte(), 's'.code.toByte(), 4, 't'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 0, 0, 1, 0, 1)
        val p = DnsPacket.parseQuestion(q)
        assertNotNull(p); assertEquals("ads.test", p!!.name); assertEquals(1, p.type)
        val r = DnsPacket.blockedResponse(q, q.size, p)
        assertEquals(0x12.toByte(), r[0]); assertEquals(0x81.toByte(), r[2]); assertEquals(1, r[7].toInt())
        assertEquals(q.size + 16, r.size)
        assertNull(DnsPacket.parseQuestion(r)) // a response is not a query
    }

    @Test fun dnsAndProtectedAppsAreNeverPaced() {
        val caps = AppCaps(fgDown = 1_000_000, bgDown = 100_000, cellularFactor = 0.5, chargingFactor = 2.0)
        assertEquals(0L, NetPolicy.capFor(caps, true, AppState.BACKGROUND, Link.WIFI, false, false, isDns = true))
        assertEquals(0L, NetPolicy.capFor(caps, true, AppState.BACKGROUND, Link.WIFI, false, isProtected = true, isDns = false))
        assertEquals(100_000L, NetPolicy.capFor(caps, true, AppState.BACKGROUND, Link.WIFI, false, false, false))
        assertEquals(50_000L, NetPolicy.capFor(caps, true, AppState.BACKGROUND, Link.CELLULAR, false, false, false))
        assertEquals(200_000L, NetPolicy.capFor(caps, true, AppState.BACKGROUND, Link.WIFI, true, false, false))
        assertEquals(0L, NetPolicy.capFor(caps, false, AppState.FOREGROUND, Link.WIFI, false, false, false))
    }
}

class AdBoxAndReplyTest {
    @Test fun adBoxLabelsAreStrict() {
        assertTrue(AdBoxRules.looksLikeAd("com.x:id/ad_container", null, null))
        assertTrue(AdBoxRules.looksLikeAd("com.x:id/nativeAdView", null, null))
        assertTrue(AdBoxRules.looksLikeAd(null, "com.google.android.gms.ads.AdView", null))
        assertTrue(AdBoxRules.looksLikeAd(null, null, "Advertisement"))
        assertTrue(!AdBoxRules.looksLikeAd("com.x:id/download_button", null, null))
        assertTrue(!AdBoxRules.looksLikeAd("com.x:id/address_line", null, null))
        assertTrue(!AdBoxRules.looksLikeAd("com.x:id/header", null, "Loading"))
    }

    @Test fun adBoxSizeIsSensible() {
        assertTrue(AdBoxRules.bottomBanner("android.webkit.WebView", 0, 2250, 1080, 2380, 1080, 2400))
        assertTrue(!AdBoxRules.bottomBanner("android.webkit.WebView", 0, 0, 1080, 2300, 1080, 2400))
        assertTrue(!AdBoxRules.bottomBanner("android.widget.TextView", 0, 2250, 1080, 2380, 1080, 2400))
        assertTrue(AdBoxRules.sensibleSize(1080, 250, 1080, 2400))
        assertTrue(!AdBoxRules.sensibleSize(1080, 2000, 1080, 2400))
        assertTrue(!AdBoxRules.sensibleSize(10, 10, 1080, 2400))
    }

    @Test fun sofascoreFeedBannerOnlyInSofascore() {
        val wv = "android.webkit.WebView"
        assertTrue(AdBoxRules.sofascoreBanner("com.sofascore.results", wv, 760, 120, 1080, 2400))
        assertTrue(!AdBoxRules.sofascoreBanner("com.other.app", wv, 760, 120, 1080, 2400))
        assertTrue(!AdBoxRules.sofascoreBanner("com.sofascore.results", "android.widget.TextView", 760, 120, 1080, 2400))
        // Bottom-docked picture banner narrower than the screen is covered; a full-width bar (navigation) and a mid-screen picture are not.
        assertTrue(AdBoxRules.sofascoreBanner("com.sofascore.results", "android.view.View", 760, 120, 1080, 2400, 2380))
        assertTrue(!AdBoxRules.sofascoreBanner("com.sofascore.results", "android.view.View", 1080, 120, 1080, 2400, 2400))
        assertTrue(!AdBoxRules.sofascoreBanner("com.sofascore.results", "android.view.View", 760, 120, 1080, 2400, 1200))
        // The small "Ad" tag near the bottom locates the promo strip; other apps, other words and mid-screen tags are ignored.
        val box = AdBoxRules.sofascoreAdTagBox("com.sofascore.results", "Ad", 2268, 2300, 1080, 2400)!!
        assertTrue(box[0] > 100 && box[2] < 1000 && box[3] <= 2400 && box[1] < 2268)
        assertNull(AdBoxRules.sofascoreAdTagBox("com.other.app", "Ad", 2268, 2300, 1080, 2400))
        assertNull(AdBoxRules.sofascoreAdTagBox("com.sofascore.results", "Add", 2268, 2300, 1080, 2400))
        assertNull(AdBoxRules.sofascoreAdTagBox("com.sofascore.results", "Ad", 1000, 1032, 1080, 2400))
        assertTrue(!AdBoxRules.sofascoreBanner("com.sofascore.results", wv, 1080, 2000, 1080, 2400))
    }

    @Test fun nameErrorReplyKeepsQuestionAndSetsErrorCode() {
        val q = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 3, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0, 0, 1, 0, 1)
        val parsed = DnsPacket.parseQuestion(q)!!
        val r = DnsPacket.nxdomainResponse(q, parsed)
        assertEquals(3, r[3].toInt() and 0x0f)
        assertEquals(0, r[7].toInt())
        assertEquals(0x12, r[0].toInt()); assertEquals(q.size, r.size)
    }
}

class AdNoticeTest {
    @Test fun recognisesAdBlockerNotices() {
        assertTrue(AdNoticeRules.looksLikeNotice("We noticed that ads aren't being displayed."))
        assertTrue(AdNoticeRules.looksLikeNotice("We noticed that ads aren\u2019t being displayed."))
        assertTrue(AdNoticeRules.looksLikeNotice("How to Allow Ads on This Site"))
        assertTrue(!AdNoticeRules.looksLikeNotice("Ads help keep content available for free"))
        assertTrue(!AdNoticeRules.looksLikeNotice("x".repeat(300) + " using an ad blocker"))
        assertTrue(!AdNoticeRules.looksLikeNotice(null))
    }

    @Test fun browsersAndCloseButtons() {
        assertTrue(AdNoticeRules.isBrowser("com.android.chrome"))
        assertTrue(!AdNoticeRules.isBrowser("com.yahoo.mobile.client.android.fantasyfootball"))
        assertTrue(AdNoticeRules.looksLikeClose("Close", false, 300, 60, 0, 700, 170))
        assertTrue(AdNoticeRules.looksLikeClose(null, true, 60, 60, 10, 700, 170))
        assertTrue(!AdNoticeRules.looksLikeClose("How to Allow Ads on This Site", true, 600, 120, 500, 700, 170))
        assertTrue(!AdNoticeRules.looksLikeClose(null, true, 60, 60, 600, 700, 170))
        assertTrue(AdNoticeRules.fullScreen(1080, 2300, 1080, 2400))
        assertTrue(!AdNoticeRules.fullScreen(1000, 900, 1080, 2400))
    }
}

class MetaAdsTest {
    @org.junit.Test fun blocksForThirdPartyAppsOnly() {
        val no: (String) -> Boolean = { false }
        org.junit.Assert.assertTrue(MetaAds.shouldBlockFor("graph.facebook.com", listOf("com.sofascore.results"), no))
        org.junit.Assert.assertFalse(MetaAds.shouldBlockFor("graph.facebook.com", listOf("com.facebook.katana"), no))
        org.junit.Assert.assertFalse(MetaAds.shouldBlockFor("graph.facebook.com", listOf("com.android.chrome"), { it.contains("chrome") }))
        org.junit.Assert.assertFalse(MetaAds.shouldBlockFor("graph.facebook.com", emptyList(), no))
        org.junit.Assert.assertFalse(MetaAds.shouldBlockFor("www.facebook.com", listOf("com.sofascore.results"), no))
    }

    // --- accuracy upgrades (2026-11-24) ---
    private fun dnsAnswer(name: String, cname: String): ByteArray {
        fun enc(n: String) = n.split('.').flatMap { l -> listOf(l.length.toByte()) + l.toByteArray().toList() } + 0.toByte()
        val q = enc(name)
        val target = enc(cname)
        val head = listOf<Byte>(0, 1, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0)
        val qtail = listOf<Byte>(0, 1, 0, 1)
        val rr = listOf<Byte>(-64, 12, 0, 5, 0, 1, 0, 0, 0, 60, 0, target.size.toByte()) + target
        return (head + q + qtail + rr).toByteArray()
    }

    @Test fun aliasTargetsAreReadFromAnswers() {
        assertEquals(listOf("tracker.adnet.com"), DnsPacket.cnameTargets(dnsAnswer("metrics.news.com", "tracker.adnet.com")))
        assertEquals(emptyList<String>(), DnsPacket.cnameTargets(ByteArray(5)))
        val cut = dnsAnswer("metrics.news.com", "tracker.adnet.com").copyOf(40) // damaged answer must not crash
        DnsPacket.cnameTargets(cut)
    }

    @Test fun disguisedTrackersAreBlockedButYourAllowAndSafeNamesWin() {
        val e = FilterEngine.withDefaults(sequenceOf("tracker.adnet.com"))
        assertEquals(FilterEngine.Verdict.ALLOW, e.decide("metrics.news.com"))
        assertEquals(FilterEngine.Verdict.BLOCK, e.decideWithAliases("metrics.news.com", listOf("edge.tracker.adnet.com")))
        assertEquals(FilterEngine.Verdict.ALLOW, e.decideWithAliases("metrics.news.com", listOf("cdn.fine.com")))
        e.userAllow = DomainTrie().apply { add("metrics.news.com") }
        assertEquals(FilterEngine.Verdict.ALLOW, e.decideWithAliases("metrics.news.com", listOf("tracker.adnet.com")))
        val safe = FilterEngine.withDefaults(sequenceOf("tracker.adnet.com"))
        assertEquals(FilterEngine.Verdict.SAFE_PASSTHROUGH, safe.decideWithAliases("chase.com", listOf("tracker.adnet.com")))
    }

    @Test fun listParserHandlesWildcardLines() {
        val t = DomainTrie.fromList(sequenceOf("*.wild.example.com", "*.*.bad", "plain.example.org"))
        assertTrue(t.matches("x.wild.example.com")); assertTrue(t.matches("plain.example.org")); assertEquals(2, t.size)
    }

    @Test fun privateLookupServicesAreRecognised() {
        assertTrue(SidestepGuard.isPrivateLookupName("dns.google")); assertTrue(SidestepGuard.isPrivateLookupName("mozilla.cloudflare-dns.com"))
        assertTrue(!SidestepGuard.isPrivateLookupName("www.google.com")); assertTrue(!SidestepGuard.isPrivateLookupName("google.com"))
    }

    @Test fun tcpToHardWiredResolverGetsAQuickRefusal() {
        val src = byteArrayOf(10, 111, -34, 1); val dst = byteArrayOf(8, 8, 8, 8)
        val syn = Packets.buildTcp(src, dst, 40000, 443, 1000, 0, Packets.SYN, 65535)
        val h = Packets.header(syn)!!
        val r = Packets.resetFor(syn, h)!!
        val rh = Packets.header(r)!!; val rt = Packets.tcp(r, rh)!!
        assertTrue(rt.flags and Packets.RST != 0); assertEquals(1001L, rt.ack); assertEquals(40000, rt.dstPort)
        assertNull(Packets.resetFor(r, rh)) // never answer a refusal with another
    }

    @Test fun adblockCheckScriptsAreRecognisedNotOrdinaryAdServers() {
        assertTrue(AdblockCheckNames.matches("html-load.com")); assertTrue(AdblockCheckNames.matches("cdn.html-load.com"))
        assertTrue(!AdblockCheckNames.matches("doubleclick.net")); assertTrue(!AdblockCheckNames.matches("load.com"))
    }
}

class TroubleMonitorTest {
    @Test fun asksOnlyAfterRepeatedTriesByTheSameApp() {
        val m = TroubleMonitor()
        assertTrue(!m.onBlocked("a", "ads.x.com", 0)); assertTrue(!m.onBlocked("a", "ads.x.com", 1000))
        assertTrue(!m.onBlocked("b", "ads.x.com", 2000)); assertTrue(!m.onBlocked("a", "ads.x.com", 3000))
        assertTrue(m.onBlocked("a", "ads.x.com", 4000))
    }
    @Test fun slowSinglesNeverTrigger() {
        val m = TroubleMonitor()
        for (i in 0 until 10) assertTrue(!m.onBlocked("a", "ads.x.com", i * 30_000L))
    }
    @Test fun doesNotNagAboutTheSameNameOrTooOften() {
        val m = TroubleMonitor(maxPerHour = 2)
        fun burst(d: String, t: Long) = (0 until 4).map { m.onBlocked("a", d, t + it) }.last()
        assertTrue(burst("one.com", 0)); assertTrue(!burst("one.com", 60_000))
        assertTrue(burst("two.com", 70_000)); assertTrue(!burst("three.com", 80_000))
    }
    @Test fun leaveBlockedSilencesForADay() {
        val m = TroubleMonitor(); m.dismiss("ads.x.com", 0)
        assertTrue(!(0 until 4).map { m.onBlocked("a", "ads.x.com", 1000L + it) }.last())
    }
}
