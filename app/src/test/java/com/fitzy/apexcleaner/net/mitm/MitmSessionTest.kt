package com.fitzy.apexcleaner.net.mitm

import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.util.concurrent.LinkedBlockingQueue
import javax.net.ssl.*

/** End to end on the JVM: a fake browser, the local certificate, the session, and a fake real website. */
class MitmSessionTest {
    private fun signerOf(kp: java.security.KeyPair) = object : CaSigner {
        override val publicKey get() = kp.public
        override fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withRSA").run { initSign(kp.private); update(data); sign() }
    }
    private fun trusting(ca: java.security.cert.X509Certificate): SSLContext {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("ca", ca) }
        return SSLContext.getInstance("TLS").apply { init(null, TrustManagerFactory.getInstance("PKIX").apply { init(ks) }.trustManagers, null) }
    }
    private fun serving(leaf: java.security.cert.X509Certificate, ca: java.security.cert.X509Certificate, key: java.security.PrivateKey): SSLContext {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setKeyEntry("k", key, CharArray(0), arrayOf(leaf, ca)) }
        return SSLContext.getInstance("TLS").apply { init(KeyManagerFactory.getInstance("SunX509").apply { init(ks, CharArray(0)) }.keyManagers, null, null) }
    }

    private fun scenario(page: String, contentType: String, upstreamTrustsUs: Boolean = true): String {
        val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val devKp = kpg.generateKeyPair(); val devSigner = signerOf(devKp); val devCa = CertAuthority.createCa(devSigner)
        val webKp = kpg.generateKeyPair(); val webSigner = signerOf(webKp); val webCa = CertAuthority.createCa(webSigner)
        val leafKey = CertAuthority.newLeafKey()
        // Fake real website over TLS, certificate from its own CA.
        val webLeaf = CertAuthority.createLeaf("test.local", leafKey, webCa, webSigner)
        val site = serving(webLeaf, webCa, leafKey.private).serverSocketFactory.createServerSocket(0) as SSLServerSocket
        val siteThread = Thread {
            runCatching {
                site.accept().use { c ->
                    val ins = c.getInputStream(); val sb = StringBuilder()
                    while (!sb.endsWith("\r\n\r\n")) sb.append(ins.read().toChar())
                    assertTrue("must ask for plain pages", sb.contains("Accept-Encoding: identity"))
                    c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nContent-Length: ${page.length}\r\nConnection: close\r\n\r\n$page".toByteArray()); c.getOutputStream().flush()
                    Thread.sleep(300)
                }
            }
        }.also { it.isDaemon = true; it.start() }
        // The relay's view: a local certificate for test.local and a bridge to the browser.
        val bridge = ServerSocket(0)
        val gate = object : MitmGate {
            override fun contextFor(host: String) = serving(CertAuthority.createLeaf(host, leafKey, devCa, devSigner), devCa, leafKey.private)
            override val upstreamFactory: SSLSocketFactory = (if (upstreamTrustsUs) trusting(webCa) else trusting(devCa)).socketFactory
            override val restrictAlpn = true
        }
        val sessThread = Thread {
            runCatching {
                bridge.accept().use { app ->
                    val q = LinkedBlockingQueue<ByteArray>()
                    Thread { runCatching { val b = ByteArray(4096); val i = app.getInputStream(); while (true) { val n = i.read(b); if (n < 0) break; q.put(b.copyOf(n)) } }; q.put(ByteArray(0)) }.also { it.isDaemon = true }.start()
                    val pipe = object : MitmPipe {
                        override fun fromApp(): ByteArray? = q.take().takeIf { it.isNotEmpty() }
                        override fun toApp(b: ByteArray, n: Int) { app.getOutputStream().write(b, 0, n); app.getOutputStream().flush() }
                        override fun appDone() { runCatching { app.shutdownOutput() } }
                    }
                    val first = pipe.fromApp()!!
                    val hello = Sni.parse(first)!!; assertEquals("test.local", hello.host)
                    val up = Socket("127.0.0.1", site.localPort)
                    MitmSession(pipe, up, hello.host!!, gate.contextFor(hello.host!!)!!, gate, first).run()
                }
            }
        }.also { it.isDaemon = true; it.start() }
        // The browser: trusts only the device CA, checks the name.
        val raw = Socket("127.0.0.1", bridge.localPort)
        val browser = trusting(devCa).socketFactory.createSocket(raw, "test.local", bridge.localPort, true) as SSLSocket
        browser.sslParameters = SSLParameters().also { it.endpointIdentificationAlgorithm = "HTTPS"; it.serverNames = listOf(SNIHostName("test.local")) }
        browser.soTimeout = 10_000; browser.startHandshake()
        browser.outputStream.write("GET / HTTP/1.1\r\nHost: test.local\r\nAccept-Encoding: gzip, br\r\n\r\n".toByteArray()); browser.outputStream.flush()
        val resp = browser.inputStream.readBytes().toString(Charsets.ISO_8859_1)
        browser.close(); sessThread.join(3000); siteThread.join(3000)
        return resp
    }

    @Test fun pageGetsCleanedAndBrowserSeesAValidLocalCertificate() {
        val page = "<html><head><title>t</title></head><body>real content</body></html>"
        val r = scenario(page, "text/html")
        assertTrue(r, r.startsWith("HTTP/1.1 200 OK"))
        assertTrue(r.contains("display:none!important")); assertTrue(r.contains("real content"))
        assertEquals(r.substringAfter("\r\n\r\n").length, Regex("Content-Length: (\\d+)").find(r)!!.groupValues[1].toInt())
    }

    @Test fun nonPagesPassThroughByteForByte() {
        val r = scenario("{\"a\":1}", "application/json")
        assertTrue(r, r.endsWith("{\"a\":1}")); assertFalse(r.contains("display:none"))
    }

    @Test fun aSiteWithABadCertificateGivesTheBrowserNothing() {
        val ex = runCatching { scenario("x", "text/html", upstreamTrustsUs = false) }
        assertTrue(ex.isFailure || !ex.getOrThrow().startsWith("HTTP/1.1 200"))
    }
}
