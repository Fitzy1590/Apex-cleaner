package com.fitzy.apexcleaner.net.mitm

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.TimeZone
import java.text.SimpleDateFormat
import java.util.Locale

/** Signs with the CA's private key. On the phone the key lives in Android's KeyStore and never leaves it. */
interface CaSigner {
    val publicKey: PublicKey
    fun sign(data: ByteArray): ByteArray   // SHA256withRSA
}

/** Minimal DER writer, just enough to build the local CA and per-site certificates without any extra library. */
internal object Der {
    fun len(n: Int): ByteArray = when {
        n < 128 -> byteArrayOf(n.toByte())
        n < 256 -> byteArrayOf(0x81.toByte(), n.toByte())
        else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
    }
    fun tlv(tag: Int, v: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + len(v.size) + v
    fun seq(vararg p: ByteArray) = tlv(0x30, p.fold(ByteArray(0)) { a, b -> a + b })
    fun set(vararg p: ByteArray) = tlv(0x31, p.fold(ByteArray(0)) { a, b -> a + b })
    fun int(v: BigInteger) = tlv(0x02, v.toByteArray())
    fun bool(b: Boolean) = tlv(0x01, byteArrayOf(if (b) 0xFF.toByte() else 0))
    fun octets(v: ByteArray) = tlv(0x04, v)
    fun bits(v: ByteArray, unused: Int = 0) = tlv(0x03, byteArrayOf(unused.toByte()) + v)
    fun utf8(s: String) = tlv(0x0C, s.toByteArray())
    fun ia5(s: String, tag: Int = 0x16) = tlv(tag, s.toByteArray(Charsets.US_ASCII))
    val NULL = byteArrayOf(5, 0)
    fun oid(vararg a: Int): ByteArray {
        val o = java.io.ByteArrayOutputStream(); o.write(a[0] * 40 + a[1])
        for (i in 2 until a.size) {
            var v = a[i]; val st = ArrayList<Int>(); st += v and 0x7f; v = v shr 7
            while (v > 0) { st += (v and 0x7f) or 0x80; v = v shr 7 }
            st.reversed().forEach { o.write(it) }
        }
        return tlv(0x06, o.toByteArray())
    }
    fun time(ms: Long): ByteArray {
        val year = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }.get(java.util.Calendar.YEAR)
        val gen = year >= 2050
        val f = SimpleDateFormat(if (gen) "yyyyMMddHHmmss'Z'" else "yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return ia5(f.format(Date(ms)), if (gen) 0x18 else 0x17)
    }
}

/** Builds X.509 certificates: one self-signed local CA, and short-lived certificates for individual sites signed by it. */
object CertAuthority {
    const val CA_NAME = "Apex Shield Local CA (this device only)"
    private val SHA256_RSA = Der.seq(Der.oid(1, 2, 840, 113549, 1, 1, 11), Der.NULL)
    private fun name(cn: String) = Der.seq(Der.set(Der.seq(Der.oid(2, 5, 4, 3), Der.utf8(cn))))
    private fun ext(oid: ByteArray, critical: Boolean, value: ByteArray) = if (critical) Der.seq(oid, Der.bool(true), Der.octets(value)) else Der.seq(oid, Der.octets(value))
    private fun keyId(pub: PublicKey) = MessageDigest.getInstance("SHA-1").digest(pub.encoded)

    private fun assemble(serial: BigInteger, issuer: ByteArray, subject: ByteArray, subjectPub: PublicKey, from: Long, to: Long, exts: ByteArray, signer: CaSigner): X509Certificate {
        val tbs = Der.seq(Der.tlv(0xA0, Der.int(BigInteger.valueOf(2))), Der.int(serial), SHA256_RSA, issuer, Der.seq(Der.time(from), Der.time(to)), subject, subjectPub.encoded, Der.tlv(0xA3, exts))
        val cert = Der.seq(tbs, SHA256_RSA, Der.bits(signer.sign(tbs)))
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(cert)) as X509Certificate
    }

    private fun serial() = BigInteger(127, SecureRandom()).add(BigInteger.ONE)

    fun createCa(signer: CaSigner, now: Long = System.currentTimeMillis()): X509Certificate {
        val n = name(CA_NAME)
        val exts = Der.seq(
            ext(Der.oid(2, 5, 29, 19), true, Der.seq(Der.bool(true))),
            ext(Der.oid(2, 5, 29, 15), true, Der.bits(byteArrayOf(0x06), 1)),
            ext(Der.oid(2, 5, 29, 14), false, Der.octets(keyId(signer.publicKey))))
        return assemble(serial(), n, n, signer.publicKey, now - 86_400_000L, now + 3650L * 86_400_000L, exts, signer)
    }

    /** Certificate for one site name, valid 30 days, signed by the CA. The same [leafKey] is reused for every site. */
    fun createLeaf(host: String, leafKey: KeyPair, ca: X509Certificate, signer: CaSigner, now: Long = System.currentTimeMillis()): X509Certificate {
        val isIp = host.all { it.isDigit() || it == '.' } && host.count { it == '.' } == 3
        val san = if (isIp) Der.tlv(0x87, host.split(".").map { it.toInt().toByte() }.toByteArray()) else Der.tlv(0x82, host.toByteArray(Charsets.US_ASCII))
        val exts = Der.seq(
            ext(Der.oid(2, 5, 29, 19), true, Der.seq()),
            ext(Der.oid(2, 5, 29, 15), true, Der.bits(byteArrayOf(0xA0.toByte()), 5)),
            ext(Der.oid(2, 5, 29, 37), false, Der.seq(Der.oid(1, 3, 6, 1, 5, 5, 7, 3, 1))),
            ext(Der.oid(2, 5, 29, 17), false, Der.seq(san)),
            ext(Der.oid(2, 5, 29, 35), false, Der.seq(Der.tlv(0x80, keyId(signer.publicKey)))))
        return assemble(serial(), ca.subjectX500Principal.encoded, name(host.take(60)), leafKey.public, now - 86_400_000L, now + 30L * 86_400_000L, exts, signer)
    }

    fun newLeafKey(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    fun pem(c: X509Certificate): String = "-----BEGIN CERTIFICATE-----\n" + java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(c.encoded) + "\n-----END CERTIFICATE-----\n"
}
