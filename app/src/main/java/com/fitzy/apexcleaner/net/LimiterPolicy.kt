package com.fitzy.apexcleaner.net

/** Works out which app is on screen from Android's usage events (resumed = on screen, paused = left). Pure. */
class ForegroundTracker {
    var current: String? = null; private set
    /** Every app on screen right now (split-screen shows two). */
    @Volatile var visible: Set<String> = emptySet(); private set

    /** [kind] 1 = app came to the screen, 2 = app left the screen (Android's own event codes). */
    fun onEvent(pkg: String, kind: Int) {
        if (kind == 1) { current = pkg; visible = visible + pkg }
        else if (kind == 2) { if (current == pkg) current = null; visible = visible - pkg }
    }
}

/**
 * Streaming priority: while a priority app (video, music, game) is on screen, every other app is paced down so the
 * priority app gets the connection. When none is on screen, nothing is slowed. Waits instead of drops (TokenBucket).
 */
object LimiterPolicy {
    /** Bytes per second that all limited apps share while a priority app is on screen. */
    const val DOWN_WHILE_STREAMING = 192L * 1024   // about 1.5 Mbps
    const val UP_WHILE_STREAMING = 48L * 1024

    fun downRate(enabled: Boolean, foreground: String?, isPriority: (String) -> Boolean): Long =
        if (enabled && foreground != null && isPriority(foreground)) DOWN_WHILE_STREAMING else 0L

    fun upRate(enabled: Boolean, foreground: String?, isPriority: (String) -> Boolean): Long =
        if (enabled && foreground != null && isPriority(foreground)) UP_WHILE_STREAMING else 0L
}

/** A pacing gate whose speed can change while running. Rate 0 = open. */
class PaceGate {
    @Volatile private var bucket: TokenBucket? = null
    @Volatile var rate: Long = 0L; private set

    fun setRate(bytesPerSec: Long) {
        if (bytesPerSec == rate) return
        rate = bytesPerSec
        bucket = if (bytesPerSec > 0) TokenBucket(bytesPerSec, maxOf(bytesPerSec / 2, 32_768L)) else null
    }

    /** Milliseconds the caller should wait before sending [bytes] (0 = go now). Never drops. */
    fun waitMs(bytes: Int): Long = (bucket?.reserve(bytes) ?: 0L) / 1_000_000
}
