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

    /** "This name does not exist" (rcode 3): ad kits treat it as a failed ad request and usually collapse the slot. */
    fun nxdomainResponse(query: ByteArray, q: Question): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(query, 0, 2)
        out.write(0x81); out.write(0x83)                         // response, recursion available, name error
        out.write(query, 4, 2)
        out.write(0); out.write(0); out.write(0); out.write(0); out.write(0); out.write(0)
        out.write(query, 12, q.endOffset - 12)
        return out.toByteArray()
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

    /**
     * Every CNAME target in a DNS answer, lower-case. Ad companies often hide behind an innocent-looking name that is
     * really an alias for a tracker; checking the alias targets lets the shield see through that disguise.
     * Safe on damaged data: returns what it could read and never loops.
     */
    fun cnameTargets(d: ByteArray, len: Int = d.size): List<String> {
        val out = ArrayList<String>()
        runCatching {
            if (len < 12) return out
            val qd = u16(d, 4); val an = u16(d, 6)
            var i = 12
            repeat(qd) { i = skipName(d, i, len) + 4 }
            repeat(minOf(an, 32)) {
                i = skipName(d, i, len)
                if (i + 10 > len) return out
                val type = u16(d, i); val rdLen = u16(d, i + 8)
                val rdata = i + 10
                if (rdata + rdLen > len) return out
                if (type == 5) readName(d, rdata, len)?.let { out += it }
                i = rdata + rdLen
            }
        }
        return out
    }

    private fun u16(d: ByteArray, i: Int) = (d[i].toInt() and 0xff shl 8) or (d[i + 1].toInt() and 0xff)

    /** Offset just after the name that starts at [start] (a pointer ends a name after 2 bytes). */
    private fun skipName(d: ByteArray, start: Int, len: Int): Int {
        var i = start
        while (true) {
            if (i >= len) throw IllegalArgumentException("short")
            val l = d[i].toInt() and 0xff
            if (l == 0) return i + 1
            if (l and 0xC0 == 0xC0) return i + 2
            i += 1 + l
        }
    }

    private fun readName(d: ByteArray, start: Int, len: Int): String? {
        val sb = StringBuilder()
        var i = start
        var jumps = 0
        while (true) {
            if (i >= len) return null
            val l = d[i].toInt() and 0xff
            if (l == 0) break
            if (l and 0xC0 == 0xC0) {
                if (i + 1 >= len || ++jumps > 8) return null
                i = (l and 0x3F shl 8) or (d[i + 1].toInt() and 0xff)
                continue
            }
            if (l and 0xC0 != 0 || i + 1 + l > len) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..l) sb.append((d[i + k].toInt() and 0xff).toChar())
            i += 1 + l
        }
        return sb.toString().lowercase()
    }
}
