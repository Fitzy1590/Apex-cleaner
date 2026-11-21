package com.fitzy.apexcleaner.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads the new version inside the app and hands it to Android's installer. Android still asks you to confirm. */
object UpdateInstaller {
    /** What to show on the Update card while working; null when idle. */
    val status = MutableStateFlow<String?>(null)
    @Volatile private var busy = false
    private const val ACTION = "com.fitzy.apexcleaner.UPDATE_RESULT"

    fun start(ctx: Context, info: UpdateInfo) {
        if (busy) return
        val app = ctx.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            status.value = "Allow Apex Cleaner to install updates on the next screen, come back, then tap Update again."
            app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        busy = true
        Thread({
            try {
                val apk = download(app, info.apkUrl)
                status.value = "Opening the installer…"
                try { install(app, apk) } catch (e: Exception) {
                    status.value = "Android would not open the installer. Tap Update to try again."; busy = false; return@Thread
                }
                // If Android never answers, do not stay stuck: free the button after a short wait.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (status.value == "Opening the installer…") { status.value = "Android did not open the installer. Tap Update to try again."; busy = false }
                }, 20_000)
            } catch (e: Exception) {
                status.value = "The update could not be downloaded. Check your connection and try again."
                busy = false
            }
        }, "apex-update").start()
    }

    private fun download(ctx: Context, url: String): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "apex-update.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
        val total = c.contentLengthLong
        var done = 0L; var lastPct = -1
        c.inputStream.use { i -> out.outputStream().use { o ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = i.read(buf); if (n < 0) break
                o.write(buf, 0, n); done += n
                if (total > 0) { val pct = (done * 100 / total).toInt(); if (pct != lastPct) { lastPct = pct; status.value = "Downloading update… $pct%" } }
            }
        } }
        if (out.length() < 1_000_000) throw java.io.IOException("file too small")
        return out
    }

    private fun install(ctx: Context, apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).also { it.setAppPackageName(ctx.packageName) }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            apk.inputStream().use { i -> s.openWrite("apex.apk", 0, apk.length()).use { o -> i.copyTo(o); s.fsync(o) } }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pend = PendingIntent.getBroadcast(ctx, id, Intent(ctx, Result::class.java).setAction(ACTION), flags)
            s.commit(pend.intentSender)
        }
    }

    /** Receives the installer's answer: show its confirm screen, or report a failure in plain words. */
    class Result : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION") val next = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    next?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    status.value = "Tap Install on the Android screen to finish the update."
                }
                PackageInstaller.STATUS_SUCCESS -> { status.value = null; busy = false }
                PackageInstaller.STATUS_FAILURE_ABORTED -> { status.value = "Update cancelled. Tap Update to try again."; busy = false }
                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    { status.value = "This update could not be installed over the current version. Uninstall Apex Cleaner once, then install the new one."; busy = false }
                else -> { status.value = "The update could not be installed. Tap Update to try again."; busy = false }
            }
        }
    }
}
