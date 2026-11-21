package com.fitzy.apexcleaner.net

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.fitzy.apexcleaner.core.ProtectedApps
import com.fitzy.apexcleaner.core.WhitelistStore
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** Live numbers for the screens. */
object ShieldStats {
    val running = MutableStateFlow(false)
    val blocked = MutableStateFlow(0)
}

/**
 * Local DNS firewall. Only DNS lookups are sent into this tunnel (one fake DNS address), so all other
 * traffic goes straight to the network at full speed. Ads and trackers get an empty answer, banking and
 * phone-essential names are passed through untouched, and the rest is resolved over encrypted DNS (DoH)
 * with a plain fallback. This app and banking/protected apps skip the tunnel completely.
 */
class ApexVpnService : VpnService() {
    @Volatile private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private val pool = Executors.newFixedThreadPool(4)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stop(); return START_NOT_STICKY }
        if (!running) start()
        return START_STICKY
    }

    private fun start() {
        val b = Builder().setSession("Apex Shield").addAddress(LOCAL_ADDR, 32)
            .addDnsServer(FAKE_DNS).addRoute(FAKE_DNS, 32).setBlocking(true)
        bypassPackages().forEach { runCatching { b.addDisallowedApplication(it) } }
        tun = b.establish() ?: return
        running = true; ShieldStats.running.value = true
        val engine = FilterEngine.withDefaults()
        Thread({ loop(engine) }, "apex-dns").start()
    }

    private fun bypassPackages(): Set<String> =
        setOf(packageName) + SafeLists.bankingBypassPackages + ProtectedApps.ALWAYS_PROTECTED + WhitelistStore(this).all()

    private fun loop(engine: FilterEngine) {
        val fd = tun ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (running) {
                val n = input.read(buf)
                if (n <= 0) continue
                val pkt = buf.copyOf(n)
                pool.execute { handle(pkt, engine, output) }
            }
        } catch (_: Exception) { /* tunnel closed */ } finally { stop() }
    }

    private fun handle(p: ByteArray, engine: FilterEngine, out: FileOutputStream) {
        if (p.size < 28 || (p[0].toInt() shr 4) != 4 || p[9].toInt() != 17) return
        val ihl = (p[0].toInt() and 15) * 4
        val dport = (p[ihl + 2].toInt() and 0xff shl 8) or (p[ihl + 3].toInt() and 0xff)
        if (dport != 53 || p.size < ihl + 8) return
        val dns = p.copyOfRange(ihl + 8, p.size)
        val q = DnsPacket.parseQuestion(dns) ?: return
        val answer = when (engine.decide(q.name)) {
            FilterEngine.Verdict.BLOCK -> { ShieldStats.blocked.value++; DnsPacket.blockedResponse(dns, dns.size, q) }
            else -> resolve(dns)
        } ?: return
        synchronized(out) { out.write(wrap(p, ihl, answer)) }
    }

    /** Encrypted lookup first; plain UDP only if the encrypted one fails. */
    private fun resolve(query: ByteArray): ByteArray? = doh(query) ?: udp(query)

    private fun doh(query: ByteArray): ByteArray? = runCatching {
        val c = URL(DOH_URL).openConnection() as HttpsURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 3000; c.readTimeout = 3000
        c.setRequestProperty("Content-Type", "application/dns-message"); c.setRequestProperty("Accept", "application/dns-message")
        c.outputStream.use { it.write(query) }
        if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
    }.getOrNull()

    private fun udp(query: ByteArray): ByteArray? = runCatching {
        DatagramSocket().use { s ->
            s.soTimeout = 3000
            s.send(DatagramPacket(query, query.size, InetAddress.getByName(FALLBACK_DNS), 53))
            val r = DatagramPacket(ByteArray(4096), 4096); s.receive(r); r.data.copyOf(r.length)
        }
    }.getOrNull()

    /** Builds the reply packet: addresses and ports swapped, new IPv4 header checksum, UDP checksum left at 0. */
    private fun wrap(req: ByteArray, ihl: Int, payload: ByteArray): ByteArray {
        val total = 20 + 8 + payload.size
        val o = ByteArray(total)
        o[0] = 0x45; o[2] = (total shr 8).toByte(); o[3] = total.toByte(); o[8] = 64; o[9] = 17
        System.arraycopy(req, 16, o, 12, 4); System.arraycopy(req, 12, o, 16, 4)
        var sum = 0
        for (i in 0 until 20 step 2) sum += (o[i].toInt() and 0xff shl 8) or (o[i + 1].toInt() and 0xff)
        while (sum shr 16 != 0) sum = (sum and 0xffff) + (sum shr 16)
        val ck = sum.inv() and 0xffff; o[10] = (ck shr 8).toByte(); o[11] = ck.toByte()
        System.arraycopy(req, ihl + 2, o, 20, 2); System.arraycopy(req, ihl, o, 22, 2)
        val ul = 8 + payload.size; o[24] = (ul shr 8).toByte(); o[25] = ul.toByte()
        System.arraycopy(payload, 0, o, 28, payload.size)
        return o
    }

    private fun stop() {
        running = false; ShieldStats.running.value = false
        runCatching { tun?.close() }; tun = null
        stopSelf()
    }

    override fun onRevoke() { stop() }
    override fun onDestroy() { running = false; ShieldStats.running.value = false; pool.shutdownNow(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "com.fitzy.apexcleaner.STOP_SHIELD"
        private const val LOCAL_ADDR = "10.111.222.1"
        private const val FAKE_DNS = "10.111.222.2"
        private const val DOH_URL = "https://cloudflare-dns.com/dns-query"
        private const val FALLBACK_DNS = "1.1.1.1"

        fun start(ctx: Context) { ctx.startService(Intent(ctx, ApexVpnService::class.java)) }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, ApexVpnService::class.java).setAction(ACTION_STOP)) }
    }
}
