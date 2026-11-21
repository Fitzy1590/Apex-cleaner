package com.fitzy.apexcleaner.net

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.fitzy.apexcleaner.core.ProtectedApps
import com.fitzy.apexcleaner.core.WhitelistStore

/**
 * Apps that stream video/music, or are games, count as "priority" apps. They are never slowed,
 * and they get first call on the connection while on screen. Matching is by well-known names so it
 * works even when Android has not labelled the app's category.
 */
object StreamingApps {
    private val KEYWORDS = listOf(
        "youtube", "stremio", "nuvio", "kayo", "nexus", "netflix", "amazon.avod", "amazon.amazonvideo", "primevideo",
        "disney", "hulu", "hbo", "wbd.stream", "paramount", "peacock", "plexapp", "jellyfin", "emby", "kodi", "xbmc",
        "videolan", "twitch", "spotify", "crunchyroll", "tubi", "pluto", "fubo", "hotstar", "mxtech", "iplayer",
        "9now", "7plus", "10play", "foxtel", "binge", "streamotion", "au.com.stan", "dazn", "espn", "sling", "philo",
        "apple.atve", "sbs.ondemand", "abc.iview", "optus", "vlc", "mediaplayer", "exoplayer", "moviebox", "cinema",
    )

    /** Pure name check on a package name or app label. */
    fun looksLikeStreaming(packageOrLabel: String): Boolean {
        val s = packageOrLabel.lowercase()
        return KEYWORDS.any { it in s }
    }

    /** Every installed app that should never be slowed (priority apps, protected apps, your own whitelist). */
    fun priorityApps(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val out = mutableSetOf<String>()
        pm.getInstalledApplications(0).forEach { ai ->
            val cat = ai.category
            val byCategory = cat == ApplicationInfo.CATEGORY_VIDEO || cat == ApplicationInfo.CATEGORY_AUDIO || cat == ApplicationInfo.CATEGORY_GAME
            if (byCategory || looksLikeStreaming(ai.packageName) || looksLikeStreaming(ai.loadLabel(pm).toString())) out += ai.packageName
        }
        val store = WhitelistStore(ctx)
        out += ProtectedApps(ctx, store).automatic()
        out += store.all()
        // Browsers ship widgets and media services, which would mark them "protected" and keep web ads unfiltered.
        // Only a browser you whitelisted yourself stays outside the shield.
        out -= (BROWSERS - store.all())
        return out
    }

    /**
     * Web browsers. Many phones ship Chrome or Samsung Internet as a built-in app, which would otherwise be skipped,
     * so ads on web pages were never filtered. They join the shield like any ordinary app.
     */
    val BROWSERS = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
        "com.microsoft.emmx", "com.brave.browser", "com.opera.browser", "com.opera.mini.native",
        "com.vivaldi.browser", "com.duckduckgo.mobile.android", "com.kiwibrowser.browser",
        "com.google.android.apps.chrome", "org.chromium.chrome", "com.mi.globalbrowser", "com.UCMobile.intl",
    )

    /** Apps that go through the speed limiter: ordinary installed apps, browsers and the Play Store, minus priority apps. */
    fun limitedApps(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val priority = priorityApps(ctx) + SafeLists.bankingBypassPackages + ProtectedApps.ALWAYS_PROTECTED + ctx.packageName
        val installed = pm.getInstalledApplications(0)
        val out = installed
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName in BROWSERS }
            .map { it.packageName }.toMutableSet()
        if (runCatching { pm.getPackageInfo("com.android.vending", 0) }.isSuccess) out += "com.android.vending"
        return out - priority
    }
}
