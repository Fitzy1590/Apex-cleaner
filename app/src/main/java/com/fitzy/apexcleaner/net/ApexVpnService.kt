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
    /** Set when the shield could not start, so the screen can say so instead of the switch silently staying off. */
    val failure = MutableStateFlow<String?>(null)
    val blocked = MutableStateFlow(0)
    /** Newest blocked names first, for the network log. */
    val recent = MutableStateFlow<List<String>>(emptyList())
    /** True while the speed limiter's full tunnel is up (limited apps go through it). */
    val limiterOn = MutableStateFlow(false)
    /** True while a priority app is on screen and other apps are being slowed. */
    val limiting = MutableStateFlow(false)
    /** How many names the loaded ad lists hold (0 until they finish loading) and how many lookups were seen since start. */
    val listNames = MutableStateFlow(0)
    val lookups = MutableStateFlow(0)
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
    private val relayPool = Executors.newCachedThreadPool()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    /** Starting the shield (reading the app list, ad lists, opening the tunnel) never runs on the screen thread. */
    private val starter = Executors.newSingleThreadExecutor()
    private val life = Any()
    @Volatile private var tunOut: FileOutputStream? = null
    private val down = PaceGate()
    private val up = PaceGate()
    @Volatile private var flows: FlowTable? = null
    @Volatile private var priority: Set<String> = emptySet()
    @Volatile private var visibleUids: Set<Int> = emptySet()
    /** Streaming, sport and game apps in the tunnel: never paced, even in the background. */
    @Volatile private var priorityUids: Set<Int> = emptySet()
    private val tracker = ForegroundTracker()
    private var lastEvents = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stop(); return START_NOT_STICKY }
        runCatching { starter.execute { synchronized(life) { if (!running) runCatching { start() }.onFailure { ShieldStats.failure.value = FAIL_TEXT; stop() } } } }
        return START_STICKY
    }

    private fun start() {
        val wantLimiter = com.fitzy.apexcleaner.core.AppSettings.limiter(this)
        val limited = if (wantLimiter) runCatching { StreamingApps.limitedApps(this) }.getOrDefault(emptySet()) else emptySet()
        // Limiter mode: only the limited apps enter the tunnel (all their traffic, so it can be paced).
        // Priority apps, banking, push and system apps stay outside it and run at full speed.
        val full = limited.isNotEmpty()
        val b = Builder().setSession("Apex Shield").addAddress(LOCAL_ADDR, 32).addDnsServer(FAKE_DNS).setBlocking(true).setMtu(1500)
        if (full) {
            b.addRoute("0.0.0.0", 0)
            limited.forEach { runCatching { b.addAllowedApplication(it) } }
        } else {
            b.addRoute(FAKE_DNS, 32)
            // Public lookup servers some apps call by address: their traffic comes here so plain lookups are filtered and encrypted ones are turned away.
            if (com.fitzy.apexcleaner.core.AppSettings.blockSidestep(this)) SidestepGuard.publicResolverAddresses.forEach { runCatching { b.addRoute(it, 32) } }
            bypassPackages().forEach { runCatching { b.addDisallowedApplication(it) } }
        }
        ShieldStats.failure.value = null
        tun = b.establish() ?: run { ShieldStats.failure.value = FAIL_TEXT; stopSelf(); return }
        tunOut = FileOutputStream(tun!!.fileDescriptor)
        running = true; ShieldStats.running.value = true; ShieldStats.limiterOn.value = full
        ShieldRules.ensureLoaded(this)
        // Start with the small built-in list so lookups work at once; the big lists are swapped in below.
        val engine = FilterEngine.withDefaults()
        ShieldRules.apply(this, engine); ShieldRules.live = engine
        AdBlock.ensureLoaded(this)
        com.fitzy.apexcleaner.core.Notifier.refresh(this, force = true)
        // Keeps the "ads blocked today" card up to date and shows when a pause ends.
        timer.scheduleWithFixedDelay({ runCatching { com.fitzy.apexcleaner.core.Notifier.refresh(this) } }, 15, 15, java.util.concurrent.TimeUnit.SECONDS)
        if (full) startLimiter()
        // Big ad lists load in the background (and download first if this is the first run).
        Thread({
            runCatching { if (!FilterLists.hasLists(this)) FilterLists.update(this) }
            runCatching { ShieldRules.reloadLists(this); ShieldRules.apply(this, ShieldRules.live); ShieldStats.listNames.value = ShieldRules.live?.blocks?.size ?: 0 }
            // Lists older than a week refresh by themselves, but only on an unmetered connection.
            runCatching {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                if (FilterLists.isStale(this) && !cm.isActiveNetworkMetered && FilterLists.update(this) > 0) {
                    ShieldRules.reloadLists(this); ShieldRules.apply(this, ShieldRules.live); ShieldStats.listNames.value = ShieldRules.live?.blocks?.size ?: 0
                }
            }
        }, "apex-lists").start()
        Thread({ loop(engine, full) }, "apex-dns").start()
    }

    private fun startLimiter() {
        val host = object : RelayHost {
            override fun toApp(packet: ByteArray) { TrafficLog.addPacket(packet, packet.size); tunOut?.let { o -> synchronized(o) { runCatching { o.write(packet) } } } }
            override fun keepOutside(s: java.net.Socket) = protect(s)
            override fun keepOutside(s: DatagramSocket) = protect(s)
            override val down get() = this@ApexVpnService.down
            override val up get() = this@ApexVpnService.up
            override val pool get() = relayPool
            override fun ownerUid(proto: Int, appIp: ByteArray, appPort: Int, serverIp: ByteArray, serverPort: Int): Int =
                if (android.os.Build.VERSION.SDK_INT < 29) -1 else runCatching {
                    val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    cm.getConnectionOwnerUid(if (proto == 6) 6 else 17,
                        java.net.InetSocketAddress(InetAddress.getByAddress(appIp), appPort), java.net.InetSocketAddress(InetAddress.getByAddress(serverIp), serverPort))
                }.getOrDefault(-1)
            override fun uidOnScreen(uid: Int) = uid >= 0 && (uid in visibleUids || uid in priorityUids)
            override fun mitm(uid: Int, serverPort: Int) = com.fitzy.apexcleaner.net.mitm.MitmEngine.gateFor(this@ApexVpnService,
                com.fitzy.apexcleaner.net.mitm.MitmEngine.isBrowserUid(this@ApexVpnService, uid), serverPort) { ShieldRules.live }
            override fun refuseQuic(uid: Int) = com.fitzy.apexcleaner.net.mitm.MitmEngine.active(this@ApexVpnService) && com.fitzy.apexcleaner.net.mitm.MitmEngine.isBrowserUid(this@ApexVpnService, uid)
            override fun flowDone(proto: Int, uid: Int, serverIp: ByteArray, serverPort: Int, up: Long, down: Long) {
                if (!TrafficLog.recording || (up == 0L && down == 0L)) return
                TrafficLog.addFlow(FlowEntry(System.currentTimeMillis(), appLabel(uid), if (proto == 6) "TCP" else "UDP", TrafficLog.nameFor(serverIp), serverPort, up, down, false))
            }
        }
        flows = FlowTable(host)
        priority = runCatching { StreamingApps.priorityApps(this) }.getOrDefault(emptySet())
        lastEvents = System.currentTimeMillis() - 3_600_000L
        var tick = 0
        timer.scheduleWithFixedDelay({
            runCatching {
                pollForeground()
                val enabled = com.fitzy.apexcleaner.core.AppSettings.limiter(this)
                val fg = tracker.visible.firstOrNull { it in priority || StreamingApps.looksLikeStreaming(it) } ?: tracker.current
                visibleUids = tracker.visible.mapNotNull { runCatching { packageManager.getPackageUid(it, 0) }.getOrNull() }.toSet()
                priorityUids = priority.mapNotNull { runCatching { packageManager.getPackageUid(it, 0) }.getOrNull() }.toSet()
                val isPri = { p: String -> p in priority || StreamingApps.looksLikeStreaming(p) }
                down.setRate(LimiterPolicy.downRate(enabled, fg, isPri)); up.setRate(LimiterPolicy.upRate(enabled, fg, isPri))
                ShieldStats.limiting.value = down.rate > 0
                if (++tick % 10 == 0) flows?.sweep()
                if (tick % 100 == 0) priority = StreamingApps.priorityApps(this)
            }
        }, 1, 3, java.util.concurrent.TimeUnit.SECONDS)
    }

    /** Which app is on screen now, from Android's usage log. Needs Usage Access; without it nothing is slowed. */
    private fun pollForeground() {
        val um = getSystemService(USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = um.queryEvents(lastEvents, now); lastEvents = now
        val e = android.app.usage.UsageEvents.Event()
        while (ev.hasNextEvent()) { ev.getNextEvent(e); tracker.onEvent(e.packageName, e.eventType) }
    }

    private fun bypassPackages(): Set<String> =
        setOf(packageName) + SafeLists.bankingBypassPackages + ProtectedApps.ALWAYS_PROTECTED + WhitelistStore(this).all()

    private fun loop(engine: FilterEngine, full: Boolean) {
        val fd = tun ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = tunOut ?: FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (running) {
                val n = input.read(buf)
                if (n <= 0) continue
                TrafficLog.addPacket(buf, n)
                val pkt = buf.copyOf(n)
                val h = Packets.header(pkt)
                val sidestep = com.fitzy.apexcleaner.core.AppSettings.blockSidestep(this)
                val isDns = h != null && h.proto == Packets.UDP && n >= h.ihl + 4 && Packets.u16(pkt, h.ihl + 2) == 53 &&
                    (h.dst.contentEquals(FAKE_DNS_BYTES) || sidestep)
                // A bad packet or a full thread pool must never end the loop or the app.
                runCatching {
                    when {
                        h == null || isDns -> pool.execute { runCatching { handle(pkt, engine, output) } }
                        full -> flows?.onPacket(pkt, h)
                        // DNS-only mode: anything else that arrives is a hard-wired public lookup server (for example encrypted lookups by address). Refuse at once so the app falls back to the phone's lookup.
                        h.proto == Packets.TCP -> Packets.resetFor(pkt, h)?.let { r -> synchronized(output) { output.write(r) } }
                        else -> Unit
                    }
                }
            }
        } catch (_: Throwable) { /* tunnel closed */ } finally { stop() }
    }

    private fun handle(p: ByteArray, engine: FilterEngine, out: FileOutputStream) {
        if (p.size < 28 || (p[0].toInt() shr 4) != 4 || p[9].toInt() != 17) return
        val ihl = (p[0].toInt() and 15) * 4
        val dport = (p[ihl + 2].toInt() and 0xff shl 8) or (p[ihl + 3].toInt() and 0xff)
        if (dport != 53 || p.size < ihl + 8) return
        val dns = p.copyOfRange(ihl + 8, p.size)
        val q = DnsPacket.parseQuestion(dns) ?: return
        val paused = AdBlock.isPaused()
        // Apps that try a private encrypted-lookup service are turned away so they use the phone's lookup (which is filtered).
        // Firefox is told "does not exist" for its canary name, the official way to keep it on the phone's lookup.
        if (!paused && com.fitzy.apexcleaner.core.AppSettings.blockSidestep(this) && !engine.userAllow.matches(q.name) &&
            (q.name == SidestepGuard.FIREFOX_CANARY || SidestepGuard.isPrivateLookupName(q.name))) {
            ShieldRules.record(this, q.name, true); ShieldStats.lookups.value++
            synchronized(out) { out.write(wrap(p, ihl, DnsPacket.nxdomainResponse(dns, q))) }
            return
        }
        // Quiet hours (PIN-locked time rules). Protected names are never touched. Not counted as ads; the 15-minute pause does not lift them.
        if (!engine.isProtected(q.name)) {
            val c = java.util.Calendar.getInstance()
            if (QuietHours.blocks(this, q.name, c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE))) {
                ShieldRules.record(this, q.name, true); ShieldStats.lookups.value++
                if (TrafficLog.recording) TrafficLog.addFlow(FlowEntry(System.currentTimeMillis(), dnsAsker(p, ihl), "DNS", q.name, 53, 0, 0, true, "Quiet hours"))
                synchronized(out) { out.write(wrap(p, ihl, DnsPacket.nxdomainResponse(dns, q))) }
                return
            }
        }
        // While paused, every name is answered normally; the lists come back by themselves when the 15 minutes end.
        val rawVerdict = engine.decide(q.name)
        // Ad-blocker detection scripts may load (so sites show the page, not "please allow ads"); your own Block choice still wins.
        val letThrough = rawVerdict == FilterEngine.Verdict.BLOCK && com.fitzy.apexcleaner.core.AppSettings.allowAdblockChecks(this) &&
            AdblockCheckNames.matches(q.name) && !engine.userDeny.matches(q.name)
        val verdict = if (letThrough) FilterEngine.Verdict.SAFE_PASSTHROUGH else rawVerdict
        var block = !paused && (verdict == FilterEngine.Verdict.BLOCK ||
            (verdict == FilterEngine.Verdict.ALLOW && MetaAds.isMetaAdName(q.name) && metaAdAsker(p, ihl)))
        var answer: ByteArray? = null
        if (!block) {
            answer = resolve(dns)
            // See through disguises: a harmless-looking name that is really an alias of a listed tracker is blocked too.
            if (!paused && answer != null && verdict == FilterEngine.Verdict.ALLOW) {
                val aliases = DnsPacket.cnameTargets(answer)
                if (aliases.isNotEmpty() && engine.decideWithAliases(q.name, aliases) == FilterEngine.Verdict.BLOCK) block = true
            }
        }
        ShieldRules.record(this, q.name, block); ShieldStats.lookups.value++
        if (block) {
            AdBlock.recordBlocked(this); ShieldStats.blocked.value++
            ShieldStats.recent.value = (listOf(q.name) + ShieldStats.recent.value).distinct().take(40)
            answer = if (com.fitzy.apexcleaner.core.AppSettings.nxdomainReply(this)) DnsPacket.nxdomainResponse(dns, q) else DnsPacket.blockedResponse(dns, dns.size, q)
        }
        if (TrafficLog.recording) {
            if (!block && answer != null) DnsPacket.addresses(answer).forEach { TrafficLog.learn(it, q.name) }
            TrafficLog.addFlow(FlowEntry(System.currentTimeMillis(), dnsAsker(p, ihl), "DNS", q.name, 53, 0, 0, block, if (block) "Ad or tracker list" else ""))
        }
        answer ?: return
        synchronized(out) { out.write(wrap(p, ihl, answer)) }
    }

    /** True when the app asking is an ordinary non-Meta, non-browser app, so Meta ad names can be blocked for it. */
    private fun metaAdAsker(p: ByteArray, ihl: Int): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT < 29) return false
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val sport = (p[ihl].toInt() and 0xff shl 8) or (p[ihl + 1].toInt() and 0xff)
        val uid = cm.getConnectionOwnerUid(17, java.net.InetSocketAddress(InetAddress.getByAddress(p.copyOfRange(12, 16)), sport),
            java.net.InetSocketAddress(InetAddress.getByAddress(FAKE_DNS_BYTES), 53))
        val pkgs = packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        MetaAds.shouldBlockFor("graph.facebook.com", pkgs) { AdNoticeRules.isBrowser(it) }
    }.getOrDefault(false)

    /** Encrypted lookup first; plain UDP only if the encrypted one fails. */
    private fun resolve(query: ByteArray): ByteArray? =
        if (com.fitzy.apexcleaner.core.AppSettings.encryptedDns(this)) doh(query) ?: udp(query) else udp(query)

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
    private fun appLabel(uid: Int): String = if (uid < 0) "unknown app" else runCatching { packageManager.getPackagesForUid(uid)?.firstOrNull() }.getOrNull() ?: "uid $uid"

    /** Names the app behind a DNS question for Live Traffic; only called while recording. */
    private fun dnsAsker(p: ByteArray, ihl: Int): String = runCatching {
        if (android.os.Build.VERSION.SDK_INT < 29) return "unknown app"
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val sport = (p[ihl].toInt() and 0xff shl 8) or (p[ihl + 1].toInt() and 0xff)
        appLabel(cm.getConnectionOwnerUid(17, java.net.InetSocketAddress(InetAddress.getByAddress(p.copyOfRange(12, 16)), sport), java.net.InetSocketAddress(InetAddress.getByAddress(p.copyOfRange(16, 20)), 53)))
    }.getOrDefault("unknown app")

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
        TrafficLog.addPacket(o, o.size)
        return o
    }

    private fun stop() {
        runCatching { tunOut = null }
        running = false; ShieldStats.running.value = false; ShieldStats.limiterOn.value = false; ShieldStats.limiting.value = false; ShieldStats.listNames.value = 0
        ShieldRules.live = null; ShieldRules.save(this); AdBlock.save(this); flows?.closeAll(); flows = null
        runCatching { tun?.close() }; tun = null
        com.fitzy.apexcleaner.core.Notifier.refresh(this, force = true)
        stopSelf()
    }

    override fun onRevoke() { stop() }
    override fun onDestroy() { running = false; ShieldStats.running.value = false; pool.shutdownNow(); relayPool.shutdownNow(); timer.shutdownNow(); starter.shutdownNow(); super.onDestroy() }

    companion object {
        const val FAIL_TEXT = "The ad shield could not start. Android may have switched its VPN permission to another app. Turn the shield on again and accept the permission."
        const val ACTION_STOP = "com.fitzy.apexcleaner.STOP_SHIELD"
        private const val LOCAL_ADDR = "10.111.222.1"
        private const val FAKE_DNS = "10.111.222.2"
        private val FAKE_DNS_BYTES = byteArrayOf(10, 111.toByte(), 222.toByte(), 2)
        private const val DOH_URL = "https://cloudflare-dns.com/dns-query"
        private const val FALLBACK_DNS = "1.1.1.1"

        fun start(ctx: Context) { ctx.startService(Intent(ctx, ApexVpnService::class.java)) }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, ApexVpnService::class.java).setAction(ACTION_STOP)) }
    }
}
