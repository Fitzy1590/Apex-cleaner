package com.fitzy.apexcleaner.net

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
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
        "bannerad", "nativead", "adplaceholder", "adchoices",
    )
    private val CLASS_HINTS = listOf("adview", "admanageradview", "nativeadview", "bannerview.ad", "unifiednativeadview", "mrecview")
    private val DESCRIPTIONS = setOf("advertisement", "adchoices", "ad choices")

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

    /** A thin, wide web view docked at the very bottom of the screen is a banner ad frame (e.g. Sofascore). Real web pages are far taller. */
    fun bottomBanner(className: String?, left: Int, top: Int, right: Int, bottom: Int, screenW: Int, screenH: Int): Boolean {
        if (className?.contains("WebView") != true) return false
        val w = right - left; val h = bottom - top
        return w >= screenW * 0.6 && h >= 40 && h <= screenH * 0.18 && bottom >= screenH * 0.9 && top >= screenH * 0.7
    }

    /** Sofascore only: its in-feed promo banner is a wide, short web view anywhere on screen, with page background on both sides. */
    fun sofascoreBanner(pkg: String, className: String?, w: Int, h: Int, screenW: Int, screenH: Int): Boolean =
        pkg.contains("sofascore") && className?.contains("WebView") == true && w >= screenW * 0.5 && h >= 40 && h <= screenH * 0.18

    fun isEnabled(ctx: Context): Boolean {
        val me = android.content.ComponentName(ctx, AdBoxHider::class.java)
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return list.split(':').any { it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true) }
    }
}

/** Spots "turn off your ad blocker" pop-ups in web browsers. Pure, so it is unit-tested. Strict phrases and short text only, so an article that merely talks about ad blockers is left alone. */
object AdNoticeRules {
    private val PHRASES = listOf(
        "ads aren't being displayed", "ads are not being displayed", "we noticed you're using an ad blocker", "we noticed you are using an ad blocker",
        "please disable your ad blocker", "please turn off your ad blocker", "disable your ad blocker", "turn off your ad blocker",
        "allow ads on this site", "ad blocker detected", "adblock detected", "ad blocker is on", "using an ad blocker", "whitelist our site",
    )
    private val BROWSERS = listOf("chrome", "browser", "firefox", "opera", "brave", "msedge", "edge", "duckduckgo", "vivaldi", "sbrowser")
    private val CLOSE_LABELS = setOf("close", "dismiss", "x", "\u00d7", "\u2715", "\u2716", "close dialog", "close popup", "close pop-up", "close modal", "close message")

    fun looksLikeNotice(text: String?): Boolean {
        val t = text?.replace('\u2019', '\'')?.trim()?.lowercase() ?: return false
        if (t.length < 12 || t.length > 200) return false
        return PHRASES.any { it in t }
    }

    fun isBrowser(pkg: String) = BROWSERS.any { pkg.contains(it) }

    fun fullScreen(w: Int, h: Int, screenW: Int, screenH: Int) = w >= screenW * 0.95 && h >= screenH * 0.85

    /** A close control is labelled "close"/"x", or is a small clickable item in the top part of the pop-up (an icon-only X). */
    fun looksLikeClose(label: String?, clickable: Boolean, w: Int, h: Int, topOffset: Int, boxH: Int, smallPx: Int): Boolean {
        if (label?.trim()?.lowercase() in CLOSE_LABELS) return true
        return clickable && w in 1..smallPx && h in 1..smallPx && topOffset <= boxH * 0.25
    }
}

/**
 * Optional helper: covers empty ad boxes left on screen by blocked ads with a plain panel. It only reads item labels (ids,
 * class names, descriptions) and sizes, never text, and stores nothing. Banking, protected, whitelisted and system screens are skipped.
 */
