package com.fitzy.apexcleaner.core

import java.io.File
import kotlin.system.exitProcess

/**
 * Runs in the Shizuku helper process (shell level). It can reach other apps' cache folders on
 * shared storage, which normal apps cannot on Android 11+. It re-checks every path itself
 * so even a bad request can only ever remove files inside Android/data/<app>/cache.
 */
class ShellFileService : IShellFiles.Stub() {
    override fun destroy() { exitProcess(0) }

    override fun cachePackages(): Array<String> =
        File(DATA_ROOT).listFiles()?.filter { File(it, "cache").isDirectory }?.map { it.name }.orEmpty().toTypedArray()

    override fun cacheFiles(pkg: String): Array<String> {
        if (!isPlainName(pkg)) return emptyArray()
        val out = ArrayList<String>()
        File(DATA_ROOT, "$pkg/cache").walkTopDown().filter { it.isFile }.take(MAX_FILES).forEach {
            out += "${it.length()}\t${it.path}"
        }
        return out.toTypedArray()
    }

    override fun deleteCacheFiles(paths: Array<String>): BooleanArray =
        BooleanArray(paths.size) { i -> isCacheFile(paths[i]) && File(paths[i]).delete() }

    companion object {
        const val DATA_ROOT = "/storage/emulated/0/Android/data"
        const val MAX_FILES = 3000

        private fun isPlainName(s: String) = s.isNotEmpty() && s != "." && s != ".." && '/' !in s

        /** /storage/emulated/0/Android/data/<pkg>/cache/... with no path tricks. */
        fun isCacheFile(path: String): Boolean {
            if (!path.startsWith("$DATA_ROOT/") || path.contains("/../") || path.endsWith("/..")) return false
            val parts = path.removePrefix("$DATA_ROOT/").split('/')
            return parts.size >= 3 && isPlainName(parts[0]) && parts[1] == "cache" && parts.last().isNotEmpty()
        }
    }
}
