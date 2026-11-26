package com.fitzy.apexcleaner.net.mitm

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

class CertAuthorityTest {
    companion object {
        val caPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val signer = object : CaSigner {
            override val publicKey get() = caPair.public
            override fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withRSA").run { initSign(caPair.private); update(data); sign() }
        }
        val ca = CertAuthority.createCa(signer)
        val leafKey = CertAuthority.newLeafKey()
    }

    @Test fun caIsSelfSignedAndMarkedAsCa() {
        ca.verify(caPair.public)
        assertTrue(ca.basicConstraints >= 0)
        assertEquals(CertAuthority.CA_NAME, ca.subjectX500Principal.name.removePrefix("CN="))
        assertTrue(CertAuthority.pem(ca).startsWith("-----BEGIN CERTIFICATE-----"))
    }

    @Test fun leafIsSignedByCaWithTheSiteName() {
        val leaf = CertAuthority.createLeaf("www.example.com", leafKey, ca, signer)
        leaf.verify(caPair.public)
        assertEquals(-1, leaf.basicConstraints)
        assertTrue(leaf.subjectAlternativeNames.any { it[1] == "www.example.com" })
        assertTrue(leaf.extendedKeyUsage.contains("1.3.6.1.5.5.7.3.1"))
        assertEquals(ca.subjectX500Principal, leaf.issuerX500Principal)
    }

    @Test fun javaAcceptsTheChainForTheRightNameOnly() {
        val leaf = CertAuthority.createLeaf("www.example.com", leafKey, ca, signer)
        val tm = object : javax.net.ssl.X509TrustManager {
            val d = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("ca", ca) }
            val real = javax.net.ssl.TrustManagerFactory.getInstance("PKIX").apply { init(d) }.trustManagers[0] as javax.net.ssl.X509ExtendedTrustManager
            override fun checkClientTrusted(c: Array<java.security.cert.X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<java.security.cert.X509Certificate>, a: String) = real.checkServerTrusted(c, a)
            override fun getAcceptedIssuers() = real.acceptedIssuers
        }
        tm.checkServerTrusted(arrayOf(leaf, ca), "RSA")
        val ip = CertAuthority.createLeaf("10.1.2.3", leafKey, ca, signer)
        assertTrue(ip.subjectAlternativeNames.any { it[0] == 7 })
    }

    @Test fun readsSniAndProtocolsFromARealClientHello() {
        val eng = SSLContext.getDefault().createSSLEngine().apply {
            useClientMode = true
            sslParameters = SSLParameters().also { it.serverNames = listOf(SNIHostName("Shop.Example.com")); it.applicationProtocols = arrayOf("h2", "http/1.1") }
        }
        val out = java.nio.ByteBuffer.allocate(20000)
        eng.beginHandshake(); eng.wrap(java.nio.ByteBuffer.allocate(0), out); out.flip()
        val b = ByteArray(out.remaining()).also { out.get(it) }
        val h = Sni.parse(b)!!
        assertEquals("shop.example.com", h.host); assertEquals(listOf("h2", "http/1.1"), h.alpn)
        assertEquals(b.size, Sni.recordSize(b, b.size))
        assertNull(Sni.parse(b, 20)); assertNull(Sni.parse(ByteArray(10)))
    }
}
