package com.fitzy.apexcleaner.core

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val tag: String, val code: Int, val apkUrl: String)

/** Looks for a newer test build on the project's GitHub releases. One tiny request, only when the app opens. */
object UpdateChecker {
    private const val URL_LIST = "https://api.github.com/repos/Fitzy1590/Apex-cleaner/releases?per_page=5"

    /** "v1.0.42" -> 42. Anything else -> null. */
    fun codeFromTag(tag: String): Int? = Regex("""^v\d+\.\d+\.(\d+)$""").find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** Picks the newest release that is newer than [currentCode] and has an .apk file. */
    fun pick(json: String, currentCode: Int): UpdateInfo? {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return null
        var best: UpdateInfo? = null
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val tag = r.optString("tag_name")
            val code = codeFromTag(tag) ?: continue
            val assets = r.optJSONArray("assets") ?: continue
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url") ?: continue
            if (code > currentCode && (best == null || code > best.code)) best = UpdateInfo(tag, code, apk)
        }
        return best
    }

    fun check(ctx: Context): UpdateInfo? = runCatching {
        val current = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0)).toInt()
        val c = URL(URL_LIST).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode != 200) return null
        pick(c.inputStream.use { it.readBytes().decodeToString() }, current)
    }.getOrNull()
}
