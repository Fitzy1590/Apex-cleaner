package com.fitzy.apexcleaner.core

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Where the optional Shizuku helper stands, in plain words for the UI. */
enum class HelperState(val label: String) {
    NotInstalled("Helper app not installed"),
    NotRunning("Helper app is not started"),
    NeedsPermission("Helper app needs your OK"),
    Ready("Deeper clean is on"),
}

object ShizukuBridge {
    const val HELPER_PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE = 4711
    const val HELPER_URL = "https://shizuku.rikka.app/download/"

    fun state(ctx: Context): HelperState = runCatching {
        when {
            Shizuku.pingBinder() ->
                if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) HelperState.NeedsPermission
                else HelperState.Ready
            isInstalled(ctx) -> HelperState.NotRunning
            else -> HelperState.NotInstalled
        }
    }.getOrDefault(HelperState.NotInstalled)

    fun isReady(ctx: Context) = state(ctx) == HelperState.Ready

    fun requestPermission() = runCatching {
        if (!Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(REQUEST_CODE)
    }

    private fun isInstalled(ctx: Context) =
        runCatching { ctx.packageManager.getPackageInfo(HELPER_PACKAGE, 0); true }.getOrDefault(false)

    /** Connects to the helper, runs [block], then disconnects. Returns null if it could not connect in time. */
    fun <T> withService(ctx: Context, block: (IShellFiles) -> T): T? {
        if (!isReady(ctx)) return null
        val latch = CountDownLatch(1)
        var service: IShellFiles? = null
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) service = IShellFiles.Stub.asInterface(binder)
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, ShellFileService::class.java.name))
            .daemon(false).processNameSuffix("files").version(1)
        return try {
            Shizuku.bindUserService(args, conn)
            if (!latch.await(6, TimeUnit.SECONDS)) null else service?.let(block)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { Shizuku.unbindUserService(args, conn, true) }
        }
    }
}
