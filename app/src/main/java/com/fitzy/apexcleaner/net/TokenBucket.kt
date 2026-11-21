package com.fitzy.apexcleaner.net

import kotlinx.coroutines.delay

/**
 * Token-bucket pacing. When the budget is spent, callers wait (suspend) instead of data being dropped,
 * which lets TCP slow down naturally and keeps calls, games and video steady.
 * A rate of 0 means unlimited.
 */
class TokenBucket(private val bytesPerSec: Long, private val burstBytes: Long = bytesPerSec, private val now: () -> Long = System::nanoTime) {
    private var tokens = burstBytes.toDouble()
    private var last = now()

    /** Takes [bytes] and returns how many nanoseconds the caller must wait before sending. Pure and testable. */
    @Synchronized fun reserve(bytes: Int): Long {
        if (bytesPerSec <= 0) return 0L
        val t = now()
        tokens = minOf(burstBytes.toDouble(), tokens + (t - last) * bytesPerSec / 1e9)
        last = t
        tokens -= bytes
        return if (tokens >= 0) 0L else (-tokens * 1e9 / bytesPerSec).toLong()
    }

    /** Suspends (never drops) until the bytes fit within the rate. */
    suspend fun acquire(bytes: Int) {
        val waitNs = reserve(bytes)
        if (waitNs > 0) delay(waitNs / 1_000_000 + 1)
    }
}
