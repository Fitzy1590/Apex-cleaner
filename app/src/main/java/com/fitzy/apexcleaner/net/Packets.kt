package com.fitzy.apexcleaner.net

/** Plain IPv4 / TCP / UDP packet reading and building (no content inspection). Pure and unit-tested. */
object Packets {
    const val TCP = 6
    const val UDP = 17
    const val FIN = 0x01; const val SYN = 0x02; const val RST = 0x04; const val PSH = 0x08; const val ACK = 0x10

    fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xff
    fun u16(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)
    fun u32(b: ByteArray, i: Int) = (u16(b, i).toLong() shl 16) or u16(b, i + 2).toLong()
    fun put16(b: ByteArray, i: Int, v: Int) { b[i] = (v shr 8).toByte(); b[i + 1] = v.toByte() }
    fun put32(b: ByteArray, i: Int, v: Long) { put16(b, i, (v shr 16).toInt() and 0xffff); put16(b, i + 2, v.toInt() and 0xffff) }

    /** Add two TCP sequence numbers with wrap-around. */
    fun add(seq: Long, n: Int): Long = (seq + n) and 0xffffffffL
    /** Bytes from [from] up to [to], with wrap-around. */
    fun diff(to: Long, from: Long): Long = (to - from) and 0xffffffffL

    fun checksum(b: ByteArray, off: Int, len: Int, initial: Long = 0): Int {
        var sum = initial
        var i = off
        val end = off + len
        while (i + 1 < end) { sum += (u8(b, i) shl 8) or u8(b, i + 1); i += 2 }
        if (i < end) sum += u8(b, i) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xffff) + (sum shr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun pseudo(src: ByteArray, dst: ByteArray, proto: Int, len: Int): Long =
        (u16(src, 0) + u16(src, 2) + u16(dst, 0) + u16(dst, 2) + proto + len).toLong()

    class Header(val ihl: Int, val proto: Int, val src: ByteArray, val dst: ByteArray, val total: Int)

    fun header(p: ByteArray, n: Int = p.size): Header? {
        if (n < 20 || (u8(p, 0) shr 4) != 4) return null
        val ihl = (u8(p, 0) and 15) * 4
        val total = u16(p, 2)
        if (ihl < 20 || total > n || total < ihl) return null
        // Fragments are not handled.
        if (u16(p, 6) and 0x3fff != 0) return null
        return Header(ihl, u8(p, 9), p.copyOfRange(12, 16), p.copyOfRange(16, 20), total)
    }

    class Tcp(val srcPort: Int, val dstPort: Int, val seq: Long, val ack: Long, val flags: Int, val window: Int, val payloadOff: Int, val payloadLen: Int, val mss: Int)

    fun tcp(p: ByteArray, h: Header): Tcp? {
        val o = h.ihl
        if (h.total < o + 20) return null
        val dataOff = (u8(p, o + 12) shr 4) * 4
        if (dataOff < 20 || o + dataOff > h.total) return null
        var mss = 0
        var i = o + 20
        while (i < o + dataOff) {
            val kind = u8(p, i)
            if (kind == 0) break
            if (kind == 1) { i++; continue }
            if (i + 1 >= o + dataOff) break
            val l = u8(p, i + 1)
            if (l < 2) break
            if (kind == 2 && l == 4) mss = u16(p, i + 2)
            i += l
        }
        return Tcp(u16(p, o), u16(p, o + 2), u32(p, o + 4), u32(p, o + 8), u8(p, o + 13), u16(p, o + 14), o + dataOff, h.total - o - dataOff, mss)
    }

    private fun ipHeader(o: ByteArray, total: Int, proto: Int, src: ByteArray, dst: ByteArray) {
        o[0] = 0x45; put16(o, 2, total); o[8] = 64; o[9] = proto.toByte()
        System.arraycopy(src, 0, o, 12, 4); System.arraycopy(dst, 0, o, 16, 4)
        put16(o, 10, checksum(o, 0, 20))
    }

    fun buildTcp(src: ByteArray, dst: ByteArray, sport: Int, dport: Int, seq: Long, ack: Long, flags: Int, window: Int, payload: ByteArray? = null, poff: Int = 0, plen: Int = 0, mss: Int = 0): ByteArray {
        val opt = if (mss > 0) 4 else 0
        val tcpLen = 20 + opt + plen
        val total = 20 + tcpLen
        val o = ByteArray(total)
        ipHeader(o, total, TCP, src, dst)
        put16(o, 20, sport); put16(o, 22, dport); put32(o, 24, seq); put32(o, 28, ack)
        o[32] = (((20 + opt) / 4) shl 4).toByte(); o[33] = flags.toByte(); put16(o, 34, window)
        if (mss > 0) { o[40] = 2; o[41] = 4; put16(o, 42, mss) }
        if (plen > 0 && payload != null) System.arraycopy(payload, poff, o, 40 + opt, plen)
        put16(o, 36, checksum(o, 20, tcpLen, pseudo(src, dst, TCP, tcpLen)))
        return o
    }

    fun buildUdp(src: ByteArray, dst: ByteArray, sport: Int, dport: Int, payload: ByteArray, plen: Int = payload.size): ByteArray {
        val udpLen = 8 + plen
        val total = 20 + udpLen
        val o = ByteArray(total)
        ipHeader(o, total, UDP, src, dst)
        put16(o, 20, sport); put16(o, 22, dport); put16(o, 24, udpLen)
        System.arraycopy(payload, 0, o, 28, plen)
        val c = checksum(o, 20, udpLen, pseudo(src, dst, UDP, udpLen))
        put16(o, 26, if (c == 0) 0xffff else c)
        return o
    }

    /** The "no thanks" reply to a TCP packet, so the app gives up at once instead of waiting for a time-out. Null for packets that need no reply. */
    fun resetFor(p: ByteArray, h: Header): ByteArray? {
        val t = tcp(p, h) ?: return null
        if (t.flags and RST != 0) return null
        return if (t.flags and SYN != 0) buildTcp(h.dst, h.src, t.dstPort, t.srcPort, 0, add(t.seq, 1), RST or ACK, 0)
        else if (t.flags and ACK != 0) buildTcp(h.dst, h.src, t.dstPort, t.srcPort, t.ack, 0, RST, 0)
        else null
    }
}
