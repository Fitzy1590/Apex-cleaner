package com.fitzy.apexcleaner.core

import java.io.File

/**
 * The single gatekeeper for every delete. Two layers:
 *  1. DENY: anything that looks like user data, logins or settings is never touched.
 *  2. ALLOW: only a short list of known-disposable file kinds may be deleted.
 */
object PathGuard {
    private val deniedSegments = setOf(
        "shared_prefs", "databases", "no_backup", "app_webview", "accounts",
        "keystore", "credentials", "cookies", "dcim", "pictures", "documents",
        "music", "movies", "whatsapp", "signal", "backups",
    )
    private val deniedNameParts = listOf(
        "token", "auth", "login", "session", "cookie", "credential", "password", "secret", "key",
    )
    private val deniedExtensions = setOf(
        "db", "sqlite", "sqlite3", "xml", "json", "preferences_pb", "pb", "jpg", "jpeg",
        "png", "mp4", "pdf", "doc", "docx", "zip", "kdbx", "bak", "crypt14", "crypt15",
    )
    private val cacheDirNames = setOf("cache", ".cache", "tmp", ".tmp", "temp", ".temp", "thumbnails", ".thumbnails", ".thumbcache", "lost.dir")
    private val protectedDataExtensions = setOf(
        "db", "sqlite", "sqlite3", "xml", "json", "preferences_pb", "pb", "kdbx", "bak", "crypt14", "crypt15",
    )
    private val disposableExtensions = setOf("tmp", "temp", "log", "apk", "thumb", "cache", "old")

    fun isDenied(file: File): Boolean {
        val path = file.path.lowercase().replace('\\', '/')
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.any { it in deniedSegments }) return true
        val name = file.name.lowercase()
        if (deniedNameParts.any { name.contains(it) }) return true
        if (file.extension.lowercase() in deniedExtensions) return true
        return false
    }

    /** Files inside a directory the app itself owns as cache (cacheDir / code_cache). */
    fun mayDeleteInOwnCache(file: File): Boolean = !isDenied(file)

    /** Files found on shared storage: only clearly disposable kinds. */
    fun mayDeleteOnSharedStorage(file: File): Boolean {
        val path = file.path.lowercase().replace('\\', '/')
        if (path.contains("/android/data/") || path.contains("/android/obb/")) return false
        if (hasProtectedName(file)) return false
        // Thumbnail caches are rebuilt by the system, so they are safe even inside photo folders.
        if (path.contains("/.thumbnails/") || path.contains("/.thumbcache")) return true
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.any { it in deniedSegments }) return false
        // Anything inside a folder literally named cache/tmp/etc. is rebuildable, except settings-like files.
        if (segments.dropLast(1).any { it in cacheDirNames } && file.extension.lowercase() !in protectedDataExtensions) return true
        return file.extension.lowercase() in disposableExtensions
    }

    private fun hasProtectedName(file: File): Boolean {
        val name = file.name.lowercase()
        return deniedNameParts.any { name.contains(it) }
    }
}
