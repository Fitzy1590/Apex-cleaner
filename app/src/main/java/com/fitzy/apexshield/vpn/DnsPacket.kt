package com.fitzy.apexshield.vpn

/** One DNS question read from an IPv4/UDP packet. [dns] is the raw DNS message. */
class DnsQuery(
    val srcIp: ByteArray, val dstIp: ByteArray, val srcPort: Int, val dstPort: Int,
    val name: String, val type: Int, val dns: ByteArray, private val questionEnd: Int,
) {
    internal fun question(): ByteArray = dns.copyOfRange(12, questionEnd)
}

/** Reads DNS questions out of tunnel packets and builds the replies written back to the tunnel. */
object DnsPacket {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    fun parse(p: ByteArray, len: Int): DnsQuery? {
        if (len < 20 || (p[0].toInt() shr 4) != 4 || p[9].toInt() != 17) return null
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (ihl < 20 || len < ihl + 8 + 12) return null
        val dstPort = u16(p, ihl + 2)
        if (dstPort != 53) return null
        val udpLen = u16(p, ihl + 4)
        val end = minOf(len, ihl + udpLen)
        val dns = p.copyOfRange(ihl + 8, end)
        if (dns.size < 13 || (dns[2].toInt() and 0x80) != 0 || u16(dns, 4) < 1) return null
        // Read the first question name (queries never use compression pointers).
        val sb = StringBuilder()
        var i = 12
        while (true) {
            if (i >= dns.size) return null
            val l = dns[i].toInt() and 0xFF
            if (l == 0) { i++; break }
            if (l > 63 || i + 1 + l > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..l) sb.append((dns[i + k].toInt() and 0xFF).toChar())
            i += 1 + l
        }
        if (i + 4 > dns.size) return null
        return DnsQuery(
            p.copyOfRange(12, 16), p.copyOfRange(16, 20), u16(p, ihl), dstPort,
            sb.toString().lowercase(), u16(dns, i), dns, i + 4,
        )
    }

    /** Answer for a blocked name: 0.0.0.0 / :: for address lookups, "does not exist" for the rest. */
    fun blockedReply(q: DnsQuery): ByteArray {
        val question = q.question()
        val addr = if (q.type == TYPE_A) 4 else if (q.type == TYPE_AAAA) 16 else 0
        val out = ArrayList<Byte>()
        fun w16(v: Int) { out += (v shr 8).toByte(); out += v.toByte() }
        out += q.dns[0]; out += q.dns[1]
        w16(if (addr == 0) 0x8183 else 0x8180)
        w16(1); w16(if (addr == 0) 0 else 1); w16(0); w16(0)
        question.forEach { out += it }
        if (addr != 0) {
            w16(0xC00C); w16(q.type); w16(1); w16(0); w16(60); w16(addr)
            repeat(addr) { out += 0 }
        }
        return wrap(q, out.toByteArray())
    }

    /** Puts a DNS answer (from the real DNS server) back into an IP/UDP packet for the asking app. */
    fun wrap(q: DnsQuery, dnsAnswer: ByteArray): ByteArray {
        val total = 28 + dnsAnswer.size
        val o = ByteArray(total)
        o[0] = 0x45; o[2] = (total shr 8).toByte(); o[3] = total.toByte()
        o[8] = 64; o[9] = 17
        q.dstIp.copyInto(o, 12); q.srcIp.copyInto(o, 16)
        var sum = 0
        for (i in 0 until 20 step 2) sum += u16(o, i)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv() and 0xFFFF
        o[10] = (ck shr 8).toByte(); o[11] = ck.toByte()
        o[20] = (q.dstPort shr 8).toByte(); o[21] = q.dstPort.toByte()
        o[22] = (q.srcPort shr 8).toByte(); o[23] = q.srcPort.toByte()
        val ul = 8 + dnsAnswer.size
        o[24] = (ul shr 8).toByte(); o[25] = ul.toByte()
        dnsAnswer.copyInto(o, 28)
        return o
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
}
