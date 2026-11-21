package com.fitzy.apexcleaner.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue

/** What a relay needs from the VPN service: write a packet back to the app, and keep our own sockets outside the tunnel. */
interface RelayHost {
    fun toApp(packet: ByteArray)
    fun keepOutside(s: Socket): Boolean
    fun keepOutside(s: DatagramSocket): Boolean
    val down: PaceGate
    val up: PaceGate
    val pool: Executor
}

/**
 * Carries one app connection to the real internet. The app talks to us as if we were the server; we open the real
 * connection (outside the tunnel) and copy bytes both ways. Slowing is done by waiting before copying (never by
 * dropping), so the app's own connection simply slows down. No content is read or changed.
 */
class TcpFlow(private val host: RelayHost, private val appIp: ByteArray, private val serverIp: ByteArray, private val appPort: Int, private val serverPort: Int) {
    private enum class St { NEW, CONNECTING, OPEN, CLOSED }

    @Volatile private var st = St.NEW
    @Volatile var lastUse = System.currentTimeMillis(); private set
    private var sock: Socket? = null
    private var clientIsn = 0L
    private var expected = 0L        // next app byte we want
    private var ourIsn = (System.nanoTime() and 0x7fffffffL)
    private var ourSeq = 0L          // next byte we send to the app
    private var appAck = 0L          // how far the app has confirmed
    private var appWin = 65535
    private var appMss = 1400
    private var finSent = false
    private var finGot = false
    private var queued = 0
    private val out = LinkedBlockingQueue<ByteArray>()

    val closed get() = st == St.CLOSED

    @Synchronized fun onSegment(p: ByteArray, h: Packets.Header, t: Packets.Tcp) {
        lastUse = System.currentTimeMillis()
        if (t.flags and Packets.RST != 0) { close(); return }
        if (t.flags and Packets.SYN != 0) {
            if (st == St.NEW) {
                clientIsn = t.seq; expected = Packets.add(t.seq, 1)
                appMss = if (t.mss in 536..1460) minOf(t.mss, 1400) else 1400
                appWin = t.window; st = St.CONNECTING
                host.pool.execute { connect() }
            } else if (st == St.OPEN) send(ourIsn, expected, Packets.SYN or Packets.ACK, mss = 1400)
            return
        }
        if (st != St.OPEN) return
        if (t.flags and Packets.ACK != 0) { appAck = t.ack; appWin = t.window; (this as Object).notifyAll() }
        var fin = t.flags and Packets.FIN != 0
        if (t.payloadLen > 0) {
            if (t.seq == expected && queued + t.payloadLen <= MAX_QUEUE) {
                out.put(p.copyOfRange(t.payloadOff, t.payloadOff + t.payloadLen))
                queued += t.payloadLen; expected = Packets.add(expected, t.payloadLen)
                send(ourSeq, expected, Packets.ACK)
            } else { send(ourSeq, expected, Packets.ACK); fin = false }   // out of order or full: ask the app to resend
        }
        if (fin && !finGot && Packets.add(t.seq, t.payloadLen) == expected) {
            finGot = true; expected = Packets.add(expected, 1); out.put(FIN_MARK); send(ourSeq, expected, Packets.ACK)
        }
        if (finGot && finSent) scheduleClose()
    }

    private fun connect() {
        try {
            val s = Socket()
            host.keepOutside(s)
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(InetAddress.getByAddress(serverIp), serverPort), 6000)
            synchronized(this) {
                if (st == St.CLOSED) { s.close(); return }
                sock = s; ourSeq = Packets.add(ourIsn, 1); appAck = ourSeq; st = St.OPEN
                send(ourIsn, expected, Packets.SYN or Packets.ACK, mss = 1400)
            }
            host.pool.execute { writer(s) }
            reader(s)
        } catch (_: Exception) {
            synchronized(this) { if (st != St.CLOSED) { send(0, expected, Packets.RST or Packets.ACK); close() } }
        }
    }

    /** App to internet. */
    private fun writer(s: Socket) {
        try {
            val os = s.getOutputStream()
            while (st != St.CLOSED) {
                val chunk = out.take()
                if (chunk === FIN_MARK) { runCatching { s.shutdownOutput() }; break }
                val w = host.up.waitMs(chunk.size); if (w > 0) Thread.sleep(w)
                os.write(chunk); os.flush()
                synchronized(this) { queued -= chunk.size }
            }
        } catch (_: Exception) { close() }
    }

    /** Internet to app. Waits for the app's window and for the pacing gate, so nothing is dropped. */
    private fun reader(s: Socket) {
        try {
            val ins = s.getInputStream()
            val buf = ByteArray(appMss)
            while (st != St.CLOSED) {
                synchronized(this) {
                    while (st != St.CLOSED && Packets.diff(ourSeq, appAck) >= minOf(appWin, MAX_WINDOW)) (this as Object).wait(50)
                }
                if (st == St.CLOSED) return
                val n = ins.read(buf)
                if (n < 0) break
                val w = host.down.waitMs(n); if (w > 0) Thread.sleep(w)
                synchronized(this) {
                    if (st == St.CLOSED) return
                    send(ourSeq, expected, Packets.ACK or Packets.PSH, buf, n)
                    ourSeq = Packets.add(ourSeq, n)
                }
            }
            synchronized(this) {
                if (st == St.CLOSED) return
                send(ourSeq, expected, Packets.FIN or Packets.ACK); ourSeq = Packets.add(ourSeq, 1); finSent = true
                if (finGot) scheduleClose()
            }
        } catch (_: Exception) { synchronized(this) { if (st != St.CLOSED) { send(ourSeq, expected, Packets.RST or Packets.ACK); close() } } }
    }

    private fun send(seq: Long, ack: Long, flags: Int, data: ByteArray? = null, len: Int = 0, mss: Int = 0) =
        host.toApp(Packets.buildTcp(serverIp, appIp, serverPort, appPort, seq, ack, flags, 65535, data, 0, len, mss))

    private fun scheduleClose() { host.pool.execute { Thread.sleep(2000); close() } }

    @Synchronized fun close() {
        if (st == St.CLOSED) return
        st = St.CLOSED; runCatching { sock?.close() }; out.offer(FIN_MARK); (this as Object).notifyAll()
    }

    private companion object {
        val FIN_MARK = ByteArray(0)
        const val MAX_QUEUE = 256 * 1024
        const val MAX_WINDOW = 65535
    }
}

