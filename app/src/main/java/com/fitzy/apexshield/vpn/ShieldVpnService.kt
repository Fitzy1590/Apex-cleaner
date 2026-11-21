package com.fitzy.apexshield.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.fitzy.apexshield.core.WhitelistStore
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Local DNS firewall. Only DNS lookups are routed here (to a made-up server address), so all other
 * traffic never touches this app. Blocked names get a "nowhere" answer; everything else is passed on.
 */
class ShieldVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var pool = Executors.newFixedThreadPool(6)
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutDown()
            return START_NOT_STICKY
        }
        start()
        return START_STICKY
    }

    private fun start() {
        if (running) return
        val filter = ShieldEngine.buildFilter(this)
        val plainDns = ShieldEngine.networkDns(this)
        val b = Builder().setSession("Apex Shield").setBlocking(true)
            .addAddress(CLIENT_IP, 32).addDnsServer(FAKE_DNS).addRoute(FAKE_DNS, 32)
        // This app and the owner's protected apps skip the VPN, so their traffic is never touched.
        val bypass = WhitelistStore(this).all() + packageName
        bypass.forEach { runCatching { b.addDisallowedApplication(it) } }
        val fd = runCatching { b.establish() }.getOrNull()
        if (fd == null) { shutDown(); return }
        tun = fd
        running = true
        pool = Executors.newFixedThreadPool(6)
        ShieldEngine.setRunning(true)
        ShieldSettings(this).enabled = true
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        reader = Thread({
            val buf = ByteArray(32767)
            while (running) {
                val n = runCatching { input.read(buf) }.getOrDefault(-1)
                if (n < 0) break
                val q = DnsPacket.parse(buf, n) ?: continue
                runCatching { pool.execute { answer(q, filter, plainDns, output) } }
            }
        }, "shield-dns").also { it.start() }
    }

    private fun answer(q: DnsQuery, filter: DnsFilter, plainDns: InetAddress, out: FileOutputStream) {
        val cat = filter.blockedCategory(q.name)
        val reply = if (cat != null) {
            DnsPacket.blockedReply(q)
        } else {
            val a = ShieldEngine.resolve(q.dns, ShieldSettings(this).provider, plainDns) ?: return
            DnsPacket.wrap(q, a)
        }
        ShieldEngine.record(this, q.name, cat)
        synchronized(out) { runCatching { out.write(reply) } }
    }

    private fun shutDown() {
        running = false
        reader?.interrupt()
        pool.shutdownNow()
        runCatching { tun?.close() }
        tun = null
        ShieldEngine.setRunning(false)
        stopSelf()
    }

    override fun onRevoke() {
        ShieldSettings(this).enabled = false
        shutDown()
    }

    override fun onDestroy() {
        running = false
        runCatching { tun?.close() }
        ShieldEngine.setRunning(false)
        super.onDestroy()
    }

    companion object {
        private const val CLIENT_IP = "10.111.222.1"
        private const val FAKE_DNS = "10.111.222.2"
        private const val ACTION_STOP = "com.fitzy.apexshield.SHIELD_STOP"

        fun start(ctx: Context) { ctx.startService(Intent(ctx, ShieldVpnService::class.java)) }

        fun stop(ctx: Context) {
            ShieldSettings(ctx).enabled = false
            ctx.startService(Intent(ctx, ShieldVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
