package com.fitzy.apexshield.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldEngineTest {
    @Test fun trieBlocksSubdomainsButNotSiblings() {
        val t = DomainTrie().apply { add("ads.example.com", Category.ADS) }
        assertEquals(Category.ADS, t.match("ads.example.com"))
        assertEquals(Category.ADS, t.match("x.y.ads.example.com"))
        assertNull(t.match("example.com"))
        assertNull(t.match("notads.example.com"))
        t.remove("ads.example.com")
        assertNull(t.match("ads.example.com"))
        assertEquals(0, t.size)
    }

    @Test fun passthroughBeatsBlockList() {
        val f = DnsFilter()
        f.block.add("tracker.net"); f.allow.add("bank.tracker.net")
        assertNotNull(f.blockedCategory("a.tracker.net"))
        assertNull(f.blockedCategory("login.bank.tracker.net"))
    }

    @Test fun listParserHandlesHostsFormatAndSections() {
        val r = ShieldLists.parse("# c\n[smart tv]\nsamsungads.com\n0.0.0.0 ad.example.org # x\n127.0.0.1 localhost\n")
        assertEquals(listOf("samsungads.com" to Category.SMART_TV, "ad.example.org" to Category.SMART_TV), r)
    }

    private fun query(name: String, type: Int): ByteArray {
        val dns = ArrayList<Byte>()
        listOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0).forEach { dns += it.toByte() }
        name.split('.').forEach { l -> dns += l.length.toByte(); l.forEach { dns += it.code.toByte() } }
        dns += 0; dns += 0; dns += type.toByte(); dns += 0; dns += 1
        val total = 28 + dns.size
        val p = ByteArray(total)
        p[0] = 0x45; p[2] = (total shr 8).toByte(); p[3] = total.toByte(); p[9] = 17
        byteArrayOf(10, 111, 222.toByte(), 1).copyInto(p, 12); byteArrayOf(10, 111, 222.toByte(), 2).copyInto(p, 16)
        p[20] = 0x9C.toByte(); p[21] = 0x40; p[23] = 53
        val ul = 8 + dns.size; p[24] = (ul shr 8).toByte(); p[25] = ul.toByte()
        dns.toByteArray().copyInto(p, 28)
        return p
    }

    @Test fun parsesQuestionAndBuildsSinkholeReply() {
        val pkt = query("Ads.Example.com", 1)
        val q = DnsPacket.parse(pkt, pkt.size)!!
        assertEquals("ads.example.com", q.name)
        assertEquals(1, q.type)
        val r = DnsPacket.blockedReply(q)
        assertEquals(0x45, r[0].toInt())
        assertEquals(10, r[12].toInt()); assertEquals(2, r[15].toInt())   // from the fake DNS server
        assertEquals(0x9C, r[22].toInt() and 0xFF)                          // back to the asking port
        assertEquals(0x12, r[28].toInt()); assertEquals(0x34, r[29].toInt()) // same query id
        assertEquals(0x81, r[30].toInt() and 0xFF)                          // response flags
        assertEquals(1, r[35].toInt() and 0xFF)                       // one answer
        assertTrue(r.takeLast(4).all { it.toInt() == 0 })                   // 0.0.0.0
    }

    @Test fun ignoresNonDnsAndGarbage() {
        val pkt = query("a.com", 1)
        pkt[23] = 80
        assertNull(DnsPacket.parse(pkt, pkt.size))
        assertNull(DnsPacket.parse(ByteArray(10), 10))
    }
}
