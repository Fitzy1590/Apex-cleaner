package com.fitzy.apexcleaner.net.mitm

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue

/** Method names of requests seen, so the response side knows when a reply has no body (HEAD). Shared by both directions of one connection. */
class HttpShared { val methods = ConcurrentLinkedQueue<String>(); @Volatile var upgraded = false }

/**
 * One direction of an HTTP/1.1 connection. Bytes in through [feed], bytes out through [emit].
 * Requests: asks for uncompressed replies (so pages can be read). Responses: web pages (text/html, uncompressed, up to 3 MB) get the cosmetic
 * rules added; everything else passes through byte for byte. WebSocket and other upgrades switch to plain pass-through.
 */
class HttpDirection(private val isResponse: Boolean, private val shared: HttpShared, private val emit: (ByteArray) -> Unit, private val injectOn: Boolean = isResponse, private val paywall: Boolean = false) {
    private enum class S { HEAD, FIXED, CHUNKED, UNTIL_CLOSE, BUFFER, PASS }
    private var st = S.HEAD
    private val head = ByteArrayOutputStream()
    private var remaining = 0L
    private val chunk = Chunked()
    private var rawBody = ByteArrayOutputStream()
    private var decoded = ByteArrayOutputStream()
    private var pendHead: ByteArray? = null
    private var pendHeaders: List<Pair<String, String>> = emptyList()
    private var bufferChunked = false
    private val rnd = SecureRandom()

    fun feed(b: ByteArray, n: Int = b.size) {
        var off = 0
        while (off < n) {
            when (st) {
                S.PASS, S.UNTIL_CLOSE -> { emit(b.copyOfRange(off, n)); off = n }
                S.HEAD -> {
                    head.write(b, off, n - off); off = n
                    val all = head.toByteArray(); val end = find(all)
                    if (end < 0) { if (all.size > 65536) { st = S.PASS; head.reset(); emit(all) }; continue }
                    head.reset()
                    val rest = all.copyOfRange(end, all.size)
                    onHead(all.copyOfRange(0, end))
                    if (rest.isNotEmpty()) { feed(rest, rest.size) }
                }
                S.FIXED -> { val k = minOf(remaining, (n - off).toLong()).toInt(); emit(b.copyOfRange(off, off + k)); off += k; remaining -= k; if (remaining == 0L) st = S.HEAD }
                S.CHUNKED -> {
                    val used = chunk.feed(b, off, n) { _, _, _ -> }
                    emit(b.copyOfRange(off, off + used)); off += used
                    if (chunk.done) { chunk.reset(); st = S.HEAD }
                }
                S.BUFFER -> {
                    if (bufferChunked) {
                        val used = chunk.feed(b, off, n) { d, o, l -> decoded.write(d, o, l) }
                        rawBody.write(b, off, used); off += used
                        if (decoded.size() > MAX_BODY) { giveUpBuffering(); continue }
                        if (chunk.done) { chunk.reset(); finishBuffer() }
                    } else {
                        val k = minOf(remaining, (n - off).toLong()).toInt(); decoded.write(b, off, k); off += k; remaining -= k
                        if (remaining == 0L) finishBuffer()
                    }
                }
            }
        }
    }

    /** The other side closed. Anything still being buffered goes out unchanged. */
    fun end() { if (st == S.BUFFER) giveUpBuffering() }

    private fun giveUpBuffering() {
        emit(pendHead!!); emit(if (bufferChunked) rawBody.toByteArray() else decoded.toByteArray())
        st = if (bufferChunked && !chunk.done) S.CHUNKED else if (!bufferChunked && remaining > 0) S.FIXED else S.HEAD
        rawBody.reset(); decoded.reset(); pendHead = null
    }

    private fun finishBuffer() {
        val body = decoded.toByteArray(); val nonce = ByteArray(12).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val out = if (CosmeticRulesHolder.enabled) CosmeticRules.inject(body, nonce, if (paywall) PaywallRules.html(nonce) else "") else null
        if (out == null) { emit(pendHead!!); emit(if (bufferChunked) rawBody.toByteArray() else body) }
        else {
            val hs = pendHeaders.filter { !it.first.equals("content-length", true) && !it.first.equals("transfer-encoding", true) }
                .map { if (it.first.equals("content-security-policy", true)) it.first to CosmeticRules.patchCsp(it.second, nonce) else it } + ("Content-Length" to out.size.toString())
            emit(build(statusLine(pendHead!!), hs)); emit(out)
        }
        rawBody.reset(); decoded.reset(); pendHead = null; st = S.HEAD
    }

