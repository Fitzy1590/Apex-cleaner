package com.fitzy.apexcleaner.net

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** Public ad/tracker lists, stored privately on the device. */
object FilterLists {
    val sources = mapOf(
        "stevenblack.txt" to "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "adguard.txt" to "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
        // Aggressive extras: Hagezi Pro++ (ads, trackers, telemetry) and Peter Lowe's ad servers.
        "hagezi_pro_plus.txt" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.plus.txt",
        "peterlowe.txt" to "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
    )
    private const val MAX_BYTES = 20L shl 20

    private fun dir(ctx: Context) = File(ctx.filesDir, "filters").apply { mkdirs() }

    /** Lines of every downloaded list (empty until the first update). */
    fun load(ctx: Context): Sequence<String> =
        dir(ctx).listFiles()?.asSequence()?.flatMap { it.useLines { l -> l.toList() }.asSequence() } ?: emptySequence()

    /** Downloads each list to a temp file, then swaps it in. A failed download keeps the old list. Returns lists updated. */
    fun update(ctx: Context): Int {
        var ok = 0
        for ((name, url) in sources) runCatching {
            val c = URL(url).openConnection() as HttpsURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            if (c.responseCode != 200) return@runCatching
            val tmp = File(dir(ctx), "$name.tmp")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            if (tmp.length() in 1000..MAX_BYTES && tmp.renameTo(File(dir(ctx), name))) ok++ else tmp.delete()
        }
        return ok
    }

    fun hasLists(ctx: Context): Boolean = sources.keys.all { File(dir(ctx), it).length() > 1000 }

    fun count(ctx: Context): Int = DomainTrie.fromList(load(ctx)).size

    /** Weekly refresh, only on Wi-Fi while charging and idle (heavy work rule). */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<ListUpdateWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresCharging(true).setRequiresDeviceIdle(true).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("filter_lists", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class ListUpdateWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result { FilterLists.update(applicationContext); return Result.success() }
}