/** One app's UDP conversation (games, calls). DNS (port 53) is never slowed. */
class UdpFlow(private val host: RelayHost, private val appIp: ByteArray, private val serverIp: ByteArray, private val appPort: Int, private val serverPort: Int) {
    @Volatile var lastUse = System.currentTimeMillis(); private set
    @Volatile var closed = false; private set
    private val sock = DatagramSocket()

    init {
        host.keepOutside(sock)
        runCatching { sock.connect(InetAddress.getByAddress(serverIp), serverPort) }
        sock.soTimeout = 1000
        host.pool.execute { receive() }
    }

    fun toServer(payload: ByteArray) {
        lastUse = System.currentTimeMillis()
        runCatching { sock.send(DatagramPacket(payload, payload.size)) }
    }

    private fun receive() {
        val buf = ByteArray(65535)
        try {
            while (!closed && System.currentTimeMillis() - lastUse < IDLE_MS) {
                val pk = DatagramPacket(buf, buf.size)
                try { sock.receive(pk) } catch (_: java.net.SocketTimeoutException) { continue }
                lastUse = System.currentTimeMillis()
                if (serverPort != 53) { val w = host.down.waitMs(pk.length); if (w > 0) Thread.sleep(w) }
                host.toApp(Packets.buildUdp(serverIp, appIp, serverPort, appPort, buf, pk.length))
            }
        } catch (_: Exception) { } finally { close() }
    }

    fun close() { closed = true; runCatching { sock.close() } }
    private companion object { const val IDLE_MS = 60_000L }
}

/** All live conversations, found by app port + server address. Idle ones are swept away. */
class FlowTable(private val host: RelayHost) {
    private val tcp = ConcurrentHashMap<String, TcpFlow>()
    private val udp = ConcurrentHashMap<String, UdpFlow>()

    private fun key(proto: Int, port: Int, dst: ByteArray, dport: Int) = "$proto:$port:${dst.joinToString(".") { (it.toInt() and 0xff).toString() }}:$dport"

    fun onPacket(p: ByteArray, h: Packets.Header) {
        when (h.proto) {
            Packets.TCP -> {
                val t = Packets.tcp(p, h) ?: return
                val k = key(6, t.srcPort, h.dst, t.dstPort)
                val f = tcp[k]?.takeIf { !it.closed } ?: if (t.flags and Packets.SYN != 0 && t.flags and Packets.ACK == 0) TcpFlow(host, h.src, h.dst, t.srcPort, t.dstPort).also { tcp[k] = it } else return
                f.onSegment(p, h, t)
            }
            Packets.UDP -> {
                if (h.total < h.ihl + 8) return
                val sp = Packets.u16(p, h.ihl); val dp = Packets.u16(p, h.ihl + 2)
                val k = key(17, sp, h.dst, dp)
                val f = udp[k]?.takeIf { !it.closed } ?: UdpFlow(host, h.src, h.dst, sp, dp).also { udp[k] = it }
                f.toServer(p.copyOfRange(h.ihl + 8, h.total))
            }
        }
    }

    fun sweep() {
        val now = System.currentTimeMillis()
        tcp.entries.removeIf { (_, f) -> (f.closed || now - f.lastUse > 120_000).also { dead -> if (dead) f.close() } }
        udp.entries.removeIf { (_, f) -> f.closed }
    }

    fun closeAll() { tcp.values.forEach { it.close() }; udp.values.forEach { it.close() }; tcp.clear(); udp.clear() }
}
