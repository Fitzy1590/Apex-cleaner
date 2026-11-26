package com.fitzy.apexcleaner.net

import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone

/** One finished connection or lookup. [blocked] means the shield refused it. */
data class FlowEntry(val timeMs: Long, val app: String, val proto: String, val host: String, val port: Int, val up: Long, val down: Long, val blocked: Boolean, val note: String = "")

/** Short-lived record of what went through the shield. Nothing is kept unless [recording] is switched on. Never stores payloads beyond the first 128 bytes of a packet. */
object TrafficLog {
    const val SNAP = 128
    private const val MAX_FLOWS = 500
    private const val MAX_PACKETS = 20_000
    @Volatile var recording = false
    val flows = MutableStateFlow<List<FlowEntry>>(emptyList())
    private val flowBuf = ArrayDeque<FlowEntry>()
    private class Pkt(val micros: Long, val orig: Int, val data: ByteArray)
    private val pkts = ArrayDeque<Pkt>()
    /** Server address to the name an app asked for, learned from DNS answers, so connections show names instead of numbers. */
    private val names = object : LinkedHashMap<String, String>(256, 0.75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>?) = size > 2000 }

    fun learn(ip: ByteArray, name: String) { synchronized(names) { names[ipText(ip)] = name } }
    fun nameFor(ip: ByteArray): String = synchronized(names) { names[ipText(ip)] } ?: ipText(ip)
    fun ipText(ip: ByteArray) = ip.joinToString(".") { (it.toInt() and 0xff).toString() }

    fun addFlow(e: FlowEntry) {
        if (!recording) return
        synchronized(flowBuf) { flowBuf.addLast(e); while (flowBuf.size > MAX_FLOWS) flowBuf.removeFirst(); flows.value = flowBuf.reversed() }
    }

    fun addPacket(p: ByteArray, len: Int) {
        if (!recording || len <= 0) return
        val k = minOf(len, SNAP)
        synchronized(pkts) { pkts.addLast(Pkt(System.currentTimeMillis() * 1000, len, p.copyOf(k))); while (pkts.size > MAX_PACKETS) pkts.removeFirst() }
    }

    fun clear() { synchronized(flowBuf) { flowBuf.clear(); flows.value = emptyList() }; synchronized(pkts) { pkts.clear() } }
    fun packetCount() = synchronized(pkts) { pkts.size }
    fun snapshotPcapng(): ByteArray = synchronized(pkts) { TrafficExport.pcapng(pkts.map { Triple(it.micros, it.orig, it.data) }) }
    fun snapshotHar(): String = TrafficExport.har(flows.value.reversed())
}

object TrafficExport {
    private fun ByteArrayOutputStream.le32(v: Long) { for (i in 0..3) write(((v shr (8 * i)) and 0xff).toInt()) }
    private fun ByteArrayOutputStream.le16(v: Int) { write(v and 0xff); write((v shr 8) and 0xff) }

    /** pcapng file (little-endian, raw IP packets, microsecond clock). Each packet is (epoch micros, real length, captured bytes). */
    fun pcapng(packets: List<Triple<Long, Int, ByteArray>>): ByteArray {
        val o = ByteArrayOutputStream()
        // Section header: type, length, byte-order magic, version 1.0, unknown section length, length.
        o.le32(0x0A0D0D0A); o.le32(28); o.le32(0x1A2B3C4D); o.le16(1); o.le16(0); o.le32(0xFFFFFFFFL); o.le32(0xFFFFFFFFL); o.le32(28)
        // Interface description: link type 101 = raw IP, snap length 128.
        o.le32(1); o.le32(20); o.le16(101); o.le16(0); o.le32(TrafficLog.SNAP.toLong()); o.le32(20)
        for ((micros, orig, data) in packets) {
            val pad = (4 - data.size % 4) % 4
            val total = 32 + data.size + pad
            o.le32(6); o.le32(total.toLong()); o.le32(0); o.le32(micros ushr 32); o.le32(micros and 0xffffffffL)
            o.le32(data.size.toLong()); o.le32(orig.toLong()); o.write(data); repeat(pad) { o.write(0) }; o.le32(total.toLong())
        }
        return o.toByteArray()
    }

    private fun iso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    /** HAR 1.2 file. The shield cannot see web page contents, so method and status are placeholders; the real facts are in the _apex fields. */
    fun har(entries: List<FlowEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            val scheme = if (e.port == 80) "http" else "https"
            arr.put(JSONObject()
                .put("startedDateTime", iso(e.timeMs)).put("time", 0)
                .put("request", JSONObject().put("method", "CONNECT").put("url", "$scheme://${e.host}${if (e.port == 443 || e.port == 80 || e.port <= 0) "" else ":" + e.port}/").put("httpVersion", "unknown")
                    .put("headers", JSONArray()).put("queryString", JSONArray()).put("cookies", JSONArray()).put("headersSize", -1).put("bodySize", e.up))
                .put("response", JSONObject().put("status", if (e.blocked) 0 else 200).put("statusText", if (e.blocked) "Blocked by Apex" else "Allowed").put("httpVersion", "unknown")
                    .put("headers", JSONArray()).put("cookies", JSONArray()).put("content", JSONObject().put("size", e.down).put("mimeType", "")).put("redirectURL", "").put("headersSize", -1).put("bodySize", e.down))
                .put("cache", JSONObject()).put("timings", JSONObject().put("send", 0).put("wait", 0).put("receive", 0))
                .put("_apex", JSONObject().put("app", e.app).put("protocol", e.proto).put("blocked", e.blocked).put("bytesUp", e.up).put("bytesDown", e.down).put("note", e.note)))
        }
        return JSONObject().put("log", JSONObject().put("version", "1.2").put("creator", JSONObject().put("name", "Apex Shield").put("version", "1"))
            .put("entries", arr)).toString(2)
    }
}
