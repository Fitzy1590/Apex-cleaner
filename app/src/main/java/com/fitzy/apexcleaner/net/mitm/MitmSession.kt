package com.fitzy.apexcleaner.net.mitm

import java.io.ByteArrayOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** The app side of one secure connection, as the relay sees it. */
interface MitmPipe {
    /** Next bytes from the app, or null when the app has closed. */
    fun fromApp(): ByteArray?
    /** Sends bytes to the app (waits for the app's window and the pacing gate). */
    fun toApp(b: ByteArray, n: Int)
    /** Everything has been sent; close the app side. */
    fun appDone()
}

/** What the relay needs to decide and set up one secure connection. */
interface MitmGate {
    /** Secure context presenting a certificate for [host], or null to pass the connection through untouched. */
    fun contextFor(host: String): SSLContext?
    val upstreamFactory: SSLSocketFactory
    val restrictAlpn: Boolean
}

/**
 * Terminates the app's secure connection with a local certificate, opens a normal verified secure connection to the real site
 * (system trust, hostname checked), and runs the readable traffic in between through [HttpDirection] filters.
 */
/** Only created when [MitmGate.restrictAlpn] is true, which the gate sets on Android 10+ only. */
@android.annotation.SuppressLint("NewApi")
class MitmSession(private val pipe: MitmPipe, private val plain: Socket, private val host: String, private val serverCtx: SSLContext, private val gate: MitmGate, private val first: ByteArray) {
    private val lock = Any()
    private lateinit var engine: SSLEngine
    private var inBuf = ByteBuffer.allocate(0)
    private var appBuf = ByteBuffer.allocate(0)
    private var netBuf = ByteBuffer.allocate(0)
    @Volatile private var up: SSLSocket? = null

    /** Blocks until the connection ends. Throws if the real site's certificate is not valid (nothing is shown to the app then). */
    fun run() {
        engine = serverCtx.createSSLEngine().apply {
            useClientMode = false
            sslParameters = sslParameters.also { if (gate.restrictAlpn) it.applicationProtocols = arrayOf("http/1.1") }
        }
        val sz = engine.session.packetBufferSize
        inBuf = ByteBuffer.allocate(sz * 2); appBuf = ByteBuffer.allocate(engine.session.applicationBufferSize); netBuf = ByteBuffer.allocate(sz)
        val us = (gate.upstreamFactory.createSocket(plain, host, plain.port, true) as SSLSocket).also { s ->
            s.sslParameters = SSLParameters().also { p -> p.endpointIdentificationAlgorithm = "HTTPS"; p.serverNames = listOf(SNIHostName(host)); if (gate.restrictAlpn) p.applicationProtocols = arrayOf("http/1.1") }
            s.soTimeout = 0; s.startHandshake()
        }
        up = us
        val shared = HttpShared()
        val req = HttpDirection(false, shared, { us.outputStream.write(it); us.outputStream.flush() })
        val resp = HttpDirection(true, shared, { sendPlain(it) })
        val t = Thread({
            try {
                val buf = ByteArray(16384); val ins = us.inputStream
                while (true) { val n = ins.read(buf); if (n < 0) break; resp.feed(buf, n) }
                resp.end()
            } catch (_: Exception) { }
            runCatching { synchronized(lock) { engine.closeOutbound(); flushWrap() } }
            pipe.appDone()
        }, "apex-mitm-down").also { it.isDaemon = true; it.start() }
        try {
            var chunk: ByteArray? = first
            while (chunk != null) {
                val plainIn = ByteArrayOutputStream()
                synchronized(lock) { feedCipher(chunk!!, plainIn) }
                if (plainIn.size() > 0) req.feed(plainIn.toByteArray())
                if (engine.isInboundDone) break
                chunk = pipe.fromApp()
            }
        } finally { runCatching { us.close() }; t.join(2000) }
    }

    /** Must hold [lock]. Decrypts app bytes (and answers handshake steps), putting readable bytes in [plainOut]. */
    private fun feedCipher(b: ByteArray, plainOut: ByteArrayOutputStream) {
        if (inBuf.remaining() < b.size) inBuf = ByteBuffer.allocate(inBuf.position() + b.size + 4096).also { inBuf.flip(); it.put(inBuf) }
        inBuf.put(b); inBuf.flip()
        try {
            var guard = 0
            while (guard++ < 10_000) {
                when (engine.handshakeStatus) {
                    SSLEngineResult.HandshakeStatus.NEED_TASK -> { while (true) { (engine.delegatedTask ?: break).run() }; continue }
                    SSLEngineResult.HandshakeStatus.NEED_WRAP -> { flushWrap(); continue }
                    else -> {}
                }
                if (!inBuf.hasRemaining()) break
                val r = engine.unwrap(inBuf, appBuf)
                when (r.status) {
                    SSLEngineResult.Status.BUFFER_UNDERFLOW -> break
                    SSLEngineResult.Status.BUFFER_OVERFLOW -> { appBuf = ByteBuffer.allocate(appBuf.capacity() * 2).also { appBuf.flip(); it.put(appBuf) }; continue }
                    SSLEngineResult.Status.CLOSED -> { flushWrap(); break }
                    else -> {}
                }
                appBuf.flip(); if (appBuf.hasRemaining()) { val a = ByteArray(appBuf.remaining()); appBuf.get(a); plainOut.write(a) }; appBuf.clear()
            }
        } finally { inBuf.compact() }
    }

    /** Must hold [lock]. Sends whatever handshake or close messages the engine has ready. */
    private fun flushWrap() { wrapFrom(ByteBuffer.allocate(0)) }

    private fun sendPlain(b: ByteArray) { synchronized(lock) { wrapFrom(ByteBuffer.wrap(b)) } }

    private fun wrapFrom(src: ByteBuffer) {
        var guard = 0
        while (guard++ < 100_000) {
            netBuf.clear()
            val r = engine.wrap(src, netBuf)
            if (r.status == SSLEngineResult.Status.BUFFER_OVERFLOW) { netBuf = ByteBuffer.allocate(netBuf.capacity() * 2); continue }
            netBuf.flip()
            if (netBuf.hasRemaining()) { val a = ByteArray(netBuf.remaining()); netBuf.get(a); pipe.toApp(a, a.size) }
            if (r.status == SSLEngineResult.Status.CLOSED) return
            if (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) { while (true) { (engine.delegatedTask ?: break).run() }; continue }
            if (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) continue
            if (!src.hasRemaining()) return
        }
    }
}