    private fun onHead(raw: ByteArray) {
        val text = String(raw, Charsets.ISO_8859_1)
        val lines = text.trimEnd('\r', '\n').split("\r\n")
        val first = lines[0]
        val hs = lines.drop(1).filter { it.contains(':') }.map { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        fun h(name: String) = hs.firstOrNull { it.first.equals(name, true) }?.second
        val te = h("transfer-encoding")?.lowercase().orEmpty(); val cl = h("content-length")?.toLongOrNull()
        val upgrade = h("upgrade") != null && (h("connection")?.contains("upgrade", true) == true)
        if (!isResponse) {
            shared.methods.add(first.substringBefore(' ').uppercase())
            if (upgrade) shared.upgraded = true
            val nh = hs.filter { !it.first.equals("accept-encoding", true) }.let { if (paywall) PaywallRules.rewrite(it) else it } + ("Accept-Encoding" to "identity")
            emit(if (upgrade) raw else build(first, nh))
            if (upgrade) { st = S.PASS; return }
            frame(te, cl)
            return
        }
        val code = first.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
        if (code == 101 || (shared.upgraded && code in 200..299 && h("upgrade") != null)) { emit(raw); st = S.PASS; return }
        if (code in 100..199) { emit(raw); return }                      // interim reply; the real one follows
        val method = shared.methods.poll() ?: "GET"
        if (method == "HEAD" || code == 204 || code == 304) { emit(raw); return }
        val html = (h("content-type") ?: "").lowercase().startsWith("text/html")
        val encoded = (h("content-encoding") ?: "identity").lowercase().let { it != "identity" && it.isNotEmpty() }
        val canBuffer = injectOn && html && !encoded && code in 200..299 && (te.contains("chunked") || (cl != null && cl in 0..MAX_BODY))
        if (canBuffer && (cl == null || cl > 0)) {
            pendHead = raw; pendHeaders = hs; decoded.reset(); rawBody.reset(); chunk.reset()
            bufferChunked = te.contains("chunked"); remaining = cl ?: 0; st = S.BUFFER
            return
        }
        emit(raw); frame(te, cl, closeDelimited = true)
    }

    private fun frame(te: String, cl: Long?, closeDelimited: Boolean = false) {
        when {
            te.contains("chunked") -> { chunk.reset(); st = S.CHUNKED }
            cl != null -> if (cl > 0) { remaining = cl; st = S.FIXED } else st = S.HEAD
            closeDelimited -> st = S.UNTIL_CLOSE
            else -> st = S.HEAD
        }
    }

    private fun statusLine(raw: ByteArray) = String(raw, Charsets.ISO_8859_1).substringBefore("\r\n")
    private fun build(first: String, hs: List<Pair<String, String>>): ByteArray =
        (first + "\r\n" + hs.joinToString("") { "${it.first}: ${it.second}\r\n" } + "\r\n").toByteArray(Charsets.ISO_8859_1)

    private fun find(a: ByteArray): Int {
        for (i in 0..a.size - 4) if (a[i] == 13.toByte() && a[i + 1] == 10.toByte() && a[i + 2] == 13.toByte() && a[i + 3] == 10.toByte()) return i + 4
        return -1
    }

    companion object { const val MAX_BODY = 3_000_000L }
}

/** Switch for the page rewriting; off means every page passes through unchanged. */
object CosmeticRulesHolder { @Volatile var enabled = true }

/** Walks chunked-transfer framing. [feed] consumes bytes up to the end of the message (and no further) and hands the decoded data to the callback. */
class Chunked {
    private enum class C { SIZE, DATA, DATA_END, TRAILER }
    private var c = C.SIZE
    private var line = StringBuilder()
    private var left = 0L
    var done = false; private set

    fun reset() { c = C.SIZE; line = StringBuilder(); left = 0; done = false }

    fun feed(b: ByteArray, from: Int, to: Int, data: (ByteArray, Int, Int) -> Unit): Int {
        var i = from
        while (i < to && !done) {
            when (c) {
                C.SIZE -> { val ch = b[i++].toInt().toChar()
                    if (ch == '\n') { val sz = line.toString().trim().substringBefore(';').trim(); line = StringBuilder(); left = sz.toLongOrNull(16) ?: 0; c = if (left == 0L) C.TRAILER else C.DATA } else line.append(ch) }
                C.DATA -> { val k = minOf(left, (to - i).toLong()).toInt(); data(b, i, k); i += k; left -= k; if (left == 0L) c = C.DATA_END }
                C.DATA_END -> { val ch = b[i++].toInt().toChar(); if (ch == '\n') c = C.SIZE }
                C.TRAILER -> { val ch = b[i++].toInt().toChar(); if (ch == '\n') { if (line.isEmpty() || line.toString() == "\r") done = true; line = StringBuilder() } else line.append(ch) }
            }
        }
        return i - from
    }
}
