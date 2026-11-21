package com.fitzy.apexshield.vpn

import android.content.Context
import android.net.ConnectivityManager
import com.fitzy.apexshield.core.WhitelistStore
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

enum class DohProvider(val label: String, val url: String?) {
    SYSTEM("Your network's DNS", null),
    CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query"),
    GOOGLE("Google", "https://dns.google/dns-query"),
    QUAD9("Quad9", "https://dns.quad9.net/dns-query"),
    ADGUARD("AdGuard", "https://dns.adguard-dns.com/dns-query"),
}

data class QueryLog(val time: Long, val domain: String, val category: Category?) {
    val blocked get() = category != null
}

data class ShieldUi(
    val running: Boolean = false,
    val blockedTotal: Long = 0,
    val queriesTotal: Long = 0,
    val rules: Int = 0,
    val bypassApps: Int = 0,
    val provider: DohProvider = DohProvider.SYSTEM,
    val log: List<QueryLog> = emptyList(),
)

/** Owner choices, stored privately on the device. */
class ShieldSettings(context: Context) {
    private val prefs = context.getSharedPreferences("shield", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()
    var provider: DohProvider
        get() = DohProvider.values().firstOrNull { it.name == prefs.getString("provider", null) } ?: DohProvider.SYSTEM
        set(v) = prefs.edit().putString("provider", v.name).apply()
    var blockedTotal: Long
        get() = prefs.getLong("blocked_total", 0)
        set(v) = prefs.edit().putLong("blocked_total", v).apply()
    var queriesTotal: Long
        get() = prefs.getLong("queries_total", 0)
        set(v) = prefs.edit().putLong("queries_total", v).apply()
    /** Ads/trackers blocked since midnight; resets itself each new day. */
    var blockedToday: Long
        get() = if (prefs.getString("today_key", null) == today()) prefs.getLong("today_count", 0) else 0
        set(v) = prefs.edit().putString("today_key", today()).putLong("today_count", v).apply()
    /** Time (ms) until which the shield is paused; 0 when not paused. */
    var pausedUntil: Long
        get() = prefs.getLong("paused_until", 0)
        set(v) = prefs.edit().putLong("paused_until", v).apply()
    private fun today() = java.time.LocalDate.now().toString()
    var customBlock: Set<String>
        get() = prefs.getStringSet("custom_block", emptySet()).orEmpty()
        set(v) = prefs.edit().putStringSet("custom_block", v).apply()
    var customAllow: Set<String>
        get() = prefs.getStringSet("custom_allow", emptySet()).orEmpty()
        set(v) = prefs.edit().putStringSet("custom_allow", v).apply()
}

/** Live shield state for the screens, plus the filter and the real-DNS lookups the VPN service uses. */
object ShieldEngine {
    val ui = MutableStateFlow(ShieldUi())
    @Volatile var filter = DnsFilter()
        private set
    private const val MAX_LOG = 300

    /** Syncs the screen with saved counters and choices (call at app start). */
    fun load(ctx: Context) {
        val s = ShieldSettings(ctx)
        ui.value = ui.value.copy(
            blockedTotal = s.blockedTotal, queriesTotal = s.queriesTotal, provider = s.provider,
            bypassApps = WhitelistStore(ctx).all().size,
        )
    }

    fun buildFilter(ctx: Context): DnsFilter {
        val f = DnsFilter()
        fun read(name: String, default: Category) =
            runCatching { ctx.assets.open(name).bufferedReader().use { ShieldLists.parse(it.readText(), default) } }.getOrDefault(emptyList())
        read("blocklist.txt", Category.ADS).forEach { (d, c) -> f.block.add(d, c) }
        read("passthrough.txt", Category.ADS).forEach { (d, _) -> f.allow.add(d) }
        val s = ShieldSettings(ctx)
        s.customBlock.forEach { f.block.add(it, Category.CUSTOM) }
        s.customAllow.forEach { f.allow.add(it) }
        filter = f
        ui.value = ui.value.copy(rules = f.block.size)
        return f
    }

    fun setRunning(on: Boolean) { ui.value = ui.value.copy(running = on) }

    @Volatile private var lastNotifyAt = 0L

    /** Updates the always-on notification; blocked-count refreshes are limited to one every 5 seconds. */
    fun refreshNotification(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifyAt < 5_000) return
        lastNotifyAt = now
        runCatching {
            ctx.getSystemService(android.app.NotificationManager::class.java)
                .notify(com.fitzy.apexshield.core.Notifier.ID_SERVICE, com.fitzy.apexshield.core.Notifier.serviceNotification(ctx))
        }
    }

    fun record(ctx: Context, domain: String, category: Category?) {
        val s = ShieldSettings(ctx)
        s.queriesTotal += 1
        if (category != null) { s.blockedTotal += 1; s.blockedToday += 1 }
        if (category != null) refreshNotification(ctx)
        val e = QueryLog(System.currentTimeMillis(), domain, category)
        ui.value = ui.value.let {
            it.copy(
                queriesTotal = s.queriesTotal, blockedTotal = s.blockedTotal,
                log = (listOf(e) + it.log).take(MAX_LOG),
            )
        }
    }

    fun setProvider(ctx: Context, p: DohProvider) {
        ShieldSettings(ctx).provider = p
        ui.value = ui.value.copy(provider = p)
    }

    /** 1-tap: block the name if it was allowed, allow it again if it was blocked. */
    fun toggleDomain(ctx: Context, domain: String) {
        val s = ShieldSettings(ctx)
        val d = domain.lowercase()
        if (filter.blockedCategory(d) != null) {
            s.customBlock = s.customBlock - d
            s.customAllow = s.customAllow + d
            filter.allow.add(d)
        } else {
            s.customAllow = s.customAllow - d
            s.customBlock = s.customBlock + d
            filter.allow.remove(d)
            filter.block.add(d, Category.CUSTOM)
        }
        ui.value = ui.value.copy(rules = filter.block.size)
    }

    fun clearLog() { ui.value = ui.value.copy(log = emptyList()) }

    /** Asks the real DNS server. Uses the chosen secure-DNS (DoH) provider first, then falls back to plain network DNS. */
    fun resolve(dns: ByteArray, provider: DohProvider, plainServer: InetAddress): ByteArray? {
        provider.url?.let { u -> doh(u, dns)?.let { return it } }
        return plain(dns, plainServer)
    }

    private fun doh(url: String, dns: ByteArray): ByteArray? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 4000; c.readTimeout = 4000
            c.setRequestProperty("Content-Type", "application/dns-message")
            c.setRequestProperty("Accept", "application/dns-message")
            c.outputStream.use { it.write(dns) }
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } finally { c.disconnect() }
    }.getOrNull()

    private fun plain(dns: ByteArray, server: InetAddress): ByteArray? = runCatching {
        DatagramSocket().use { s ->
            s.soTimeout = 3000
            s.send(DatagramPacket(dns, dns.size, server, 53))
            val buf = ByteArray(4096)
            val r = DatagramPacket(buf, buf.size)
            s.receive(r)
            buf.copyOf(r.length)
        }
    }.getOrNull()

    /** The network's own DNS server, read before the VPN starts. Falls back to Cloudflare. */
    fun networkDns(ctx: Context): InetAddress {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val found = runCatching {
            cm.getLinkProperties(cm.activeNetwork)?.dnsServers?.firstOrNull { it.address.size == 4 }
        }.getOrNull()
        return found ?: InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))
    }
}
