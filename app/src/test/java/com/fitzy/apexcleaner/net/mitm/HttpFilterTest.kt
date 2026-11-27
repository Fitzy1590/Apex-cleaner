package com.fitzy.apexcleaner.net.mitm

import org.junit.Assert.*
import org.junit.Test

class HttpFilterTest {
    private fun run(isResp: Boolean, shared: HttpShared, vararg parts: String, split: Int = 0): String {
        val out = java.io.ByteArrayOutputStream()
        val d = HttpDirection(isResp, shared, { out.write(it) })
        for (p in parts) { val b = p.toByteArray(Charsets.ISO_8859_1); if (split > 0) b.toList().chunked(split).forEach { d.feed(it.toByteArray()) } else d.feed(b) }
        d.end()
        return out.toString("ISO-8859-1")
    }
    private val html = "<html><head><title>x</title></head><body>hello</body></html>"

    @Test fun requestAsksForUncompressedReplies() {
        val s = HttpShared()
        val o = run(false, s, "GET /a HTTP/1.1\r\nHost: e.com\r\nAccept-Encoding: gzip, br\r\n\r\n")
        assertTrue(o.contains("Accept-Encoding: identity")); assertFalse(o.contains("gzip")); assertEquals("GET", s.methods.peek())
    }

    @Test fun requestBodyPassesThroughAndNextRequestIsSeen() {
        val s = HttpShared()
        val o = run(false, s, "POST /p HTTP/1.1\r\nHost: e\r\nContent-Length: 5\r\n\r\nhelloGET /b HTTP/1.1\r\nHost: e\r\n\r\n")
        assertTrue(o.contains("hello")); assertEquals(listOf("POST", "GET"), s.methods.toList())
    }

    @Test fun fixedLengthHtmlGetsInjectedWithNewLength() {
        val s = HttpShared().also { it.methods.add("GET") }
        val o = run(true, s, "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.length}\r\n\r\n$html")
        val body = o.substringAfter("\r\n\r\n")
        assertTrue(body.contains("display:none!important")); assertTrue(body.contains("</head><body>hello"))
        assertEquals(body.length, Regex("Content-Length: (\\d+)").find(o)!!.groupValues[1].toInt())
    }

    @Test fun chunkedHtmlGetsInjectedAndSplitBytesWork() {
        val s = HttpShared().also { it.methods.add("GET") }
        val ch = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nTransfer-Encoding: chunked\r\n\r\n10\r\n${html.take(16)}\r\n${Integer.toHexString(html.length - 16)}\r\n${html.drop(16)}\r\n0\r\n\r\n"
        for (split in listOf(0, 1, 7)) {
            val o = run(true, HttpShared().also { it.methods.add("GET") }, ch, split = split)
            assertTrue("split=$split", o.substringAfter("\r\n\r\n").contains("display:none!important"))
            assertFalse(o.lowercase().contains("transfer-encoding"))
        }
    }

    @Test fun otherContentAndCompressedPagesAreUntouched() {
        val img = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: 4\r\n\r\nabcd"
        assertEquals(img, run(true, HttpShared().also { it.methods.add("GET") }, img))
        val gz = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Encoding: gzip\r\nContent-Length: 4\r\n\r\nabcd"
        assertEquals(gz, run(true, HttpShared().also { it.methods.add("GET") }, gz))
    }

    @Test fun headAnd304HaveNoBodyAndKeepFraming() {
        val s = HttpShared().also { it.methods.add("HEAD"); it.methods.add("GET") }
        val a = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 999\r\n\r\n"
        val b = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nok"
        assertEquals(a + b, run(true, s, a + b))
    }

    @Test fun twoPagesBackToBackBothInjected() {
        val s = HttpShared().also { it.methods.add("GET"); it.methods.add("GET") }
        val one = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${html.length}\r\n\r\n$html"
        val o = run(true, s, one + one)
        assertEquals(2, Regex("display:none!important").findAll(o).count())
    }

    @Test fun upgradeSwitchesToPassThrough() {
        val s = HttpShared()
        run(false, s, "GET /ws HTTP/1.1\r\nHost: e\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n\r\n")
        val r = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n\r\n\u0001\u0002raw"
        assertEquals(r, run(true, s, r))
    }

    @Test fun cspGetsNonceOnlyWhereNeeded() {
        val p = CosmeticRules.patchCsp("default-src 'self'; img-src *; script-src 'self' cdn.x.com", "N1")
        assertTrue(p.contains("script-src 'self' cdn.x.com 'nonce-N1'")); assertTrue(p.contains("default-src 'self' 'nonce-N1'")); assertTrue(p.contains("img-src *;") || p.contains("img-src *"))
        assertFalse(p.contains("img-src * 'nonce"))
        assertEquals("script-src 'none'", CosmeticRules.patchCsp("script-src 'none'", "N1").let { it.substringBefore("; ") })
        assertEquals("img-src *", CosmeticRules.patchCsp("img-src *", "N1"))
        assertEquals("default-src 'self' 'unsafe-inline'", CosmeticRules.patchCsp("default-src 'self' 'unsafe-inline'", "N1"))
    }

    @Test fun injectionFallsBackToBodyOrNothing() {
        assertNotNull(CosmeticRules.inject("<body>x</body>".toByteArray(), "n")); assertNull(CosmeticRules.inject("plain text".toByteArray(), "n"))
        assertTrue(String(CosmeticRules.inject("<HEAD><TITLE>t</TITLE>".toByteArray(), "n")!!).startsWith("<HEAD><style"))
    }

    @Test fun adBlockWallScriptIsInjectedAndNarrow() {
        val out = String(CosmeticRules.inject("<head></head><body></body>".toByteArray(), "n")!!)
        assertTrue(out.contains("ad[\\s-]?block")); assertTrue(out.contains("t.length<600"))
    }
}
