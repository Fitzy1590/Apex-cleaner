package com.fitzy.apexcleaner.net

/** Tiny DNS message helper: read the asked name and build a "blocked" answer. */
object DnsPacket {
    data class Question(val name: String, val type: Int, val endOffset: Int)

    fun parseQuestion(d: ByteArray, len: Int = d.size): Question? {
        if (len < 17) return null
        if ((d[2].toInt() and 0x80) != 0) return null // a response, not a query
        if (((d[4].toInt() and 0xff) shl 8 or (d[5].toInt() and 0xff)) < 1) return null
        var i = 12
        val sb = StringBuilder()
        while (true) {
            if (i >= len) return null
            val l = d[i].toInt() and 0xff
            if (l == 0) { i++; break }
            if (l and 0xC0 != 0 || i + 1 + l > len) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..l) sb.append((d[i + k].toInt() and 0xff).toChar())
            i += 1 + l
        }
        if (i + 4 > len) return null
        val type = (d[i].toInt() and 0xff) shl 8 or (d[i + 1].toInt() and 0xff)
        return Question(sb.toString().lowercase(), type, i + 4)
    }

    /** Answers 0.0.0.0 (A) or :: (AAAA) with a short lifetime; other types get an empty answer. */
    fun blockedResponse(query: ByteArray, len: Int, q: Question): ByteArray {
        val addr = when (q.type) { 1 -> ByteArray(4); 28 -> ByteArray(16); else -> null }
        val out = java.io.ByteArrayOutputStream()
        out.write(query, 0, 2)                                   // id
        out.write(0x81); out.write(0x80)                         // response, recursion available, no error
        out.write(query, 4, 2)                                   // question count
        out.write(0); out.write(if (addr != null) 1 else 0)      // answer count
        out.write(0); out.write(0); out.write(0); out.write(0)
        out.write(query, 12, q.endOffset - 12)                   // the question, copied back
        if (addr != null) {
            out.write(0xC0); out.write(0x0C)                     // name pointer
            out.write(0); out.write(q.type)
            out.write(0); out.write(1)                           // class IN
            out.write(0); out.write(0); out.write(0); out.write(30) // 30 s lifetime
            out.write(0); out.write(addr.size)
            out.write(addr)
        }
        return out.toByteArray()
    }

    /** Reads a (possibly compressed) name starting at [start]; returns the name and the offset just after it in place. */
    private fun readName(d: ByteArray, len: Int, start: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var i = start
        var next = -1
        var hops = 0
        while (true) {
            if (i >= len) return null
            val l = d[i].toInt() and 0xff
            if (l == 0) { i++; break }
            if (l and 0xC0 == 0xC0) {
                if (i + 1 >= len || ++hops > 10) return null
                if (next < 0) next = i + 2
                i = ((l and 0x3F) shl 8) or (d[i + 1].toInt() and 0xff)
                continue
            }
            if (l and 0xC0 != 0 || i + 1 + l > len) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..l) sb.append((d[i + k].toInt() and 0xff).toChar())
            i += 1 + l
        }
        return sb.toString().lowercase() to (if (next >= 0) next else i)
    }

    /**
     * Every alias (CNAME) name a DNS answer points to. Used to catch ad servers hiding behind innocent-looking names
     * ("CNAME cloaking"): a lookup for a harmless name that is really an alias of an ad network.
     */
    fun cnameTargets(r: ByteArray, len: Int = r.size): List<String> {
        if (len < 12 || (r[2].toInt() and 0x80) == 0) return emptyList()
        val qd = Packets.u16(r, 4); val an = Packets.u16(r, 6)
        var i = 12
        repeat(qd) { i = (readName(r, len, i) ?: return emptyList()).second + 4 }
        val out = ArrayList<String>()
        repeat(an) {
            i = (readName(r, len, i) ?: return out).second
            if (i + 10 > len) return out
            val type = Packets.u16(r, i); val rdLen = Packets.u16(r, i + 8)
            val rd = i + 10
            if (rd + rdLen > len) return out
            if (type == 5) readName(r, len, rd)?.first?.let { out += it }
            i = rd + rdLen
        }
        return out
    }
}