class AdBoxHider : AccessibilityService() {
    private val covers = mutableListOf<View>()
    private val noticeCovers = mutableListOf<View>()
    private var lastClick = 0L
    private var noticeTries = 0
    private var noticeSince = 0L
    private var lastRun = 0L
    private var lastKey = ""
    private var lastPkg = ""
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var settlePkg = ""
    private var settling = false
    private val settle = Runnable { settling = false; lastRun = System.currentTimeMillis(); runCatching { refresh(settlePkg) }.onFailure { clear() } }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        val pkg = e.packageName?.toString() ?: return
        if ((!AppSettings.hideAdBoxes(this) && !AppSettings.hideAdNotices(this)) || skip(pkg)) { clear(); return }
        if (pkg != lastPkg) { clear(); lastPkg = pkg }
        // While the page is moving a fixed cover would slide off its box and sit on real content, so take it down
        // at once and put it back only after scrolling has stopped.
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            clear()
            handler.removeCallbacks(settle)
            settlePkg = pkg; settling = true
            handler.postDelayed(settle, 700)
            return
        }
        if (settling) return
        val now = System.currentTimeMillis()
        if (now - lastRun < 400) return
        lastRun = now
        runCatching { refresh(pkg) }.onFailure { clear() }
    }

    private fun skip(pkg: String): Boolean =
        pkg == packageName || pkg == "com.android.systemui" || pkg in SafeLists.bankingBypassPackages ||
            pkg in com.fitzy.apexcleaner.core.ProtectedApps.ALWAYS_PROTECTED || pkg in WhitelistStore(this).all() ||
            SHOPPING_HINTS.any { pkg.contains(it) } || pkg.contains("launcher") || pkg.contains("inputmethod") || pkg.contains("keyboard")

    private fun refresh(pkg: String) {
        val root = rootInActiveWindow ?: return clear()
        if (root.packageName?.toString() != pkg) return
        val dm = resources.displayMetrics
        if (AppSettings.hideAdNotices(this) && AdNoticeRules.isBrowser(pkg)) handleNotice(root, dm) else clearNotice()
        if (!AppSettings.hideAdBoxes(this)) return
        val found = mutableListOf<Rect>()
        val outside = mutableSetOf<Rect>()
        var visited = 0
        fun walk(n: AccessibilityNodeInfo, parent: AccessibilityNodeInfo?) {
            if (visited++ > 600 || found.size >= 4) return
            if (n.isVisibleToUser && !n.isEditable) {
                val br = Rect(); n.getBoundsInScreen(br)
                if (AdBoxRules.bottomBanner(n.className?.toString(), br.left, br.top, br.right, br.bottom, dm.widthPixels, dm.heightPixels) && br.left >= 0 && br.bottom <= dm.heightPixels) { found += br; return }
            }
            if (n.isVisibleToUser && !n.isEditable) {
                val br = Rect(); n.getBoundsInScreen(br)
                if (AdBoxRules.sofascoreBanner(pkg, n.className?.toString(), br.width(), br.height(), dm.widthPixels, dm.heightPixels) &&
                    br.left >= 10 && br.top >= 0 && br.right <= dm.widthPixels - 10 && br.bottom <= dm.heightPixels) { found += br; outside += br; return }
            }
            if (n.isVisibleToUser && !n.isEditable && AdBoxRules.looksLikeAd(n.viewIdResourceName, n.className?.toString(), n.contentDescription?.toString())) {
                val r = Rect()
                // An "AdChoices" icon sits inside the ad box, so cover its parent box instead of the tiny icon.
                (if (n.contentDescription?.toString()?.lowercase()?.contains("adchoices") == true && parent != null) parent else n).getBoundsInScreen(r)
                // Only boxes fully on screen: a half-scrolled-in box would put a cover over neighbouring content.
                val whole = r.top >= 0 && r.left >= 0 && r.bottom <= dm.heightPixels && r.right <= dm.widthPixels
                if (whole && AdBoxRules.sensibleSize(r.width(), r.height(), dm.widthPixels, dm.heightPixels)) { found += r; return }
            }
            for (i in 0 until n.childCount) { val c = n.getChild(i) ?: continue; walk(c, n) }
        }
        walk(root, null)
        val key = found.joinToString("|") { it.flattenToString() }
        if (key == lastKey) return
        lastKey = key
        clear(keepKey = true, notices = false)
        if (found.isEmpty()) return
        if (android.os.Build.VERSION.SDK_INT < 30) return  // no way to match the app background, so never cover
        shoot(found, outside)
    }

    /** Match the app's own background: look at one pixel inside each box corner (kept in memory only, never stored). */
    @androidx.annotation.RequiresApi(30)
    private fun shoot(found: List<Rect>, outside: Set<Rect> = emptySet()) {
        handler.postDelayed({
            runCatching {
                takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val shot = runCatching {
                            val hb = result.hardwareBuffer
                            Bitmap.wrapHardwareBuffer(hb, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false).also { hb.close() }
                        }.getOrNull()
                        found.forEach { r ->
                            val b = shot ?: return@forEach
                            if (r in outside) {
                                // Picture banner: copy the page colour from just outside both sides; cover only if they match.
                                val y = ((r.top + r.bottom) / 2).coerceIn(0, b.height - 1)
                                val l = runCatching { b.getPixel((r.left - 8).coerceIn(0, b.width - 1), y) }.getOrNull()
                                val rt = runCatching { b.getPixel((r.right + 8).coerceIn(0, b.width - 1), y) }.getOrNull()
                                if (l != null && l == rt) show(r, l)
                                return@forEach
                            }
                            // Cover only if all four corners are the same flat colour; a photo or product tile is left alone.
                            val pts = listOf(r.left + 12 to r.bottom - 12, r.right - 12 to r.bottom - 12, r.right - 12 to r.top + 12, (r.left + r.right) / 2 to r.bottom - 12)
                            val cols = pts.mapNotNull { (x, y) -> runCatching { b.getPixel(x.coerceIn(0, b.width - 1), y.coerceIn(0, b.height - 1)) }.getOrNull() }
                            if (cols.size == 4 && cols.all { it == cols[0] }) show(r, cols[0])
                        }
                        shot?.recycle()
                    }
                    override fun onFailure(errorCode: Int) { }
                })
            }
        }, 80)
    }

    /** Looks for an "ads aren't being displayed" pop-up. Only phrase matches are checked; no text is kept. */
    private fun handleNotice(root: AccessibilityNodeInfo, dm: android.util.DisplayMetrics) {
        var hit: AccessibilityNodeInfo? = null
        var visited = 0
        fun walk(n: AccessibilityNodeInfo) {
            if (hit != null || visited++ > 2500) return
            if (n.isVisibleToUser && !n.isEditable && (AdNoticeRules.looksLikeNotice(n.text?.toString()) || AdNoticeRules.looksLikeNotice(n.contentDescription?.toString()))) { hit = n; return }
            for (i in 0 until n.childCount) { val c = n.getChild(i) ?: continue; walk(c) }
        }
        walk(root)
        val node = hit ?: run { noticeTries = 0; noticeSince = 0L; return clearNotice() }
        // Climb to the pop-up box, stopping before the full-screen dimmed layer or the page itself.
        var box = node
        var up = 0
        while (up++ < 10) {
            val p = box.parent ?: break
            val pr = Rect(); p.getBoundsInScreen(pr)
            if (AdNoticeRules.fullScreen(pr.width(), pr.height(), dm.widthPixels, dm.heightPixels)) break
            box = p
        }
        val br = Rect(); box.getBoundsInScreen(br)
        if (br.width() <= 0 || br.height() <= 0) return
        val now = System.currentTimeMillis()
        if (noticeSince == 0L) noticeSince = now
        // Try the close button first, retrying about once a second; the pop-up may still be sliding in.
        if (noticeTries < 4 && now - lastClick > 900) {
            val close = findClose(box, br, (64 * dm.density).toInt())
            lastClick = now
            noticeTries++
            if (close != null && close.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { clearNotice(); return }
        }
        // Black panel only as a last resort: every close attempt failed and the pop-up has stayed for over 3 seconds.
        if (noticeTries < 4 || now - noticeSince < 3000) return
        if (br.height() > dm.heightPixels * 0.8 || noticeCovers.isNotEmpty()) return
        val cover = Rect(br.left.coerceAtLeast(0), br.top.coerceAtLeast(0), br.right.coerceAtMost(dm.widthPixels), br.bottom.coerceAtMost(dm.heightPixels))
        show(cover, 0xFF000000.toInt(), noticeCovers)
    }

    private fun findClose(box: AccessibilityNodeInfo, br: Rect, smallPx: Int): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        var visited = 0
        fun walk(n: AccessibilityNodeInfo) {
            if (found != null || visited++ > 300) return
            if (n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)
                val label = n.text?.toString() ?: n.contentDescription?.toString()
                if (AdNoticeRules.looksLikeClose(label, n.isClickable, r.width(), r.height(), r.top - br.top, br.height(), smallPx)) { found = n; return }
            }
            for (i in 0 until n.childCount) { val c = n.getChild(i) ?: continue; walk(c) }
        }
        walk(box)
        return found
    }

    private fun clearNotice() {
        if (noticeCovers.isEmpty()) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        noticeCovers.forEach { runCatching { wm.removeView(it) } }
        noticeCovers.clear()
    }

    private fun show(r: Rect, sampled: Int?, into: MutableList<View> = covers) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val v = View(this).apply { setBackgroundColor(sampled?.let { it or 0xFF000000.toInt() } ?: if (night) 0xFF101114.toInt() else 0xFFFFFFFF.toInt()) }
        val lp = WindowManager.LayoutParams(r.width(), r.height(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE).apply { gravity = Gravity.TOP or Gravity.START; x = r.left; y = r.top }
        runCatching { wm.addView(v, lp); into += v }
    }

    private fun clear(keepKey: Boolean = false, notices: Boolean = true) {
        if (!keepKey) lastKey = ""
        if (notices) clearNotice()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        covers.forEach { runCatching { wm.removeView(it) } }
        covers.clear()
    }

    override fun onInterrupt() { clear() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); clear(); super.onDestroy() }

    companion object {
        private val SHOPPING_HINTS = listOf("amazon", "shop", "ebay", "walmart", "target", "aliexpress", "etsy", "temu", "argos", "currys", "tesco", "asda", "sainsbury", "bestbuy", "costco")
        fun isEnabled(ctx: Context) = AdBoxRules.isEnabled(ctx) }
}
