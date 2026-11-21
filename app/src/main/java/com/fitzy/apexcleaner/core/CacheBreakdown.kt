package com.fitzy.apexcleaner.core

import android.content.Context

enum class CacheKind(val label: String) {
    Media("Media cache"), Thumbnails("Web & image thumbnails"), TempLogs("Temp files & logs"), Other("Other cache"),
}

data class AppCache(val pkg: String, val label: String, val bytesByKind: Map<CacheKind, Long>) {
    val total: Long get() = bytesByKind.values.sum()
}

/** Read-only look at other apps' cache folders. Never deletes anything. */
object CacheBreakdown {
    /** Sorts one cache file into a plain-English kind by its name and extension. */
    fun classify(path: String): CacheKind {
        val p = path.lowercase()
        val ext = p.substringAfterLast('.', "")
        return when {
            "thumb" in p || ext in setOf("webp", "webview", "favicon") || "/webview/" in p || "/image_manager" in p -> CacheKind.Thumbnails
            ext in setOf("mp4", "mkv", "mp3", "m4a", "aac", "ogg", "exo", "ts", "m4s", "wav", "jpg", "jpeg", "png", "gif") ||
                "exoplayer" in p || "/video" in p || "/audio" in p || "/media" in p -> CacheKind.Media
            ext in setOf("log", "tmp", "temp", "txt", "trace", "crash", "dmp") || "/tmp/" in p || "/logs/" in p -> CacheKind.TempLogs
            else -> CacheKind.Other
        }
    }

    /** Adds up "size<TAB>path" lines from the helper into bytes per kind. */
    fun sum(lines: Array<String>): Map<CacheKind, Long> {
        val out = HashMap<CacheKind, Long>()
        for (l in lines) {
            val size = l.substringBefore('\t').toLongOrNull() ?: continue
            val k = classify(l.substringAfter('\t'))
            out[k] = (out[k] ?: 0L) + size
        }
        return out
    }

    /** Biggest cache users first. Empty when the Shizuku helper is not ready (nothing is guessed). */
    fun scan(ctx: Context, limit: Int = 8): List<AppCache> {
        val pm = ctx.packageManager
        return ShizukuBridge.withService(ctx) { svc ->
            svc.cachePackages().filter { it != ctx.packageName }.map { pkg ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                AppCache(pkg, label, sum(svc.cacheFiles(pkg)))
            }.filter { it.total > 0 }.sortedByDescending { it.total }.take(limit)
        }.orEmpty()
    }
}
