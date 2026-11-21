package com.fitzy.apexcleaner.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetEngineTest {
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
