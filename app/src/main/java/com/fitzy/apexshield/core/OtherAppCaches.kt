package com.fitzy.apexshield.core

import android.content.Context
import android.os.storage.StorageManager
import java.io.File
import java.io.RandomAccessFile

/**
 * Asks Android itself to clear the caches of ALL apps (the official, no-root way).
 * Android only ever deletes cache files this way; app data, logins and settings are never touched.
 */
object OtherAppCaches {
    private const val MAX_EXTRA = 6L shl 30 // ask for at most 6 GB beyond current free space

    /** Estimate of how much other apps' caches Android is willing to clear. */
    fun clearableBytes(ctx: Context): Long = runCatching {
        val sm = ctx.getSystemService(StorageManager::class.java)
        val uuid = sm.getUuidForPath(ctx.filesDir)
        (sm.getAllocatableBytes(uuid) - ctx.filesDir.usableSpace).coerceAtLeast(0)
    }.getOrDefault(0L)

    /** Returns how many bytes were freed. Briefly reserves space so Android clears caches to make room. */
    fun clear(ctx: Context): Long {
        val sm = ctx.getSystemService(StorageManager::class.java)
        val tmp = File(ctx.cacheDir, "space_request.tmp")
        val before = ctx.filesDir.usableSpace
        try {
            val uuid = sm.getUuidForPath(ctx.filesDir)
            val clearable = sm.getAllocatableBytes(uuid) - before
            if (clearable <= 0) return 0L
            val want = before + clearable.coerceAtMost(MAX_EXTRA)
            RandomAccessFile(tmp, "rw").use { sm.allocateBytes(it.fd, want) }
        } catch (_: Exception) {
            // Android declined or storage changed; nothing was removed by us.
        } finally {
            tmp.delete()
        }
        return (ctx.filesDir.usableSpace - before).coerceAtLeast(0L)
    }
}
