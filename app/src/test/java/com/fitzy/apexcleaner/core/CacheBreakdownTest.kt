package com.fitzy.apexcleaner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheBreakdownTest {
    @Test fun sortsFilesIntoKinds() {
        val base = "/storage/emulated/0/Android/data/com.x/cache/"
        assertEquals(CacheKind.Media, CacheBreakdown.classify(base + "video/a.mp4"))
        assertEquals(CacheKind.Thumbnails, CacheBreakdown.classify(base + "thumbs/a.0"))
        assertEquals(CacheKind.TempLogs, CacheBreakdown.classify(base + "logs/run.log"))
        assertEquals(CacheKind.Other, CacheBreakdown.classify(base + "blob.bin"))
    }

    @Test fun addsUpBytesPerKind() {
        val s = CacheBreakdown.sum(arrayOf("100\t/x/cache/a.mp4", "50\t/x/cache/b.mp3", "7\t/x/cache/c.log", "bad"))
        assertEquals(150L, s[CacheKind.Media])
        assertEquals(7L, s[CacheKind.TempLogs])
    }
}
