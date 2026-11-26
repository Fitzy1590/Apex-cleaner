package com.fitzy.apexcleaner.net.mitm

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.fitzy.apexcleaner.core.AppSettings
import com.fitzy.apexcleaner.net.FilterEngine
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

/** The CA key is made inside Android's KeyStore and never leaves it; it only signs. */
private class KeyStoreSigner(private val alias: String) : CaSigner {
    private val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    init {
        if (!ks.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1).build())
            }.generateKeyPair()
        }
    }
    override val publicKey: PublicKey get() = ks.getCertificate(alias).publicKey
    override fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withRSA").run { initSign(ks.getKey(alias, null) as java.security.PrivateKey); update(data); sign() }
}

/**
 * Web page cleaner for browsers. Off by default. Needs the user to install the local certificate in Android first.
 * Only browsers on Android 10 or newer are touched; banking, payment, login, government and video-licence names are never opened.
 */
object MitmEngine {
    private const val ALIAS = "apex_local_ca"
    private const val FILE = "AdShield-Root-CA.crt"
    @Volatile private var signer: CaSigner? = null
    @Volatile private var ca: X509Certificate? = null
    private val leafKey: KeyPair by lazy { CertAuthority.newLeafKey() }
    private val contexts = object : LinkedHashMap<String, SSLContext>(64, 0.75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, SSLContext>?) = size > 150 }

    @Synchronized private fun ready(ctx: Context): Pair<CaSigner, X509Certificate> {
        var s = signer; var c = ca
        if (s == null || c == null) {
            s = KeyStoreSigner(ALIAS)
            val f = File(ctx.filesDir, FILE)
            c = runCatching { java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(f.inputStream()) as X509Certificate }.getOrNull()
                ?.takeIf { it.publicKey == s.publicKey }
            if (c == null) { c = CertAuthority.createCa(s); f.writeText(CertAuthority.pem(c)) }
            signer = s; ca = c; contexts.clear()
        }
        return s to c!!
    }

    /** Creates the certificate if needed and returns it as PEM text. */
    fun certificatePem(ctx: Context): String = CertAuthority.pem(ready(ctx).second)

    /** Saves the certificate file into Downloads (Android 10+). Returns false if that failed. */
    @android.annotation.SuppressLint("NewApi")   // the setup screen only offers this on Android 10+
    fun saveToDownloads(ctx: Context): Boolean = runCatching {
        val pem = certificatePem(ctx)
        val v = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, FILE); put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/x-x509-ca-cert")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(pem.toByteArray()) } ?: return false
        true
    }.getOrDefault(false)

    /** True once the user has installed our certificate in Android's security settings. */
    fun isInstalled(ctx: Context): Boolean = runCatching {
        val mine = ready(ctx).second.encoded
        val ks = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        ks.aliases().asSequence().filter { it.startsWith("user:") }.any { (ks.getCertificate(it) as? X509Certificate)?.encoded?.contentEquals(mine) == true }
    }.getOrDefault(false)

    private val sensitiveWords = setOf("login", "signin", "sign-in", "accounts", "account", "auth", "sso", "secure", "pay", "payment", "payments", "checkout", "wallet", "bank", "banking", "id", "identity", "oauth")
    /** Names that are never opened, whatever else is set: banking/payment/essential lists, your Allow list, government, and sign-in or payment style names. */
    fun neverOpen(host: String, engine: FilterEngine?): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (engine == null || engine.isProtected(h)) return true
        val labels = h.split('.')
        if (labels.any { it in sensitiveWords || it.contains("bank") }) return true
        return labels.last() in setOf("gov", "mil") || labels.takeLast(2).firstOrNull() in setOf("gov", "mil")
    }

    /** The gate the relay asks for each secure connection. Null means: leave this connection alone. */
    fun gateFor(ctx: Context, uidIsBrowser: Boolean, port: Int, engine: () -> FilterEngine?): MitmGate? {
        if (!uidIsBrowser || port != 443 || !active(ctx)) return null
        val (s, c) = runCatching { ready(ctx) }.getOrNull() ?: return null
        return object : MitmGate {
            override fun contextFor(host: String): SSLContext? {
                if (neverOpen(host, engine())) return null
                synchronized(contexts) { contexts[host]?.let { return it } }
                val leaf = CertAuthority.createLeaf(host, leafKey, c, s)
                val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setKeyEntry("k", leafKey.private, CharArray(0), arrayOf(leaf, c)) }
                val sc = SSLContext.getInstance("TLS").apply { init(KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, CharArray(0)) }.keyManagers, null, null) }
                synchronized(contexts) { contexts[host] = sc }
                return sc
            }
            override val upstreamFactory: SSLSocketFactory = javax.net.ssl.SSLContext.getDefault().socketFactory
            override val restrictAlpn = true
        }
    }

    /** Browsers known to trust a certificate the user installed (Chrome-family and Samsung Internet). Firefox ignores it by default, so it is left alone. */
    private val trustingBrowsers = listOf("com.android.chrome", "com.chrome.", "com.sec.android.app.sbrowser", "com.microsoft.emmx", "com.brave.browser", "com.vivaldi.browser", "org.chromium.")
    fun isBrowserUid(ctx: Context, uid: Int) = uid >= 0 && runCatching { ctx.packageManager.getPackagesForUid(uid)?.any { p -> trustingBrowsers.any { p.startsWith(it) } } == true }.getOrDefault(false)

    @Volatile private var checkedAt = 0L
    @Volatile private var installedNow = false
    /** [isInstalled] remembered for 30 seconds, for the per-connection checks. */
    fun installedCached(ctx: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - checkedAt > 30_000) { installedNow = isInstalled(ctx); checkedAt = now }
        return installedNow
    }

    /** Looks again right now (used by the setup screen's Check again button). */
    fun recheck(ctx: Context): Boolean { checkedAt = 0; return installedCached(ctx) }

    /** True while the cleaner is switched on AND the certificate is installed, so web pages from browsers are being cleaned. */
    fun active(ctx: Context) = Build.VERSION.SDK_INT >= 29 && AppSettings.httpsCleaner(ctx) && installedCached(ctx)
}
