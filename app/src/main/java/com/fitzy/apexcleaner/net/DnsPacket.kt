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
}
