package com.fitzy.apexcleaner.net.mitm

/** Reads the website name (SNI) and offered protocols out of the first bytes a browser sends in a secure connection. Never throws. */
object Sni {
    class Hello(val host: String?, val alpn: List<String>)

    /** Null if [d] is not (yet) a complete TLS ClientHello record. */
    fun parse(d: ByteArray, len: Int = d.size): Hello? = try {
        if (len < 5 || d[0].toInt() != 0x16) null else {
            val rec = u16(d, 3); if (len < 5 + rec) null else {
                var i = 5
                if (d[i].toInt() != 1) null else {
                    i += 4 + 2 + 32                       // handshake header, version, random
                    i += 1 + (d[i].toInt() and 0xff)      // session id
                    i += 2 + u16(d, i)                    // cipher suites
                    i += 1 + (d[i].toInt() and 0xff)      // compression
                    val extEnd = i + 2 + u16(d, i); i += 2
                    var host: String? = null; val alpn = ArrayList<String>()
                    while (i + 4 <= extEnd) {
                        val t = u16(d, i); val l = u16(d, i + 2); val s = i + 4
                        if (t == 0 && l >= 5) { val n = u16(d, s + 3); host = String(d, s + 5, n, Charsets.US_ASCII).lowercase() }
                        if (t == 16 && l >= 2) { var p = s + 2; val e = s + 2 + u16(d, s); while (p < e) { val n = d[p].toInt() and 0xff; alpn += String(d, p + 1, n, Charsets.US_ASCII); p += 1 + n } }
                        i = s + l
                    }
                    Hello(host, alpn)
                }
            }
        }
    } catch (_: Exception) { null }

    /** Total size of the first TLS record, or -1 if not enough bytes yet. */
    fun recordSize(d: ByteArray, len: Int): Int = if (len < 5 || d[0].toInt() != 0x16) -1 else 5 + u16(d, 3)

    private fun u16(d: ByteArray, i: Int) = (d[i].toInt() and 0xff shl 8) or (d[i + 1].toInt() and 0xff)
}
