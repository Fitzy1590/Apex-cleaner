package com.fitzy.apexshield.core

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process

/** Access the app asks for, each explained in plain words. */
enum class NeededPermission(val title: String, val why: String) {
    Notifications("Notifications", "Lets Apex Cleaner show 1-tap fix cards in your notification shade."),
    AllFiles("All files access", "Lets deep clean find leftover installers, thumbnails, logs and empty folders. Photos, documents and logins are never touched."),
    UsageAccess("Usage access", "Lets the Whitelist App button know which app you were just using so it can protect it."),
}

object Permissions {
    fun missing(ctx: Context): List<NeededPermission> = NeededPermission.values().filter { !isGranted(ctx, it) }

    fun isGranted(ctx: Context, p: NeededPermission): Boolean = when (p) {
        NeededPermission.Notifications ->
            Build.VERSION.SDK_INT < 33 ||
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        NeededPermission.AllFiles ->
            Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        NeededPermission.UsageAccess -> {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
                AppOpsManager.MODE_ALLOWED
        }
    }
}
