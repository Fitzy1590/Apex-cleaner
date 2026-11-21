package com.fitzy.apexcleaner.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class LimiterTest {
    private val app = byteArrayOf(10, 111, 222.toByte(), 1)
    private val srv = byteArrayOf(127, 0, 0, 1)

    @Test fun streamingAppsAreRecognisedByName() {
        listOf("com.google.android.youtube.tv", "com.stremio.one", "com.nuvio.tv", "au.com.kayosports", "Nexus", "com.netflix.ninja", "org.xbmc.kodi")
            .forEach { assertTrue(it, StreamingApps.looksLikeStreaming(it)) }
        assertFalse(StreamingApps.looksLikeStreaming("com.android.vending"))
        assertFalse(StreamingApps.looksLikeStreaming("com.example.filemanager"))
    }

    @Test fun commonBrowsersJoinTheShieldAndAreNotStreamingApps() {
        listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.brave.browser")
            .forEach { assertTrue(it, it in StreamingApps.BROWSERS); assertFalse(it, StreamingApps.looksLikeStreaming(it)) }
    }

    @Test fun otherAppsAreOnlySlowedWhilePriorityAppIsOnScreen() {
        val pri = { p: String -> p == "com.stremio.one" }
        assertEquals(LimiterPolicy.DOWN_WHILE_STREAMING, LimiterPolicy.downRate(true, "com.stremio.one", pri))
        assertEquals(0L, LimiterPolicy.downRate(true, "com.example.game2", pri))
        assertEquals(0L, LimiterPolicy.downRate(true, null, pri))
        assertEquals(0L, LimiterPolicy.downRate(false, "com.stremio.one", pri))
    }

    @Test fun foregroundTrackerFollowsResumeAndPause() {
        val t = ForegroundTracker()
        t.onEvent("a", 1); assertEquals("a", t.current)
        t.onEvent("b", 1); t.onEvent("a", 2); assertEquals("b", t.current)
        t.onEvent("b", 2); assertNull(t.current)
    }

    @Test fun trackerKnowsEveryAppOnScreenForSplitScreen() {
        val t = ForegroundTracker()
        t.onEvent("video", 1); t.onEvent("chrome", 1)
        assertEquals(setOf("video", "chrome"), t.visible)
        t.onEvent("video", 2); assertEquals(setOf("chrome"), t.visible)
    }

    @Test fun appOnScreenIsNeverPaced() {
        val g = PaceGate(); g.setRate(1000); g.waitMs(10)
        val host = object : RelayHost {
            override fun toApp(packet: ByteArray) {}
            override fun keepOutside(s: Socket) = true
            override fun keepOutside(s: DatagramSocket) = true
            override val down = g; override val up = g; override val pool = Executors.newSingleThreadExecutor()
            override fun uidOnScreen(uid: Int) = uid == 7
        }
        val t0 = System.nanoTime(); host.pace(g, 7, 500_000)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 200)
    }

    @Test fun gateWaitsInsteadOfDropping() {
        val g = PaceGate(); assertEquals(0L, g.waitMs(1_000_000))
        g.setRate(1000); assertEquals(0L, g.waitMs(10)); assertTrue(g.waitMs(200_000) > 1000)
        g.setRate(0); assertEquals(0L, g.waitMs(1_000_000))
    }

    @Test fun builtPacketsReadBackAndChecksumsHold() {
        val p = Packets.buildTcp(app, srv, 40000, 443, 0xfffffff0L, 5, Packets.SYN or Packets.ACK, 65535, byteArrayOf(1, 2, 3), 0, 3, mss = 1400)
        val h = Packets.header(p)!!
        val t = Packets.tcp(p, h)!!
        assertEquals(40000, t.srcPort); assertEquals(443, t.dstPort); assertEquals(0xfffffff0L, t.seq); assertEquals(1400, t.mss); assertEquals(3, t.payloadLen)
        assertEquals(0, Packets.checksum(p, 0, 20))
        val len = h.total - 20
        val pseudo = (Packets.u16(app, 0) + Packets.u16(app, 2) + Packets.u16(srv, 0) + Packets.u16(srv, 2) + 6 + len).toLong()
        assertEquals(0, Packets.checksum(p, 20, len, pseudo))
        assertEquals(0x10L, Packets.add(0xfffffff0L, 32)); assertEquals(32L, Packets.diff(0x10L, 0xfffffff0L))
        val u = Packets.buildUdp(app, srv, 5000, 9999, byteArrayOf(9, 9))
        assertEquals(17, Packets.header(u)!!.proto)
    }

    /** End to end on this computer: an "app" talks to the relay, the relay talks to a real echo server. */
    @Test fun tcpRelayCarriesDataBothWays() {
        val server = ServerSocket(0)
        val pool = Executors.newCachedThreadPool()
        pool.execute { server.accept().use { s -> val b = ByteArray(100); val n = s.getInputStream().read(b); s.getOutputStream().write(b, 0, n); s.getOutputStream().flush(); Thread.sleep(300) } }
        val back = LinkedBlockingQueue<ByteArray>()
        val host = object : RelayHost {
            override fun toApp(packet: ByteArray) { back.put(packet) }
            override fun keepOutside(s: Socket) = true
            override fun keepOutside(s: DatagramSocket) = true
            override val down = PaceGate(); override val up = PaceGate(); override val pool = pool
        }
        val table = FlowTable(host)
        val port = server.localPort
        fun send(p: ByteArray) { table.onPacket(p, Packets.header(p)!!) }
        fun next(): Packets.Tcp { val p = back.poll(5, TimeUnit.SECONDS)!!; return Packets.tcp(p, Packets.header(p)!!)!!.let { t -> TcpView.last = p; t } }
        send(Packets.buildTcp(app, srv, 40001, port, 1000, 0, Packets.SYN, 65535))
        val synAck = next()
        assertEquals(Packets.SYN or Packets.ACK, synAck.flags); assertEquals(1001L, synAck.ack)
        val ourSeq = Packets.add(synAck.seq, 1)
        send(Packets.buildTcp(app, srv, 40001, port, 1001, ourSeq, Packets.ACK, 65535))
        send(Packets.buildTcp(app, srv, 40001, port, 1001, ourSeq, Packets.ACK or Packets.PSH, 65535, "hello".toByteArray(), 0, 5))
        var got = ""
        val end = System.currentTimeMillis() + 5000
        while (!got.contains("hello") && System.currentTimeMillis() < end) {
            val t = next()
            if (t.payloadLen > 0) got += String(TcpView.last, t.payloadOff, t.payloadLen)
        }
        assertEquals("hello", got)
        table.closeAll(); server.close(); pool.shutdownNow()
        assertNotNull(table)
    }

    private object TcpView { @Volatile var last = ByteArray(0) }
}
