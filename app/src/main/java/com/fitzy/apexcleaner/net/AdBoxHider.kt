package com.fitzy.apexcleaner.net

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.fitzy.apexcleaner.core.AppSettings
import com.fitzy.apexcleaner.core.WhitelistStore

/** Decides from an item's labels whether it is an ad box. Pure, so it is unit-tested. Deliberately strict: a miss is better than covering real content. */
object AdBoxRules {
    private val ID_TOKENS = setOf(
        "ad", "ads", "adview", "adviews", "admob", "advert", "advertisement", "adcontainer", "adframe", "adslot", "adbanner",
        "bannerad", "nativead", "adplaceholder", "adchoices", "sponsored",
    )
    private val CLASS_HINTS = listOf("adview", "admanageradview", "nativeadview", "bannerview.ad", "unifiednativeadview", "mrecview")
    private val DESCRIPTIONS = setOf("advertisement", "ad", "ads", "sponsored", "adchoices", "ad choices")

    /** "com.x.app:id/native_adContainer" becomes the words native, ad, container, so "ad" counts but "download" and "address" do not. */
    fun idWords(id: String?): List<String> {
        val name = id?.substringAfter('/') ?: return emptyList()
        return name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    }

    fun looksLikeAd(id: String?, className: String?, description: String?): Boolean {
        val words = idWords(id)
        if (words.any { it in ID_TOKENS }) return true
        // Joined pairs such as "ad" + "container" in "ad_container".
        if (words.zipWithNext().any { (a, b) -> (a == "ad" || a == "ads") && b in setOf("container", "frame", "slot", "banner", "view", "holder", "placeholder", "layout") }) return true
        val cls = className?.lowercase().orEmpty()
        if (CLASS_HINTS.any { it in cls }) return true
        return description?.trim()?.lowercase() in DESCRIPTIONS
    }

    /** A box is only covered if it is a sensible size: not tiny, and never most of the screen. */
    fun sensibleSize(w: Int, h: Int, screenW: Int, screenH: Int): Boolean =
        w >= 48 && h >= 32 && h <= screenH * 0.45 && w <= screenW

    fun isEnabled(ctx: Context): Boolean {
        val me = android.content.ComponentName(ctx, AdBoxHider::class.java)
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return list.split(':').any { it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true) }
    }
}

/**
 * Optional helper: covers empty ad boxes left on screen by blocked ads with a plain panel. It only reads item labels (ids,
 * class names, descriptions) and sizes, never text, and stores nothing. Banking, protected, whitelisted and system screens are skipped.
 */
class AdBoxHider : AccessibilityService() {
    private val covers = mutableListOf<View>()
    private var lastRun = 0L
    private var lastKey = ""
    private var lastPkg = ""

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        val pkg = e.packageName?.toString() ?: return
        if (!AppSettings.hideAdBoxes(this) || skip(pkg)) { clear(); return }
        if (pkg != lastPkg) { clear(); lastPkg = pkg }
        val now = System.currentTimeMillis()
        if (now - lastRun < 400) return
        lastRun = now
        runCatching { refresh(pkg) }.onFailure { clear() }
    }

    private fun skip(pkg: String): Boolean =
        pkg == packageName || pkg == "com.android.systemui" || pkg in SafeLists.bankingBypassPackages ||
            pkg in com.fitzy.apexcleaner.core.ProtectedApps.ALWAYS_PROTECTED || pkg in WhitelistStore(this).all() ||
            pkg.contains("launcher") || pkg.contains("inputmethod") || pkg.contains("keyboard")

    private fun refresh(pkg: String) {
        val root = rootInActiveWindow ?: return clear()
        if (root.packageName?.toString() != pkg) return
        val dm = resources.displayMetrics
        val found = mutableListOf<Rect>()
        var visited = 0
        fun walk(n: AccessibilityNodeInfo, parent: AccessibilityNodeInfo?) {
            if (visited++ > 600 || found.size >= 4) return
            if (n.isVisibleToUser && !n.isEditable && AdBoxRules.looksLikeAd(n.viewIdResourceName, n.className?.toString(), n.contentDescription?.toString())) {
                val r = Rect()
                // An "AdChoices" icon sits inside the ad box, so cover its parent box instead of the tiny icon.
                (if (n.contentDescription?.toString()?.lowercase()?.contains("adchoices") == true && parent != null) parent else n).getBoundsInScreen(r)
                if (AdBoxRules.sensibleSize(r.width(), r.height(), dm.widthPixels, dm.heightPixels)) { found += r; return }
            }
            for (i in 0 until n.childCount) { val c = n.getChild(i) ?: continue; walk(c, n) }
        }
        walk(root, null)
        val key = found.joinToString("|") { it.flattenToString() }
        if (key == lastKey) return
        lastKey = key
        clear(keepKey = true)
        found.forEach { show(it) }
    }

    private fun show(r: Rect) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val v = View(this).apply { setBackgroundColor(if (night) 0xFF101114.toInt() else 0xFFFFFFFF.toInt()) }
        val lp = WindowManager.LayoutParams(r.width(), r.height(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE).apply { gravity = Gravity.TOP or Gravity.START; x = r.left; y = r.top }
        runCatching { wm.addView(v, lp); covers += v }
    }

    private fun clear(keepKey: Boolean = false) {
        if (!keepKey) lastKey = ""
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        covers.forEach { runCatching { wm.removeView(it) } }
        covers.clear()
    }

    override fun onInterrupt() { clear() }
    override fun onDestroy() { clear(); super.onDestroy() }

    companion object { fun isEnabled(ctx: Context) = AdBoxRules.isEnabled(ctx) }
}
