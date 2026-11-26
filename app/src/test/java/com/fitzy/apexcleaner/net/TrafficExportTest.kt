package com.fitzy.apexcleaner.net

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TrafficExportTest {
    @Test fun pcapngHasValidBlocksAndPadding() {
        val b = TrafficExport.pcapng(listOf(Triple(1_700_000_000_123_456L, 60, ByteArray(5) { 7 }), Triple(5L, 3, ByteArray(4))))
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var off = 0; val types = ArrayList<Int>()
        while (off < b.size) { val t = bb.getInt(off); val len = bb.getInt(off + 4); assertEquals(0, len % 4); assertEquals(len, bb.getInt(off + len - 4)); types += t; off += len }
        assertEquals(b.size, off)
        assertEquals(listOf(0x0A0D0D0A, 1, 6, 6), types)
        assertEquals(0x1A2B3C4D, bb.getInt(8))
        assertEquals(101, bb.getShort(28 + 8).toInt())
        val epb = 28 + 20
        assertEquals(1_700_000_000_123_456L, (bb.getInt(epb + 12).toLong() shl 32) or (bb.getInt(epb + 16).toLong() and 0xffffffffL))
        assertEquals(5, bb.getInt(epb + 20)); assertEquals(60, bb.getInt(epb + 24))
    }

    @Test fun harIsValidJsonAndKeepsFacts() {
        val j = JSONObject(TrafficExport.har(listOf(
            FlowEntry(1_700_000_000_000L, "Chrome", "DNS", "ads.example.net", 53, 0, 0, true, "Ads"),
            FlowEntry(1_700_000_001_000L, "YouTube", "TCP", "rr3.googlevideo.com", 443, 900, 8_000_000, false))))
        val e = j.getJSONObject("log").getJSONArray("entries")
        assertEquals(2, e.length())
        assertEquals("https://ads.example.net:53/", e.getJSONObject(0).getJSONObject("request").getString("url"))
        assertTrue(e.getJSONObject(0).getJSONObject("_apex").getBoolean("blocked"))
        assertEquals("https://rr3.googlevideo.com/", e.getJSONObject(1).getJSONObject("request").getString("url"))
        assertEquals(8_000_000, e.getJSONObject(1).getJSONObject("response").getInt("bodySize"))
        assertEquals("2023-11-14T22:13:20.000Z", e.getJSONObject(0).getString("startedDateTime"))
    }

    @Test fun nothingIsKeptWhileRecordingIsOff() {
        TrafficLog.recording = false; TrafficLog.clear()
        TrafficLog.addFlow(FlowEntry(1, "a", "TCP", "h", 443, 1, 1, false)); TrafficLog.addPacket(ByteArray(40), 40)
        assertTrue(TrafficLog.flows.value.isEmpty()); assertEquals(0, TrafficLog.packetCount())
        TrafficLog.recording = true
        TrafficLog.addPacket(ByteArray(500), 500)
        assertEquals(1, TrafficLog.packetCount())
        TrafficLog.recording = false; TrafficLog.clear()
    }
}
