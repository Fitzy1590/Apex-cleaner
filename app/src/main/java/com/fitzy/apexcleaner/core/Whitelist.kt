package com.fitzy.apexcleaner.core

import android.app.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** User-chosen protected apps, stored privately on the device. */
class WhitelistStore(context: Context) {
    private val prefs = context.getSharedPreferences("whitelist", Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty()
    fun add(pkg: String) = prefs.edit().putStringSet(KEY, all() + pkg).apply()
    fun remove(pkg: String) = prefs.edit().putStringSet(KEY, all() - pkg).apply()
    fun toggle(pkg: String) = if (pkg in all()) remove(pkg) else add(pkg)

    var lastFlagged: String?
        get() = prefs.getString(LAST_FLAGGED, null)
        set(v) = prefs.edit().putString(LAST_FLAGGED, v).apply()

    private companion object {
        const val KEY = "packages"
        const val LAST_FLAGGED = "last_flagged"
    }
}

/**
 * Works out which apps must never be interrupted: launchers, notification listeners,
 * widget hosts, media apps, alarm apps, plus anything the user whitelisted.
 * This app never force-stops anything; the list also guards any future tuning action.
 */
class ProtectedApps(private val context: Context, private val store: WhitelistStore) {

    fun automatic(): Set<String> {
        val pm = context.packageManager
        val out = mutableSetOf(context.packageName)

        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL,
        ).forEach { out += it.activityInfo.packageName }

        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.split(':')?.mapNotNull { android.content.ComponentName.unflattenFromString(it)?.packageName }
            ?.let { out += it }

        AppWidgetManager.getInstance(context).installedProviders.forEach { out += it.provider.packageName }

        pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0)
            .forEach { out += it.serviceInfo.packageName }

        runCatching {
            pm.getPackagesHoldingPermissions(
                arrayOf(
                    "android.permission.SCHEDULE_EXACT_ALARM",
                    "android.permission.USE_EXACT_ALARM",
                ),
                0,
            ).forEach { out += it.packageName }
        }
        return out
    }

    fun isProtected(pkg: String): Boolean = pkg in store.all() || pkg in automatic()
}
