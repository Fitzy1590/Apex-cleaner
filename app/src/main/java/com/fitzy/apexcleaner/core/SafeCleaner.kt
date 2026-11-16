package com.fitzy.apexcleaner.core

import android.content.Context
import android.os.Environment
import java.io.File

enum class CleanCategory(val label: String) {
    AppCache("App cache"),
    HiddenCache("Hidden temporary files"),
    InstallerApks("Leftover installers"),
    Thumbnails("Thumbnail caches"),
    Logs("Log files"),
    EmptyFolders("Empty leftover folders"),
    OtherAppFolders("Other apps' cache folders (helper app)"),
}

data class CleanReport(val bytesByCategory: Map<CleanCategory, Long>, val filesRemoved: Int) {
    val totalBytes: Long get() = bytesByCategory.values.sum()
}

/** Scans and cleans only disposable files. Every delete is approved by [PathGuard]. */
class SafeCleaner(private val context: Context) {

    fun scan(): CleanReport = run(dryRun = true)

    fun clean(): CleanReport = run(dryRun = false)

    private fun run(dryRun: Boolean): CleanReport {
        val bytes = CleanCategory.values().associateWith { 0L }.toMutableMap()
        var count = 0

        fun handle(cat: CleanCategory, f: File) {
            val size = f.length()
            if (dryRun || f.delete()) {
                bytes[cat] = (bytes[cat] ?: 0L) + size
                count++
            }
        }

        // Layer 1: this app's own cache areas (always permitted by Android).
        val own = listOfNotNull(context.cacheDir, context.codeCacheDir) +
            context.externalCacheDirs.filterNotNull()
        own.forEach { root ->
            root.walkBottomUp().filter { it.isFile && PathGuard.mayDeleteInOwnCache(it) }.forEach {
                val cat = if (it.name.startsWith(".")) CleanCategory.HiddenCache else CleanCategory.AppCache
                handle(cat, it)
            }
        }

        // Layer 2: shared storage leftovers (only if the user granted All-files access).
        if (hasAllFilesAccess()) {
            val root = Environment.getExternalStorageDirectory()
            root.walkTopDown()
                .onEnter { dir -> !isOffLimits(dir) }
                .filter { it.isFile && PathGuard.mayDeleteOnSharedStorage(it) }
                .forEach {
                    val p = it.path.lowercase()
                    val cat = when {
                        it.extension.equals("apk", true) -> CleanCategory.InstallerApks
                        p.contains("thumb") -> CleanCategory.Thumbnails
                        it.extension.equals("log", true) -> CleanCategory.Logs
                        else -> CleanCategory.HiddenCache
                    }
                    handle(cat, it)
                }
            // Orphans: folders that are completely empty (and not protected) get removed.
            root.walkBottomUp()
                .onEnter { dir -> !isOffLimits(dir) }
                .filter { it.isDirectory && it != root && it.list()?.isEmpty() == true && !PathGuard.isDenied(it) }
                .forEach { if (dryRun || it.delete()) { count++ } }
        }

        // Layer 3: other apps' own cache folders, only when the optional Shizuku helper is on.
        // Apps the user or system protects are skipped; PathGuard approves every file.
        if (ShizukuBridge.isReady(context)) {
            val protectedApps = ProtectedApps(context, WhitelistStore(context))
            ShizukuBridge.withService(context) { svc ->
                for (pkg in svc.cachePackages()) {
                    if (protectedApps.isProtected(pkg)) continue
                    val approved = svc.cacheFiles(pkg).mapNotNull { line ->
                        val size = line.substringBefore('\t').toLongOrNull() ?: return@mapNotNull null
                        File(line.substringAfter('\t')).takeIf { PathGuard.mayDeleteInOtherAppCache(it) }?.let { it to size }
                    }
                    if (approved.isEmpty()) continue
                    val removed = if (dryRun) BooleanArray(approved.size) { true }
                    else svc.deleteCacheFiles(approved.map { it.first.path }.toTypedArray())
                    approved.forEachIndexed { i, (_, size) ->
                        if (removed[i]) {
                            bytes[CleanCategory.OtherAppFolders] = (bytes[CleanCategory.OtherAppFolders] ?: 0L) + size
                            count++
                        }
                    }
                }
            }
        }
        return CleanReport(bytes, count)
    }

    private fun isOffLimits(dir: File): Boolean {
        val p = dir.path.lowercase()
        return p.contains("/android/data") || p.contains("/android/obb") || PathGuard.isDenied(dir)
    }

    private fun hasAllFilesAccess(): Boolean =
        android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
}
