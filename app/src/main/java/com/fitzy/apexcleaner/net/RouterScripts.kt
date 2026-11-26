package com.fitzy.apexcleaner.net

/** Copy-ready router settings that send every device's lookups (port 53) to one DNS server and refuse private lookups (port 853). */
object RouterScripts {
    enum class Kind(val label: String) { OPENWRT("OpenWrt"), ASUS("AsusWRT-Merlin"), PFSENSE("pfSense") }

    private val ipv4 = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")
    fun validIp(s: String) = ipv4.matches(s.trim())

    /** [dnsIp] is the filtering DNS server (usually the router's own address or a Pi-hole). Null when the address is not a valid IPv4 address. */
    fun generate(kind: Kind, dnsIp: String): String? {
        val ip = dnsIp.trim()
        if (!validIp(ip)) return null
        return when (kind) {
            Kind.OPENWRT -> """
                # OpenWrt: run over SSH. Sends all lookups to $ip and refuses private (DoT, port 853) lookups.
                # If $ip is the router itself, the exclusion line is harmless.
                uci add firewall redirect
                uci set firewall.@redirect[-1].name='Apex-force-DNS'
                uci set firewall.@redirect[-1].src='lan'
                uci set firewall.@redirect[-1].proto='tcp udp'
                uci set firewall.@redirect[-1].src_dport='53'
                uci set firewall.@redirect[-1].src_dip='!$ip'
                uci set firewall.@redirect[-1].dest_ip='$ip'
                uci set firewall.@redirect[-1].dest_port='53'
                uci set firewall.@redirect[-1].target='DNAT'
                uci add firewall rule
                uci set firewall.@rule[-1].name='Apex-block-DoT'
                uci set firewall.@rule[-1].src='lan'
                uci set firewall.@rule[-1].dest='wan'
                uci set firewall.@rule[-1].proto='tcp udp'
                uci set firewall.@rule[-1].dest_port='853'
                uci set firewall.@rule[-1].target='REJECT'
                uci commit firewall
                /etc/init.d/firewall restart
            """.trimIndent() + "\n"
            Kind.ASUS -> """
                #!/bin/sh
                # AsusWRT-Merlin: save as /jffs/scripts/firewall-start, then run: chmod a+rx /jffs/scripts/firewall-start
                # (Administration > System > Enable JFFS custom scripts must be on). Reboot or restart the firewall after saving.
                for P in udp tcp; do
                  iptables -t nat -A PREROUTING -i br0 -p ${'$'}P --dport 53 ! -d $ip -j DNAT --to-destination $ip:53
                done
                iptables -I FORWARD -i br0 -p tcp --dport 853 -j REJECT
                iptables -I FORWARD -i br0 -p udp --dport 853 -j REJECT
            """.trimIndent() + "\n"
            Kind.PFSENSE -> """
                # pfSense has no script for this; use the web screen (Firewall menu). Same result:
                # 1) Firewall > NAT > Port Forward > Add
                #    Interface: LAN | Protocol: TCP/UDP
                #    Destination: tick Invert match, type "Address or Alias" $ip | Destination port range: DNS (53)
                #    Redirect target IP: $ip | Redirect target port: DNS (53) | Save, then Apply Changes
                # 2) Firewall > Rules > LAN > Add (top of list)
                #    Action: Reject | Protocol: TCP | Source: LAN subnets | Destination: any | Destination port: 853 | Save
                #    Repeat with Protocol UDP, then Apply Changes
            """.trimIndent() + "\n"
        }
    }
}
